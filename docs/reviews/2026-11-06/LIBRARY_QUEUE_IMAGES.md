# Library, Queue, Settings and image compatibility

Baseline: `bcaf59f` (main, 0.8.17). Unrelated local iOS and earlier report changes preserved.

## Checklist

- [x] Library: All label, compact Collection / Libraries tabs, independent scope and preserved filters.
- [x] Custom libraries: drag ordering, persistence, accessible move actions.
- [x] Settings: source drag ordering, enable/age confirmation unchanged, accessible alternatives.
- [x] Queue: compact Queue / Offline navigation, visible grouping and queue actions, selection feedback.
- [x] Shared selection: narrow-screen layout, selected state, accessible dismiss/action controls.
- [x] Images: reproduce Manager Kim 202 response where available; distinguish transfer rejection from decoder failure.
- [x] GIF: animated reading, bounded decoding and offline support; region-decoder fallback for other platform image types.
- [x] Import/export/repair consistency for supported image formats.
- [x] Add focused regression coverage and run compilation, lint and emulator checks; final execution results are maintained in PR #14.
- [x] Update README/full unreleased changelog, create focused commits and PR #14, trigger build and API checks.

## Design

Keep Komori's existing Night/Day palettes, ReaderTypography and green selected states. Use two compact navigation rows instead of stacked statistic cards. Keep 48dp touch targets. Reordering uses explicit handles, keeping row taps and source switches independent; TalkBack offers move earlier/later. Queue distinguishes pending transfers from offline chapters while retaining an All view. Selection uses a dismiss icon, announced count and compact labeled actions without overflowing a single toolbar.

## Initial findings

- Library scope cards and custom-library chips stack and intersect, obscuring the difference between collections and custom libraries. Improvement requiring implementation.
- Custom libraries and Settings sources have only move buttons. Improvement requiring implementation.
- Shared selection packs four text actions into one row; long labels compete on narrow screens. Improvement requiring implementation.
- Transfer validation only uses BitmapFactory; continuous reader always requires BitmapRegionDecoder. A valid GIF can pass transfer validation but fail region decoding. Confirmed decoder-path mismatch; exact reported chapter pending source identification.
- GIF animation decoder is not installed. Improvement requiring implementation.

## Manager Kim 202: live HTTP evidence

On 2026-11-06 the public chapter `/chaptered.php?manga=12109&chapter=202` advertised 24 reader images. Bounded header requests for 23 images returned JPEG signatures and dimensions; page 17's range request returned HTTP 416. A subsequent ordinary HTTPS GET for `https://cdn.demoniclibs.com/Manager%20Kim/202/17.jpeg` returned HTTP 200, `Content-Type: image/jpeg`, **Content-Length: 0**, with an ETag ending in `-0`. This is an empty source asset, not an unsupported image codec. Raw captures remain outside Git under `/tmp/komori-manager-*`; no comic artwork is committed.

The transfer now reports an empty image explicitly (including through the source diagnostic wrapper) and discards its temporary file. Demonic's chapter HTML also advertises `cdn.librarydm.com` as the on-error alternate. The app attempts that exact HTTPS host once for invalid/empty or 404/410 content on `cdn.demoniclibs.com`, preserving the path. Other sources/hosts, verification/challenges, rate limiting, TLS errors and cancellation do not enter this fallback. HTML Cloudflare challenges remain verification failures. Both CDN transfers still require image validation. The alternate endpoint failed here with HTTP/2 protocol and HTTP/1.1 empty-response errors; no live repair is claimed. Fixture tests cover successful alternate delivery and blocked fallback contexts. The reader never fabricates a missing page or silently marks it complete.

## Image implementation scope

JPEG/PNG/WebP retain tiled decoding. GIF and animated WebP use Coil's matching 2.7.0 animation module, bounded request sizes, drawable disposal and existing reader navigation gestures. Animations above four megapixels use the first frame. Static BMP and platform-supported HEIF/AVIF use a bounded fallback where BitmapRegionDecoder cannot decode them. HEIF/AVIF availability depends on Android/device codecs; no native codec bundle, SVG scripting support, TLS changes or MIME-only acceptance is introduced. Import/export and integrity scanning use the same format metadata. Empty/HTML responses remain rejected. API 26 fallback caps whole-frame raster allocation at 4 megapixels before cropping; normal long JPEG/WebP pages remain tiled.

