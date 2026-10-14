#!/usr/bin/env python3
"""Create (once), validate, and use a JVM keytool keystore for signed APKs."""
import argparse
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def java_tool(name):
    java_home = os.environ.get("JAVA_HOME")
    path = Path(java_home) / "bin" / name if java_home else None
    resolved = str(path) if path and path.is_file() else shutil.which(name)
    if not resolved:
        raise SystemExit(f"{name} is missing. Set JAVA_HOME to a JDK 17 or newer.")
    return resolved


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["init", "check", "build"])
    parser.add_argument("--signing-dir", type=Path, default=Path.home() / ".config/komori/signing")
    parser.add_argument("--version-code", type=int)
    parser.add_argument("--version-name")
    args = parser.parse_args()
    directory = args.signing_dir.expanduser().resolve()
    if directory == ROOT or ROOT in directory.parents:
        parser.error("Keep signing material outside the repository.")
    key = directory / "release.jks"
    credentials = directory / "credentials.json"
    if args.command == "init":
        if key.exists() or credentials.exists():
            parser.error("Signing material already exists; refusing to replace your update identity.")
        directory.mkdir(parents=True, mode=0o700, exist_ok=True)
        password = secrets.token_urlsafe(36)
        config = {"storePassword": password, "keyPassword": password, "alias": "komori"}
        env = dict(os.environ, KOMORI_STORE_PASSWORD=password, KOMORI_KEY_PASSWORD=password)
        subprocess.run([java_tool("keytool"), "-genkeypair", "-noprompt", "-storetype", "JKS",
                        "-keystore", str(key), "-alias", config["alias"], "-keyalg", "RSA",
                        "-keysize", "3072", "-validity", "10000", "-dname", "CN=Komori Android Release",
                        "-storepass:env", "KOMORI_STORE_PASSWORD", "-keypass:env", "KOMORI_KEY_PASSWORD"],
                       env=env, check=True)
        key.chmod(0o600)
        with os.fdopen(os.open(credentials, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "w") as output:
            json.dump(config, output)
        print("Created release identity. Back up the keystore and credentials securely before publishing.")
    if not key.is_file() or not credentials.is_file():
        parser.error("Missing signing material. Restore your existing release key; use init only for a new app.")
    config = json.loads(credentials.read_text())
    env = dict(os.environ, SIGNING_STORE_FILE=str(key),
               SIGNING_STORE_PASSWORD=config["storePassword"], SIGNING_KEY_ALIAS=config["alias"],
               SIGNING_KEY_PASSWORD=config["keyPassword"])
    subprocess.run([java_tool("keytool"), "-list", "-keystore", str(key),
                    "-alias", config["alias"], "-storepass:env", "SIGNING_STORE_PASSWORD"],
                   env=env, check=True, stdout=subprocess.DEVNULL)
    print("JVM keytool validated the existing release identity.")
    if args.command == "build":
        if not args.version_code or args.version_code <= 0:
            parser.error("build requires --version-code greater than the last published version code.")
        env["VERSION_CODE"] = str(args.version_code)
        if args.version_name:
            env["VERSION_NAME"] = args.version_name
        subprocess.run([str(ROOT / "gradlew"), "testDebugUnitTest", "lintRelease", "assembleRelease"],
                       cwd=ROOT, env=env, check=True)
        print(ROOT / "app/build/outputs/apk/release/app-release.apk")


if __name__ == "__main__":
    main()
