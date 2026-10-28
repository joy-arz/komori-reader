#!/usr/bin/env python3
"""Offline upgrade/navigation acceptance on a disposable Android emulator.

Uses synthetic pages only. --seed-disposable-fixture replaces this app's test
metadata and is refused on a physical device. Run after instrumentation, whose
normal cleanup can uninstall the debug package.
"""
import argparse
import atexit
import struct
import zlib
import json
from pathlib import Path
import re
import sqlite3
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
BOOK = "11111111-1111-4111-8111-111111111111"
CHAPTER = "22222222-2222-4222-8222-222222222222"
def synthetic_png():
    def chunk(kind, payload):
        body = kind + payload
        return struct.pack(">I", len(payload)) + body + struct.pack(">I", zlib.crc32(body))
    width, height = 40, 60
    rows = b"".join(b"\x00" + bytes((32, 96, 160)) * width for _ in range(height))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b""))

PNG = synthetic_png()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--package", choices=["app.komori.reader", "app.komori.reader.debug"], required=True)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--seed-disposable-fixture", action="store_true", required=True)
    args = parser.parse_args()
    adb = [args.adb, "-s", args.serial]

    def run(*command, binary=False):
        result = subprocess.check_output([*adb, *command], text=not binary, timeout=120 if command[0] == "install" else 60)
        return result if binary else result.strip()

    def shell(command):
        return run("shell", command)

    def wait_for(check, message, seconds=30):
        until = time.monotonic() + seconds
        while time.monotonic() < until:
            if check():
                return
            time.sleep(.5)
        raise AssertionError(message)

    if shell("getprop ro.kernel.qemu") != "1":
        raise SystemExit("Refusing to seed a physical device.")
    run("root")
    run("wait-for-device")
    if shell("id -u") != "0":
        raise SystemExit("A rootable disposable emulator is required.")
    def restore_network():
        for service in ("wifi", "data"):
            subprocess.run([*adb, "shell", f"svc {service} enable"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=15)
    atexit.register(restore_network)
    shell("svc power stayon true")
    shell("settings put system screen_off_timeout 2147483647")
    shell("input keyevent KEYCODE_WAKEUP")
    shell("wm dismiss-keyguard || true")
    shell("input keyevent 82")
    baseline = args.baseline or args.candidate
    run("install", "-r", str(baseline.resolve()))
    package = args.package
    shell(f"am start -W -n {package}/app.komori.MainActivity")
    time.sleep(2)
    shell(f"am force-stop {package}")
    data = f"/data/data/{package}"
    owner = shell(f"stat -c %u {data}")
    if not owner.isdigit():
        raise AssertionError("Invalid app owner")
    schema = json.loads((ROOT / "app/schemas/app.komori.data.LibraryDatabase/5.json").read_text())["database"]
    with tempfile.TemporaryDirectory(prefix="komori-acceptance-") as folder:
        path = Path(folder) / "fixture.db"
        db = sqlite3.connect(path)
        for entity in schema["entities"]:
            db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
            for index in entity.get("indices", []):
                db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for query in schema["setupQueries"]:
            db.execute(query)
        db.execute("PRAGMA user_version = 5")
        db.execute("INSERT INTO manga VALUES (?, ?, '', '', '[]', '', '[]')", (BOOK, "Recovery fixture"))
        db.execute("INSERT INTO shelf VALUES (?, 1, 1, 'Reading', ?, 1)", (BOOK, CHAPTER))
        chapter = {"id": CHAPTER, "attributes": {"chapter": "1", "translatedLanguage": "en", "pages": 3},
                   "relationships": [{"id": BOOK, "type": "manga"}]}
        db.execute("INSERT INTO chapters VALUES (?, ?, ?)", (CHAPTER, BOOK, json.dumps(chapter)))
        db.execute("INSERT INTO progress VALUES (?, ?, ?, 1, 0, 0.0)", (CHAPTER, BOOK, f"{BOOK}|en|?|1"))
        db.execute("INSERT INTO downloads VALUES (?, ?, 'Ch. 1', 3, 3, 'Complete', '', 1)", (CHAPTER, BOOK))
        db.commit()
        db.close()
        # Only this explicitly disposable app fixture is replaced.
        shell(f"rm -f {data}/databases/komori.db {data}/databases/komori.db-wal {data}/databases/komori.db-shm")
        run("push", str(path), "/data/local/tmp/komori-acceptance.db")
        shell(f"mkdir -p {data}/databases {data}/files/chapters/{CHAPTER}")
        shell(f"cp /data/local/tmp/komori-acceptance.db {data}/databases/komori.db")
        page = Path(folder) / "page.png"
        page.write_bytes(PNG)
        run("push", str(page), "/data/local/tmp/komori-acceptance.png")
        for index in range(3):
            shell(f"cp /data/local/tmp/komori-acceptance.png {data}/files/chapters/{CHAPTER}/{index}.img")
        shell(f"chown -R {owner}:{owner} {data}/databases {data}/files/chapters")
        shell(f"restorecon -R {data}/databases {data}/files")
        run("install", "-r", str(args.candidate.resolve()))
        shell("svc wifi disable"); shell("svc data disable")
        shell(f"am start -W -n {package}/app.komori.MainActivity")

        def tree():
            shell("rm -f /data/local/tmp/komori-acceptance.xml")
            shell("uiautomator dump /data/local/tmp/komori-acceptance.xml >/dev/null")
            try:
                return ET.fromstring(shell("cat /data/local/tmp/komori-acceptance.xml 2>/dev/null || true"))
            except ET.ParseError:
                # UIAutomator can return a null root during the first activity frame.
                # Return no nodes so the existing bounded wait retries the capture.
                return ET.Element("hierarchy")

        def tap(text):
            for node in tree().iter("node"):
                if node.get("text") == text or node.get("content-desc") == text:
                    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
                    shell(f"input tap {(x1+x2)//2} {(y1+y2)//2}")
                    return True
            return False

        def visible(text):
            return any(node.get("text") == text or node.get("content-desc") == text for node in tree().iter("node"))

        # Dismiss a previously cached update prompt if this is a debug acceptance run.
        if visible("Snooze 24h"):
            tap("Snooze 24h")
        print("Checking upgraded Library fixture", flush=True)
        def library_ready():
            if visible("Snooze 24h"):
                tap("Snooze 24h")
            return visible("Recovery fixture")
        wait_for(library_ready, "Library fixture missing after upgrade")
        wait_for(lambda: tap("Downloads"), "Downloads action missing")
        wait_for(lambda: visible("Read offline"), "Offline reading action missing")
        wait_for(lambda: tap("Read offline"), "Offline reading action could not be tapped")
        wait_for(lambda: visible("2 / 3"), "Saved page was not restored")
        print("Saved page restored; killing background process", flush=True)
        shell("am start -W -a android.intent.action.MAIN -c android.intent.category.HOME")
        time.sleep(2)
        original_pids = shell(f"pidof {package} || true").split()
        assert original_pids, "App process disappeared before the process-death exercise"
        shell(f"am kill {package}")
        time.sleep(1)
        # Some API levels retain an important background process despite am kill.
        # Root-only emulator SIGKILL models process eviction without force-stopping
        # the package or removing its saved activity/task state.
        remaining = set(shell(f"pidof {package} || true").split())
        for pid in original_pids:
            if pid in remaining:
                assert pid.isdecimal()
                shell(f"kill -9 {pid}")
        def original_processes_exited():
            running = set(shell(f"pidof {package} || true").split())
            return not running.intersection(original_pids)
        wait_for(original_processes_exited, "Original background app process did not exit")
        # API 26 can display the restored task while am start -W never returns.
        # The actual page assertion below is the readiness check.
        shell(f"am start -n {package}/app.komori.MainActivity")
        wait_for(lambda: visible("2 / 3"), "Reader navigation lost after process death")
        print("Reader restored after process death", flush=True)
        try:
            shell("svc wifi disable"); shell("svc data disable")
            def next_page():
                if tap("Next page"):
                    return True
                # New reader sessions start immersive. Reveal controls through the viewport center.
                tap("Reading area")
                return False
            wait_for(next_page, "Next-page action missing after a reader-center tap")
            wait_for(lambda: visible("3 / 3"), "Cached reading failed without network")
        finally:
            shell("svc wifi enable"); shell("svc data enable")
        shell(f"am force-stop {package}")
        raw = run("exec-out", "cat", f"{data}/databases/komori.db", binary=True)
        path.write_bytes(raw)
        wal = shell(f"test -f {data}/databases/komori.db-wal && echo yes || true")
        if wal == "yes":
            Path(str(path) + "-wal").write_bytes(run("exec-out", "cat", f"{data}/databases/komori.db-wal", binary=True))
        db = sqlite3.connect(path)
        assert db.execute("PRAGMA user_version").fetchone()[0] == 6
        assert db.execute("SELECT saved, liked FROM shelf WHERE mangaId=?", (BOOK,)).fetchone() == (1, 1)
        assert db.execute("SELECT count(*) FROM progress WHERE mangaId=?", (BOOK,)).fetchone()[0] == 1
        db.close()
        print("PASS: schema 5→6, saved/liked/progress retention, process-death reader restoration, offline page navigation")


if __name__ == "__main__":
    main()
