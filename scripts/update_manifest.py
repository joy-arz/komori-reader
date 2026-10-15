#!/usr/bin/env python3
"""Emit the updater manifest next to the verified, signed release APK."""
import hashlib
import json
import os
from pathlib import Path

version = os.environ["VERSION_NAME"]
code = int(os.environ["VERSION_CODE"])
apk = Path("dist") / f"komori-{version}.apk"
with apk.open("rb") as stream:
    digest = hashlib.file_digest(stream, "sha256").hexdigest()
manifest = {
    "versionName": version,
    "versionCode": code,
    "packageName": "app.komori.reader",
    "apkUrl": f"https://github.com/joy-arz/komori-reader/releases/download/v{version}/{apk.name}",
    "size": apk.stat().st_size,
    "sha256": digest,
}
Path("dist/update.json").write_text(json.dumps(manifest, indent=2) + "\n")
