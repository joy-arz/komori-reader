# Releases and signing

## Automated flow

1. Merge a passing change to `main`.
2. Create and push a new immutable version tag: `vMAJOR.MINOR.PATCH` or `vMAJOR.MINOR.PATCH-alpha.N`.
3. `Release APK` runs JVM tests and release lint, assembles a shrunk signed APK and verifies its signature. Before publishing, it requires successful `Android checks` and `Android device checks` for the exact tagged commit. The tagged commit must have been pushed to `main` first so those workflows exist. Signing/building can run alongside device checks; failed, cancelled or missing checks block publication. The gate waits up to 20 minutes for pending checks. Published assets include the APK, `SHA256SUMS.txt` and `update.json`.
4. Download and install on a test device. Android signing identity must match previous releases to update in place.

The release version name comes from the tag. Version code is `100 + github.run_number`, monotonically increasing within the release workflow. Keep that workflow identity and never reset version codes when changing build infrastructure.

The workflow intentionally fails if signing secrets are missing; it never substitutes a debug key. For a failed publication after an existing release was created, inspect the existing release and assets before rerunning. Published version tags must not be moved.

If GitHub reports a workflow aggregate as running after its job completed, do not move the release tag or bypass the gate. Dispatch `android.yml` on the exact immutable tag, wait for its successful run, then retry the failed release job after checking existing assets. This resolved the 0.8.0 aggregate-status inconsistency.

Signed-upgrade diagnostics must be captured inside the live emulator action. `scripts/run_signed_upgrade.sh` bounds individual diagnostic commands and preserves the acceptance script's exit code before emulator shutdown.

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

The tag workflow emits `update.json` alongside the signed APK and checksum. It contains the release tag's version name, CI version code, package name, fixed repository APK URL, byte count and SHA-256 digest. Publish all three assets together. Never reuse a version code or change the signing identity. Plain version tags publish full releases and explicitly mark them as Latest; suffixed tags publish prereleases. The updater recognizes both full releases and prereleases for compatibility with earlier builds; it does not rely on GitHub's stable-only `/latest` endpoint.

The repository must remain public for unauthenticated in-app checks. Android installation is user-confirmed and requires the per-app unknown-source installation permission. No root, silent installation, or GitHub credential is used.


## VirusTotal evidence and README updates

The `Release APK` workflow calls `VirusTotal release evidence` after successful publication. It downloads the published APK and `update.json`, verifies package/version/URL/size/SHA-256, and creates version-specific JSON/SVG evidence under `docs/security/virustotal/`. The latest stable version also refreshes the README badge, availability text, APK link and generated scan section. Preview receipts are archived without replacing the stable README. Commits retain the repository owner's identity.

For automatic new-file submissions and results, add `VIRUSTOTAL_API_KEY` in repository **Settings → Secrets and variables → Actions**. Obtain a key from your own VirusTotal account; do not put it in source files, documentation or chat. Only published public release APKs are submitted. Existing reports are reused for the same hash; missing files are submitted and polled at 16-second intervals within a five-minute analysis window. API access/quota/network failures leave new scans pending, without inventing detection counts. Re-run after an unfinished analysis using **Actions → VirusTotal release evidence → Run workflow**, with the release tag.

Without a key, version/hash/report links still refresh, but a new APK is marked scan pending. A supplied screenshot/result is retained only for its exact version and hash, with its observation date. The 0.8.5 screenshot is the unchanged maintainer-supplied evidence; future summaries are generated from API results, not edited screenshots. VirusTotal findings are a point-in-time report and do not guarantee Google Play Protect approval.

Manual refresh from a checked-out main branch:

```sh
python3 scripts/virustotal_release.py --tag v0.8.5
```

The README's `VIRUSTOTAL:START` / `VIRUSTOTAL:END` comments delimit generated content. Keep both markers. Evidence updates use `[skip ci]` because no app code changes, while the evidence workflow runs its own Python regression tests. Failure to update documentation does not alter the already-published APK or its immutable tag.
