#!/usr/bin/env python3
"""Actual WorkManager + HTTP + SIGKILL/restart check on disposable debug emulators."""
import collections
import http.server
import json
import os
from pathlib import Path
import shlex
import struct
import subprocess
import threading
import time
import uuid
from urllib.parse import urlsplit
import zlib

PACKAGE = "app.komori.reader.debug"
AUTHORITY = f"content://{PACKAGE}.download-fixtures"


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True, timeout=30).strip()


def shell(*args):
    return adb("shell", shlex.join(args))


def wait_for(predicate, label):
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        value = predicate()
        if value:
            return value
        time.sleep(0.2)
    raise AssertionError("Timed out: " + label)


def png():
    def chunk(kind, data):
        return struct.pack("!I", len(data)) + kind + data + struct.pack("!I", zlib.crc32(kind + data))
    header = struct.pack("!2I5B", 32, 64, 8, 2, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress((b"\0" + b"\x30\x90\x60" * 32) * 64)) + chunk(b"IEND", b"")


def main():
    if os.environ.get("KOMORI_DISPOSABLE_EMULATOR") != "1" or not adb("get-serialno").startswith("emulator-"):
        raise RuntimeError("Explicit disposable emulator opt-in is required")
    if shell("getprop", "ro.kernel.qemu") != "1":
        raise RuntimeError("Physical devices are not supported by this SIGKILL probe")
    adb("root"); adb("wait-for-device")
    active = "work-fixture-" + str(uuid.uuid4())
    queued = "work-fixture-" + str(uuid.uuid4())
    paused = "work-fixture-" + str(uuid.uuid4())
    network = "work-fixture-" + str(uuid.uuid4())
    blocked = threading.Event(); release = threading.Event()
    network_blocked = threading.Event(); network_release = threading.Event()
    screen = "work-fixture-" + str(uuid.uuid4())
    screen_blocked = threading.Event(); screen_release = threading.Event()
    wifi_before = shell("settings", "get", "global", "wifi_on") != "0"
    data_before = shell("settings", "get", "global", "mobile_data") != "0"
    counts = collections.Counter(); lock = threading.Lock(); image = png()

    class Handler(http.server.BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def do_GET(self):
            with lock:
                counts[self.path] += 1
            path = urlsplit(self.path).path
            if path.startswith("/at-home/server/"):
                identity = path.rsplit("/", 1)[-1]
                body = json.dumps(dict(baseUrl="https://fixture.invalid", chapter=dict(hash=identity, data=["0.png", "1.png", "2.png"], dataSaver=["0.png", "1.png", "2.png"]))).encode()
            else:
                for identity, arrived, resume in ((active, blocked, release), (network, network_blocked, network_release), (screen, screen_blocked, screen_release)):
                    if path in (f"/data/{identity}/1.png", f"/data-saver/{identity}/1.png"):
                        arrived.set()
                        if not resume.wait(45):
                            self.send_error(504)
                            return
                body = image
            try:
                self.send_response(200)
                self.send_header("Content-Type", "application/json" if self.path.startswith("/at-home/") else "image/png")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers(); self.wfile.write(body)
            except (BrokenPipeError, ConnectionResetError):
                pass

    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    server.daemon_threads = True
    threading.Thread(target=server.serve_forever, daemon=True).start()
    port = server.server_port
    adb("reverse", f"tcp:{port}", f"tcp:{port}")

    def command(method, identity, *extras):
        result = shell("content", "call", "--uri", AUTHORITY, "--method", method, "--arg", identity, *extras)
        if "result=ok" not in result:
            raise AssertionError(result)

    def state(identity):
        return shell("content", "query", "--uri", AUTHORITY + "/" + identity)

    def pid():
        return shell("sh", "-c", "pidof " + PACKAGE + " || true")

    try:
        shell("am", "start", "-W", "-n", PACKAGE + "/app.komori.MainActivity")
        for identity in (active, queued, paused, network, screen):
            command(f"configure:{port}", identity)
        # A paused row must remain paused across restart, without creating any HTTP work.
        command("low:1", paused); command("queue", paused)
        wait_for(lambda: "Waiting for storage" in state(paused), "storage fixture wait")
        command("pause", paused)
        command("queue", network)
        wait_for(network_blocked.is_set, "network fixture second image")
        assert "done=1" in state(network), state(network)
        shell("svc", "data", "disable"); shell("svc", "wifi", "disable")
        wait_for(lambda: "connected=false" in state(network) and "ENQUEUED" in state(network), "OS network constraint stops the worker")
        network_release.set()
        shell("svc", "wifi", "enable" if wifi_before else "disable")
        shell("svc", "data", "enable" if data_before else "disable")
        wait_for(lambda: "state=Complete" in state(network), "OS network recovery resumes the worker")
        assert sum(count for path, count in counts.items() if path.endswith(f"/{network}/0.png")) == 1, dict(counts)
        command("queue", screen)
        wait_for(screen_blocked.is_set, "screen-off fixture second image")
        shell("input", "keyevent", "KEYCODE_SLEEP")
        wait_for(lambda: "mWakefulness=Asleep" in shell("dumpsys", "power"), "emulator display asleep")
        screen_release.set()
        wait_for(lambda: "state=Complete" in state(screen), "ongoing foreground transfer completes with screen off")
        assert "mWakefulness=Asleep" in shell("dumpsys", "power"), "Display woke during screen-off transfer"
        shell("input", "keyevent", "KEYCODE_WAKEUP"); shell("wm", "dismiss-keyguard")
        command("queue", active)
        wait_for(blocked.is_set, "second image transfer")
        assert "done=1" in state(active), state(active)
        command("queue", queued)
        old_pid = pid()
        assert old_pid and " " not in old_pid, old_pid
        shell("input", "keyevent", "KEYCODE_HOME")
        shell("kill", "-9", old_pid)
        wait_for(lambda: pid() != old_pid, "old process death")
        release.set()
        shell("am", "start", "-W", "-n", PACKAGE + "/app.komori.MainActivity")
        wait_for(lambda: "state=Complete" in state(active), "resumed chapter completion")
        wait_for(lambda: "state=Complete" in state(queued), "queue continuation")
        assert "state=Paused" in state(paused), state(paused)
        assert sum(count for path, count in counts.items() if path.endswith(f"/{active}/0.png")) == 1, dict(counts)
        assert not any(paused in path for path in counts), dict(counts)
        new_pid = pid()
        report = dict(status="PASS", old_pid=old_pid, new_pid=new_pid, completed_page_reused=True,
                      queued_chapter_completed=True, paused_intent_preserved=True, os_network_constraint_recovery=True, emulator_screen_off_transfer_completed=True, counts=dict(counts),
                      scope="synthetic HTTP, OS WorkManager foreground worker, emulator SIGKILL/restart; not manufacturer battery acceptance")
        Path("device-diagnostics").mkdir(exist_ok=True)
        Path("device-diagnostics/download-process-recovery.json").write_text(json.dumps(report, indent=2) + "\n")
        print(json.dumps(report))
    except Exception as error:
        Path("device-diagnostics").mkdir(exist_ok=True)
        report = dict(status="FAIL", error=str(error), counts=dict(counts),
                      rows={identity: state(identity) for identity in (active, queued, paused, network, screen)})
        Path("device-diagnostics/download-process-recovery.json").write_text(json.dumps(report, indent=2) + "\n")
        print(json.dumps(report))
        raise
    finally:
        release.set(); network_release.set(); screen_release.set()
        shell("input", "keyevent", "KEYCODE_WAKEUP"); shell("wm", "dismiss-keyguard")
        shell("svc", "wifi", "enable" if wifi_before else "disable")
        shell("svc", "data", "enable" if data_before else "disable")
        for identity in (active, queued, paused, network, screen):
            command("cleanup", identity)
        adb("reverse", "--remove", f"tcp:{port}")
        server.shutdown(); server.server_close()


if __name__ == "__main__":
    main()