## Navigation and selection

The default Library launch mode is Last viewed. Manage libraries → On open can pin All or a named custom library. DataStore persists the selected collection or library; named-library ordering remains in Room and metadata backups. Deleted library IDs fall back to All. Restoring a selected chip scrolls it into view. The Manage libraries button stays fixed beside the scrolling chips so a long library list cannot hide ordering or launch preferences. Collection shortcuts remain simultaneously visible at ordinary phone widths; category scope does not restrict Saved/Liked/History/Downloaded. Status/unread/search filters remain available. The redundant active-library summary is removed because the selected chip already identifies it.

Queue views filter presentation only. Existing WorkManager coordination, committed pages, pause/delete/cancel behavior, queue order and export remain intact. Selection uses the visible scope and clears when changing Queue/Offline view; selected cards/covers get a green outline. The count is a polite accessibility announcement. Dismissal, Select all/Clear and the labeled Actions menu keep independent accessible targets.

## Validation iterations

Early test development caught a custom-accessibility-action test API mismatch (corrected to invoke the advertised action from semantics), an assertion before asynchronous Library restoration, and a duplicate-label selector matching both the active-library summary and its chip. Tests now synchronize on selected semantics. The redundant summary was also removed for compactness. The existing batch test now explicitly opens Collection, because remembering a custom library is intentional new behavior. No timeouts increased and no assertions disabled.

An intermediate lint invocation overlapped edits and reported stale-line SuspiciousIndentation locations. A subsequent stable pass passed lint. The animated gesture view forwards confirmed single taps through performClick, with centered accessibility activation; its GestureDetector forwarding is narrowly annotated for lint's inability to follow delayed callbacks. Inspection of the existing tiled widget showed it already invokes performClick internally, so its established reader tap callback is retained to avoid duplicate activation. No security or release gate is suppressed.

## Review and delivery

Focused code commits: `70549ec` (Library/Queue/Settings) and `00166ca` (images and Demonic recovery). [PR #14](https://github.com/joy-arz/komori-reader/pull/14) contains the final code, documentation and automated check results. The PR description records final CI conclusions; this report distinguishes the test scopes below.

No application/version/signing identity or secrets changed. The local release APK is an **unsigned compilation check**, not a published update. The unrelated local `.gitignore`, iOS tree and October 30 reports remain outside the commits.

Remaining acceptance: actual TalkBack navigation on a phone, physical low-memory/OEM behavior, platform-specific HEIF/AVIF codecs, and the user's Android 14 live Manager Kim page/fallback. Local UI tests include narrow 320dp / 1.6 font-scale RTL selection; this is synthetic layout coverage, not a complete accessibility audit. No physical-phone or complete live-chapter success is claimed.

## Test scope and results location

The final execution summary is maintained in [PR #14](https://github.com/joy-arz/komori-reader/pull/14), alongside the exact-head build/device checks. This avoids treating a pre-push local result as evidence for a subsequently changed revision.

- JVM suite includes animation memory-budget and redacted image-diagnostic regressions; the expanded suite has 227 cases, with 9 optional/live fixture skips in the default environment.
- Instrumentation covers Library scope/pinned/last-view/deletion restoration, real source drag plus accessibility reordering, Queue selection, narrow large-font RTL actions, offline GIF animation, paged taps/swipes/zoom, image strips/formats and constrained CDN fallback/challenge handling. Existing continuous-reader, stream-position, offline, selection and download-repair coverage is also run.
- Local test logs: `/tmp/komori-ux-confirmed-validation.log` and the final scoped rerun recorded in the PR. Generated JVM/lint/instrumentation reports are under the standard app build report directories; cloud reports/screenshots are attached as CI artifacts.
- The first animation fixture inspected its view before Compose created it; the condition now waits for actual creation and a running drawable. An intermediate local binary predated the HTML challenge guard (compiled class 05:17:26 vs source 05:18:00); it was identified through javap and rebuilt. These iterations did not weaken assertions or production verification.

Screenshots captured by the synthetic UI tests are `komori-library-tabs.png`, `komori-source-reorder.png` and `komori-queue-offline.png`. The device-check script collects them into artifacts. They show fixture data, not a claim of live-source reading acceptance.
