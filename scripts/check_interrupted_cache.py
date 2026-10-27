#!/usr/bin/env python3
"""SIGKILL/restart smoke check, restricted to explicitly disposable debug emulators."""
import json
import os
from pathlib import Path
import shlex
import subprocess
import time

PACKAGE = 'app.komori.reader.debug'


def adb(*args):
    return subprocess.check_output(['adb', *args], text=True, timeout=30).strip()


def shell(*args):
    return adb('shell', shlex.join(args))


def wait_for(predicate, description):
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        value = predicate()
        if value:
            return value
        time.sleep(0.25)
    raise RuntimeError('Timed out: ' + description)


def main():
    if os.environ.get('KOMORI_DISPOSABLE_EMULATOR') != '1':
        raise RuntimeError('Disposable emulator opt-in is required')
    serial = adb('get-serialno')
    if not serial.startswith('emulator-') or shell('getprop', 'ro.kernel.qemu') != '1':
        raise RuntimeError('This probe only supports disposable Android emulators')
    apk = Path('app/build/outputs/apk/debug/app-debug.apk')
    adb('install', '-r', str(apk))
    adb('root')
    adb('wait-for-device')
    def pid():
        return shell('sh', '-c', 'pidof ' + PACKAGE + ' || true')
    def launch():
        try:
            shell('am', 'start', '-W', '-n', PACKAGE + '/app.komori.MainActivity')
        except subprocess.TimeoutExpired:
            process = pid()
            if process:
                shell('kill', '-3', process)
            raise
        return wait_for(lambda: pid(), 'application process')
    old_pid = launch()
    ready = shell("content", "call", "--uri", f"content://{PACKAGE}.download-fixtures",
                  "--method", "ready", "--arg", "work-fixture-cache-recovery")
    if "result=ok" not in ready:
        raise AssertionError("Startup recovery barrier failed: " + ready)
    root = '/data/user/0/' + PACKAGE
    partial = root + '/cache/reader/page-process-death-probe.part'
    image = root + '/cache/reader/process-probe.img'
    survivor = root + '/files/process-probe-survivor'
    update_dir = root + '/cache/updates'
    export_dir = root + '/cache/exports'
    update_partial = update_dir + '/update-999-123456.part'
    ready_apk = update_dir + '/update-999.apk'
    unrelated = update_dir + '/personal.part'
    export_partial = export_dir + '/comic-123456.cbz'
    staging = root + '/files/local-staging/process-probe'
    shell('run-as', PACKAGE, 'mkdir', '-p', root + '/cache/reader', staging, update_dir, export_dir)
    for path in (partial, image, survivor, staging + '/interrupted.part', update_partial, ready_apk, unrelated, export_partial):
        shell('run-as', PACKAGE, 'touch', path)
    shell('input', 'keyevent', 'KEYCODE_HOME')
    wait_for(lambda: any("mCurrentFocus=" in line and "launcher" in line.lower()
                         for line in shell("dumpsys", "window").splitlines()), "Home focused before SIGKILL")
    shell('kill', '-9', old_pid)
    wait_for(lambda: pid() != old_pid, 'old process death')
    wait_for(lambda: f"{old_pid}:{PACKAGE}/" not in shell("dumpsys", "activity", "processes"),
             "ActivityManager acknowledged process death")
    new_pid = launch()
    def missing(path):
        return shell('sh', '-c', 'if test -e ' + shlex.quote(path) + '; then echo present; else echo missing; fi') == 'missing'
    wait_for(lambda: missing(partial) and missing(staging) and missing(update_partial) and missing(export_partial), 'startup recovery cleanup')
    if missing(image) or missing(survivor) or missing(ready_apk) or missing(unrelated):
        raise AssertionError('Recovery deleted retained cached/persistent data')
    if old_pid == new_pid:
        raise AssertionError('Process did not change')
    report = dict(status='PASS', serial=serial, old_pid=old_pid, new_pid=new_pid,
                  partial_removed=True, staging_removed=True, cached_image_preserved=True,
                  persistent_marker_preserved=True, updater_part_removed=True, export_orphan_removed=True,
                  ready_named_apk_preserved=True, unrelated_update_file_preserved=True, scope='debug emulator SIGKILL and startup recovery')
    Path('device-diagnostics').mkdir(exist_ok=True)
    Path('device-diagnostics/process-recovery.json').write_text(json.dumps(report, indent=2) + '\n')
    shell('run-as', PACKAGE, 'rm', '-f', image, survivor, ready_apk, unrelated)
    print(json.dumps(report))


if __name__ == '__main__':
    main()
