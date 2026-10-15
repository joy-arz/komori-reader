# Releases and signing

## Automated flow

1. Merge a passing change to `main`.
2. Create and push a new immutable version tag: `vMAJOR.MINOR.PATCH` or `vMAJOR.MINOR.PATCH-alpha.N`.
3. `Release APK` runs JVM tests and release lint, assembles a shrunk signed APK, verifies the APK signature and publishes it with `SHA256SUMS.txt`.
4. Download and install on a test device. Android signing identity must match previous releases to update in place.

The release version name comes from the tag. Version code is `100 + github.run_number`, monotonically increasing within the release workflow. Keep that workflow identity and never reset version codes when changing build infrastructure.

The workflow intentionally fails if signing secrets are missing; it never substitutes a debug key. For a failed publication after an existing release was created, inspect the existing release and assets before rerunning. Published version tags must not be moved.

## Repository Actions secrets

- `ANDROID_KEYSTORE_BASE64`: base64-encoded JKS keystore.
- `ANDROID_STORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The release job decodes the keystore into runner temporary storage and removes it in an always-run cleanup step. The key is never uploaded as an artifact.

## Owner backup

The original release key and its credentials were generated outside the repository under `~/.config/komori/signing/` on the development machine. Keep an encrypted offline backup of both `release.jks` and `credentials.json`. GitHub Actions secrets cannot be downloaded again as plaintext. Losing both the original and its backup prevents signing compatible updates.

## Local signed build

Supply these values from a secure local source without committing them:

- `SIGNING_STORE_FILE`: absolute keystore path.
- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`
- Optional `VERSION_NAME` and `VERSION_CODE`.

Then run `./gradlew testDebugUnitTest lintRelease assembleRelease`.

## Attribution

Commits use the repository owner's configured Git identity. No generated co-author trailers are added. The app credits MangaDex and scanlation groups as required by its source integration.

## JVM keytool automation

`scripts/sign_release.py` uses the JDK's `keytool` to validate the release identity before every local build. Gradle's Android signing configuration signs the APK; `keytool` manages the keystore, rather than signing APK files itself. CI now also validates the keystore and alias with `keytool` before assembling, then verifies the produced APK with `apksigner`.

```sh
export JAVA_HOME=/path/to/jdk-17-or-newer
python3 scripts/sign_release.py check
python3 scripts/sign_release.py build --version-code 105 --version-name 0.4.0-alpha.1
```

Choose a version code greater than the latest published code for subsequent builds. The script defaults to the existing `~/.config/komori/signing/` directory, keeps passwords in environment variables rather than command-line arguments, and never prints credentials.

For a brand-new signing identity only, `python3 scripts/sign_release.py init --signing-dir /secure/path` creates an RSA JKS key and private credentials file. It refuses to overwrite either existing file. **Do not initialize a replacement key for Komori updates**: reuse the existing release identity so installed apps can update without losing data. Back up both files securely.

## In-app update metadata

The tag workflow emits `update.json` alongside the signed APK and checksum. It contains the release tag's version name, CI version code, package name, fixed repository APK URL, byte count and SHA-256 digest. Publish all three assets together. Never reuse a version code or change the signing identity. The updater includes prereleases because this project currently distributes alphas; it does not rely on GitHub's stable-only `/latest` endpoint.

The repository must remain public for unauthenticated in-app checks. Android installation is user-confirmed and requires the per-app unknown-source installation permission. No root, silent installation, or GitHub credential is used.
