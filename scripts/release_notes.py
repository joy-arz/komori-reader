#!/usr/bin/env python3
"""Produce full release notes from the reviewed version entry, never compare links."""
import argparse
from pathlib import Path
import re


def entry(changelog: str, version: str) -> str:
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?", version):
        raise ValueError("Invalid release version")
    match = re.search(r"^## " + re.escape(version) + r"\s*$\n(.*?)(?=^## |\Z)", changelog, re.M | re.S)
    if not match:
        raise ValueError(f"Missing complete changelog entry for {version}")
    body = match.group(1).strip()
    bullets = re.findall(r"^- (.+)$", body, re.M)
    descriptions = [re.sub(r"\[[^\]]*\]\([^)]*\)|https?://\S+", "", bullet).strip() for bullet in bullets]
    if sum(bool(re.search(r"[A-Za-z]{3,}", item)) for item in descriptions) < 2 or "Validation: pending" in body:
        raise ValueError(f"Changelog for {version} is incomplete or not validated")
    return body


def render(changelog: str, version: str) -> str:
    body = entry(changelog, version)
    notes = f"## Install on Android\n\nDownload **komori-{version}.apk** from Assets below. Android 8.0 or newer. Install over your existing app to preserve your Library, progress and downloads.\n\n## Komori {version}\n\n{body}\n"
    if version == "0.8.5":
        for previous in ("0.8.4", "0.8.3"):
            notes += f"\n## Complete changes from {previous}\n\n{entry(changelog, previous)}\n"
    return notes


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", required=True)
    parser.add_argument("--changelog", type=Path, default=Path(__file__).resolve().parent.parent / "CHANGELOG.md")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.write_text(render(args.changelog.read_text(), args.version))


if __name__ == "__main__":
    main()
