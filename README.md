<div align="center">

<img src="docs/images/komori-icon.png" width="128" alt="Komori: a green bat reading an open manga">

# Komori

**A quiet, lightweight Android manga reader.**
Built with Kotlin and Compose. Native online and offline reading.

<br>

[![Release](https://img.shields.io/badge/release-0.7.5-A3E635?style=for-the-badge&labelColor=0A0A0A)](https://github.com/joy-arz/komori-reader/releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0%2B-A3E635?style=for-the-badge&logo=android&logoColor=A3E635&labelColor=0A0A0A)](#install)
[![Kotlin](https://img.shields.io/badge/Kotlin-Compose-A3E635?style=for-the-badge&logo=kotlin&logoColor=A3E635&labelColor=0A0A0A)](#build)
[![No ads](https://img.shields.io/badge/no%20ads-no%20analytics-A3E635?style=for-the-badge&labelColor=0A0A0A)](#features)

<br>

[Download](#install) · [Features](#features) · [Sources](#sources) · [Build](#build) · [Limits](#current-limits) · [Docs](#documentation) · [Support](#support) · [Contributors](#contributors)

<br>

<img src="docs/images/library.png" width="240" alt="Komori black-and-lime library">&nbsp;&nbsp;
<img src="docs/images/discover.png" width="240" alt="Source browsing">&nbsp;&nbsp;
<img src="docs/images/settings.png" width="240" alt="Grouped reading settings">

</div>

<br>

> **0.8.0 release candidate on main.** The features below include the pending backlog pass. The latest published APK is still 0.7.5 until release validation completes. Android 8.0 and newer.

---

## <img src="https://api.iconify.design/lucide/star.svg?color=%23A3E635" width="26" height="26" align="absmiddle"> At a glance

| | |
|---|---|
| <img src="https://api.iconify.design/lucide/layout-dashboard.svg?color=%23A3E635" width="18" height="18" align="absmiddle">&nbsp; **Design** | Black and lime, Outfit typography, floating navigation |
| <img src="https://api.iconify.design/lucide/book-open.svg?color=%23A3E635" width="18" height="18" align="absmiddle">&nbsp; **Reading** | Paged and Webtoon modes, zoom, immersive controls |
| <img src="https://api.iconify.design/lucide/download.svg?color=%23A3E635" width="18" height="18" align="absmiddle">&nbsp; **Offline** | Persistent downloads with integrity checks and repair |
| <img src="https://api.iconify.design/lucide/search.svg?color=%23A3E635" width="18" height="18" align="absmiddle">&nbsp; **Discover** | Six sources, tag, author, parody, language and category browsing |
| <img src="https://api.iconify.design/lucide/refresh-cw.svg?color=%23A3E635" width="18" height="18" align="absmiddle">&nbsp; **Updates** | Verified in-app updates from GitHub releases |
| <img src="https://api.iconify.design/lucide/lock.svg?color=%23A3E635" width="18" height="18" align="absmiddle">&nbsp; **Private** | No account, no ads, no analytics. Everything stays on your device |

---

## <img src="https://api.iconify.design/lucide/layout-dashboard.svg?color=%23A3E635" width="26" height="26" align="absmiddle"> Features

### <img src="https://api.iconify.design/lucide/layout-dashboard.svg?color=%23A3E635" width="22" height="22" align="absmiddle"> Design and interface

- Black-and-lime design with Outfit typography, outlined collection, setting and chapter cards, green gradient actions and floating navigation.
- Choose System, Dark or Light appearance and compact or comfortable cover density. Existing appearance preferences are retained.
- Settings are grouped into **Reading**, **Sources**, **App** and a full-page **Support** tab.
- Background update checks and downloads use a nonblocking banner. Installation dialogs open from **Review/Install**.
- Compact Library and Discover toolbars with icon-triggered search. Library collection tabs keep counts in a shorter row, leaving more room for covers.
- Swipe left or right across the catalog to change sources in your Settings order.
- Discover filters live in the toolbar. **Verify source** and **Reset search** sit under Source options.

### Library backup and restore

- **Settings → App → Library backup** exports a versioned JSON file through Android's document picker.
- Backups contain saved/liked titles, history and reading positions, followed authors, chapter metadata and per-title reader/group/language preferences, categories and edition links. Device settings, source sessions, download queues and image files are excluded.
- Restore validates the complete file before showing a preview. New entries merge into the Library; existing values are kept by default. Select **Use backup values for matching entries** to replace them. Unrelated entries remain available.
- [Backup format and merge details](docs/BACKUP.md). Metadata writes commit in one database transaction. Invalid files and conflicting chapter ownership are rejected. The format supports files up to 16 MiB; scheduled backups remain planned. Completed chapters can be exported as CBZ separately.

### <img src="https://api.iconify.design/lucide/book-open.svg?color=%23A3E635" width="22" height="22" align="absmiddle"> Reading

- Direct reading from cover play buttons, page resume, a restart-current-chapter preference and optional rereading when caught up.
- Per-title **Left-to-right**, **Right-to-left** and **Webtoon** modes, with app-default fallback.
- Forward and backward continuous webtoon scrolling across chapters, with saved page and scroll position, bounded neighboring-chapter windows and image strips. Works offline, with a Paged/Webtoon switch.
- Chapter/page jump and seek, numeric chapter-gap warnings, and navigation restoration from saved identifiers.
- Brightness, artwork dimming, orientation lock, optional volume-key navigation, and conservative preload/cache choices.
- Webtoon pinch zoom and opt-in tall-page detection; Smart Chapter explanations, alternate releases and per-title language.
- Immersive reading. A center or image tap hides controls, and a floating page counter brings them back.
- Direction-aware edge taps and swipes in paged mode.
- Tiled pinch and double-tap zoom, plus optional keep-screen-awake.
- Dismissible **Continue reading** banner (restore it in Settings) and direct **Read offline** from Downloads.

### <img src="https://api.iconify.design/lucide/library.svg?color=%23A3E635" width="22" height="22" align="absmiddle"> Library and organization

- Personal library, likes, categories, unread badges, status filters and history stored locally with Room.
- Opt-in daily/12-hour chapter checks, grouped Updates, and optional notifications. Author checks rotate through up to three follows per cycle, checking their latest source page; the first check establishes a baseline.
- Reviewed source/edition transfer with explicit chapter mapping. Number, volume and language must agree for automatic suggestions; originals and progress remain available.
- The **Downloaded** collection includes titles with saved pages and opens their offline chapter list.
- Long-press a title, chapter, download or followed creator to select it. Batch controls appear only during selection; browsing has no idle Select bar.
- Bulk actions: save, like, download, read-state changes, pause, resume, and confirmed deletion or unfollow. Select all applies to loaded or filtered items.
- Possible-duplicate warnings for normalized matching titles already saved from another source. Titles are never merged automatically.
- Dedicated **Authors** tab with compact creator chips, searchable follow management and paginated source catalogs. Source indexes can omit works. HentaiZap artist browsing supplements its index with exact-creator-verified search candidates, including the reported Kanamirubon case. Latest is the default, with Popularity / views, Alphabetical and Author ordering. Latest/popularity blend each creator’s source-ranked results; alphabetical and author ordering apply to loaded works. Local reordering retains loaded pages.
- Saved titles keep their source order and receive no Library highlight.
- Compact language badges overlay thumbnail corners when explicit language metadata is available: `en`, `jp`, `sp`, `fr`, `kr`, `ch` and other recognized languages. Browse flags are retained without fetching every gallery; unknown languages remain unlabelled.

### <img src="https://api.iconify.design/lucide/list-filter.svg?color=%23A3E635" width="22" height="22" align="absmiddle"> Smart Chapters

- Language filtering and conservative duplicate grouping.
- Minimal centered chapter markers separate chapters during continuous scrolling, with subtle divider rules and accessible chapter headings.
- Preferred scanlation with strict or fallback selection.
- Chapter lists show source-returned counts, unavailable entries and active filters.
- Choose **Language** on a title to check another translation or all languages.
- Start reading uses ascending chapter numbers, independent of the list's newest-first display option.
- Smart Chapters keeps the current scanlation where possible. Unread and downloaded filters only affect the visible list.

### <img src="https://api.iconify.design/lucide/download.svg?color=%23A3E635" width="22" height="22" align="absmiddle"> Downloads

- Persistent ordered chapter downloads with Download now, reordering, cancel without file deletion, explicit network/storage waits, title grouping, Wi-Fi constraint, retries, pause and resume, original or data-saving images, and offline access.
- Local CBZ/folder import and completed-chapter CBZ export through Android document providers, including a preferred export folder. Metadata backups exclude local pages.
- Bulk options: custom reading-order ranges, all readable, all unread and next 1, 5, 10 or 25 unread. Active and completed downloads are skipped.
- A compact Downloads header shows queued and offline counts. Queue options contain pause and resume/retry commands when available. Completed chapters omit redundant progress bars; active transfers retain detailed progress.
- Configurable visible-image retries (off, 3, 5, 10 or 30 seconds, up to three attempts, 3 seconds by default).
- Integrity checks when opening saved chapters, a persistent **Needs repair** status and targeted repair. Intact pages stay accessible with **Read saved pages**.

### <img src="https://api.iconify.design/lucide/search.svg?color=%23A3E635" width="22" height="22" align="absmiddle"> Discover and browsing

- Source results load the next page automatically near the end. Failures keep a manual retry, and repeated pages stop automatic loading.
- Exact **Tag**, **Author**, **Parody**, **Language** and **Category** name browsing, alongside title search.
- Language and category are separate browse filters. Nhentai can combine them with tags, parodies and creators.
- HentaiZap and ImHentai offer those filter combinations in title browsing, with explicit limits in metadata browsing.
- Gallery details group tappable tags, parodies, languages and categories, alongside authors and artists.
- Source-native popularity sorting:
  - **HentaiZap:** Popular, Top Rated, Downloaded
  - **Nhentai:** daily, weekly, monthly and all-time popularity
  - These are source rankings, not locally calculated view counts.
- HentaiZap metadata extraction excludes unrelated sidebar and ranking tags and strips entity counts from names. Existing saved tags remain compatible, so no database reset is needed.

### <img src="https://api.iconify.design/lucide/shield-check.svg?color=%23A3E635" width="22" height="22" align="absmiddle"> Source verification and network

- The user-operated **Verify source** screen shares source cookies and user agent with native requests and cover loading.
- Reader retries stop automatically for missing pages, invalid content, storage failures and TLS errors. Transient failures have a page-specific session budget; manual retry resets it.
- HTTP 429 and HTTP 503 honor `Retry-After` (seconds or HTTP date). Rate-limited downloads schedule a durable continuation instead of holding the queue; pause/delete cancels the same work chain.
- HTTP 403 stays distinct from a detected verification challenge. Blocked downloads wait for user action instead of retrying repeatedly.
- Cookies are isolated from unrelated image hosts and excluded from app backup.
- Nhentai catalog and reader-manifest GETs retry reset and EOF failures once through a separate HTTP/1.1 connection pool. The browser session and TLS checks stay in place.
- Persistent transport failures explain the network limitation. Verify source handles browser challenges, not connection resets.

### <img src="https://api.iconify.design/lucide/refresh-cw.svg?color=%23A3E635" width="22" height="22" align="absmiddle"> App updates

- Stable/Preview channels, release notes and 24-hour snooze. Automatic checks persist a six-hour success cooldown and 30-minute failure cooldown; manual checks bypass it. HTTP caching supports conditional revalidation when GitHub supplies validators.
- Automatic APK download on unmetered networks, plus manual checking and downloading.
- Verified same-key upgrades through Android's installer.

---

## <img src="https://api.iconify.design/lucide/library.svg?color=%23A3E635" width="26" height="26" align="absmiddle"> Sources

Reorderable source tabs in this order: DemonicScans, WeebCentral, MangaDex, HentaiZap, ImHentai and Nhentai.

| Source | Status | Highlights |
|---|---|---|
| **DemonicScans** | Native | Reading and downloads. Views, new and title browsing, genre selection. Title search is separate from genre browsing. |
| **WeebCentral** | Experimental | Native paged and webtoon reader. Parser and request routing have fixture coverage. |
| **MangaDex** | Native | Search, popularity, rating, update and title sorting, tag filters, safe and suggestive catalog filtering. |
| **HentaiZap** | Native | Popular, Top Rated and Downloaded sorting. Tag, parody, language and category filters. |
| **ImHentai** | Native | Tag, parody, language and category filter combinations in title browsing. |
| **Nhentai** | Native | Daily, weekly, monthly and all-time popularity. Filters combine with tags, parodies and creators. |

> **Comix is removed.** Existing source preferences are filtered automatically.

MangaDex must be reachable on your network. If a network redirects or blocks it, the app reports a connection error and keeps certificate verification enabled.

MangaDex and scanlation groups are credited in the application. This is an independent client, not an official MangaDex app. Source availability and policy can change.

---

## <img src="https://api.iconify.design/lucide/package.svg?color=%23A3E635" width="26" height="26" align="absmiddle"> Install

<div align="center">

### [Download komori-0.7.5.apk](https://github.com/joy-arz/komori-reader/releases/download/v0.7.5/komori-0.7.5.apk)

</div>

Or open [GitHub Releases](https://github.com/joy-arz/komori-reader/releases) and expand **Assets**. Choose the `.apk` file, not the source-code archives.

Full releases and earlier alpha builds use the same private release key. Install over your existing app to keep your Library, downloads and reading progress.

---

## <img src="https://api.iconify.design/lucide/wrench.svg?color=%23A3E635" width="26" height="26" align="absmiddle"> Build

**Requirements**

- JDK 17 (JDK 21 also works locally)
- Android SDK platform 36 and build-tools 35.0.0
- `ANDROID_HOME` set, or an untracked `local.properties` containing `sdk.dir=/absolute/path/to/sdk`

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Release builds use R8 and resource shrinking. An unsigned local release can be built with `./gradlew assembleRelease`. Signed releases require the environment variables documented in [release setup](docs/RELEASING.md).

Local signing is automated through JVM `keytool` validation and Gradle. See [releasing instructions](docs/RELEASING.md).

---

## <img src="https://api.iconify.design/lucide/bot.svg?color=%23A3E635" width="26" height="26" align="absmiddle"> Automated releases

Every push to `main` and every pull request runs unit tests, lint, a debug APK build, and API 26/API 36 instrumentation. A separate Signed upgrade checks workflow validates published APKs against the 0.7.5 baseline with synthetic data, process death and offline navigation.

A version tag such as `v0.8.0` automatically tests, builds, signs and publishes a release APK plus a SHA-256 checksum and updater metadata (`update.json`).

- Plain version tags publish full releases and mark them as **Latest**.
- Tags containing a suffix remain prereleases.

```sh
git tag v0.8.0
git push origin v0.8.0
```

Signing secrets are already configured in repository Actions secrets. No signing material is committed. See [RELEASING.md](docs/RELEASING.md) for key backup and release details.

---

## <img src="https://api.iconify.design/lucide/refresh-cw.svg?color=%23A3E635" width="26" height="26" align="absmiddle"> How app updates work

Install version 0.5 manually once if you are coming from an older build. After that, automatic checks use public GitHub releases with a persistent cooldown. Stable excludes prereleases; Preview includes them.

- Failed update checks do not block reading.
- Automatic downloads default to unmetered connections. Change this in Settings, or choose a manual download on mobile data.
- Android requires permission to install from Komori and confirmation in its installer. This is not a silent system installation.
- The updater verifies the checksum, package, increasing version code and existing signing certificate before installation.
- No GitHub token is stored in the app.

---

## <img src="https://api.iconify.design/lucide/triangle-alert.svg?color=%23A3E635" width="26" height="26" align="absmiddle"> Current limits

<details>
<summary><b>Reader</b></summary>

<br>

- Webtoon mode saves the vertical position within each source page. Paged mode saves the page, not its zoom and pan viewport.
- Continuous chapters can be disabled in Settings. Webtoon zoom is bounded to 3×; tall-page mode detection is opt-in.

</details>

<details>
<summary><b>Library and sync</b></summary>

<br>

- Library metadata and creator follows remain local. Scheduled source checks use best-effort source catalogs; a latest-page author check can miss uploads between runs.
- AniList/MAL tracking is deferred until core acceptance. Scheduled metadata backups and broader storage-management UI remain planned.

</details>

<details>
<summary><b>Chapters and downloads</b></summary>

<br>

- Smart Chapters uses deterministic selection, not translation-quality scoring. Unnumbered releases and different volumes stay separate. Volume metadata inconsistencies may leave duplicates visible.
- Download work is interruptible and Android may delay it. There is no promise of uninterrupted large background transfers. Work checkpoints each page and retries.

</details>

<details>
<summary><b>Performance</b></summary>

<br>

- Low-end performance budgets are goals. They are not yet verified on a physical 2 GB device.

</details>

<details>
<summary><b>Source limitations</b></summary>

<br>

- MangaDex external-only chapters cannot supply pages to the native reader. Start reading skips those entries and chooses the first readable chapter. Their separately labeled external links remain available.
- DemonicScans and WeebCentral pages use the native paged and webtoon reader and the same download storage, not embedded website readers.
- Completed downloads can be opened directly without a source refresh. MangaDex IDs and existing library and download data are retained.
- Source websites may block requests or change their HTML. WeebCentral's parser and request routing have fixture coverage, but live reading has not been verified on the current network.
- For HTTP 403 or a browser-verification prompt, use **Verify source**, complete verification manually, then **Retry native access**.
- Download jobs waiting for verification retain completed pages. Resume them from Downloads after verification.
- **Reset sessions** clears all source browser cookies and storage without changing library data.
- Browser verification cannot guarantee that a source will accept native requests. Unsupported sources are not represented as working readers.

</details>

---

## <img src="https://api.iconify.design/lucide/file-text.svg?color=%23A3E635" width="26" height="26" align="absmiddle"> Documentation

| Document | Description |
|---|---|
| [Source and session review](docs/SOURCE_SESSION_REVIEW_2026-10-15.md) | Follow-up review of sources and sessions |
| [Implementation plan](docs/PLAN.md) | Roadmap and design decisions |
| [Validation notes](docs/VALIDATION.md) | Test and verification results |
| [Komikku reference review](docs/KOMIKKU_REVIEW.md) | Feature comparison, implemented reader improvements and remaining priorities |
| [Releasing](docs/RELEASING.md) | Signing, key backup and release steps |
| [TODO](docs/TODO.md) | Complete prioritized backlog |

---

## <img src="https://api.iconify.design/lucide/users.svg?color=%23A3E635" width="26" height="26" align="absmiddle"> Contributors

<a href="https://github.com/joy-arz/komori-reader/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=joy-arz/komori-reader" alt="Komori contributors">
</a>

Maintained by [@joy-arz](https://github.com/joy-arz). Issues and pull requests are welcome.

---

## <img src="https://api.iconify.design/lucide/heart.svg?color=%23A3E635" width="26" height="26" align="absmiddle"> Credits

- **Typography:** Outfit is bundled under the SIL Open Font License. See `app/src/main/assets/licenses/Outfit-OFL.txt`.
- **Icon:** Supplied bat-and-manga artwork, cropped to remove the outer surround. Android applies the launcher shape through an adaptive icon, and the original illustration is preserved.
- **Icons:** [Lucide](https://lucide.dev) via [Iconify](https://iconify.design), ISC license.
- **Sources:** MangaDex and scanlation groups are credited in the application.

<div align="center">

<br>

*Read quietly.*

</div>

## Support

<div align="center">

<a href="https://ko-fi.com/joyarz">
  <img src="docs/assets/support-on-kofi.jpeg" width="100%" alt="Support Komori, the open-source comics and manga reader, on Ko-fi">
</a>

### Help Komori keep growing

Komori is a free, open-source project. If it makes your reading day better,<br>
a coffee helps support continued development, maintenance, and improvements.

<a href="https://ko-fi.com/joyarz">
  <img src="docs/assets/kofi.svg" width="40" height="30" alt="Ko-fi">
</a>

**[Support joyarz on Ko-fi →](https://ko-fi.com/joyarz)**

Support is always optional. Bug reports, translations, and code contributions help too.

You can also find this link in **Settings → Support**.

</div>
