# Komikku reference review

Reviewed October 14, 2026. This is a feature comparison for Komori, not a claim of feature parity or a fork of Komikku. Implementations in this milestone were written for Komori's existing native reader.

## References inspected

- [Komikku feature list](https://github.com/komikku-app/komikku#features)
- [Reading modes](https://github.com/komikku-app/komikku/blob/master/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReadingMode.kt)
- [Reader preferences](https://github.com/komikku-app/komikku/blob/master/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt)
- [Per-series reader settings](https://github.com/komikku-app/komikku/blob/master/app/src/main/java/eu/kanade/presentation/reader/settings/ReadingModePage.kt)

## Priorities

| Reference capability | Komori decision | Reason |
| --- | --- | --- |
| Multiple viewers and per-series modes | Implement LTR, RTL and Webtoon overrides per title, with app-default fallback | Manga and manhwa need different navigation; a global switch alone is inconvenient |
| Configurable navigation and fullscreen | Implement edge taps, directional swipes and center-tap immersive controls | Reduces permanent reader chrome without hiding the way back to settings |
| Keep screen awake | Add an opt-in reader-only preference | Useful for long pages; disabled by default to respect battery life |
| Automatic webtoon detection | Defer until source metadata is persisted and validated | Covers often have ordinary page proportions, so inferring a title from one image is unreliable |
| Categories, bulk favorites and library sorting | Next library milestone | Useful as libraries grow; user prioritized reader controls for this build |
| Local backup and restore | High-priority durability milestone | Device-local library currently has no user-controlled export |
| Page preload/cache customization | Retain conservative serial loading and bounded cache for now | Low-end-phone requirement favors predictable memory/network use |
| Cover-derived color palettes, many theme options | Retain the jade palette | Consistent contrast and fewer settings suit this app's restrained design |
| Trackers, extensions, recommendations and synchronization | Defer | Adds accounts, dependencies and background work beyond the initial MangaDex client |

## Reader design review

Keep paper #F6F8F5, jade #1F6256 / #D8ECE1 and charcoal #121713 for the shelf; use pale jade #9CD5BD for reader actions. System sans stays readable and avoids a font download. Left-align chapter and scanlation text; put title-specific options in a sheet instead of permanent mode chips.

```
Reader with controls           Immersive reader
[Back | Chapter | 2/36 | Tune] [ full-width page / strips ]
[          Artwork          ] [                           ]
[Mode          Hide controls] [                           ]
[Previous              Next] [              2/36 + Tune ]
```

The toolbar is deliberately compact. A small bottom control remains visible in immersive mode for discoverability and accessibility; Back first reveals controls. Pinch and double-tap zoom stay with the tiled image view. Swipes change pages only when the gesture starts at minimum zoom with one pointer. Center taps toggle chrome; left/right edge taps follow the chosen reading direction. This avoids hijacking a zoomed drag used to inspect a panel.

Per-title mode and scanlation preferences share a row. Updates read the current row under the existing mutation mutex and copy only the changed fields, so choosing a reader mode cannot erase a preferred scanlation, and changing groups cannot reset the reader mode. Room 2 → 3 is additive, with coverage for the original 1 → 3 upgrade path.

## Deliberate remaining limits

Webtoon scrolling is continuous inside a chapter, with an explicit next-chapter action. Webtoon pinch zoom, configurable tap-zone layouts, spread splitting, automatic mode detection, volume-key navigation and title-specific language remain future work. Physical-device memory and battery measurements are still required before claiming low-end performance guarantees.

## Chapter and library follow-up — 0.4.0

Inspected Komikku's [duplicate chapter selection](https://github.com/komikku-app/komikku/blob/master/app/src/main/java/eu/kanade/tachiyomi/util/chapter/ChapterRemoveDuplicates.kt), [chapter sorting](https://github.com/komikku-app/komikku/blob/master/domain/src/main/java/tachiyomi/domain/chapter/service/ChapterSort.kt), and [display filters](https://github.com/komikku-app/komikku/blob/master/app/src/main/java/eu/kanade/domain/chapter/model/ChapterFilter.kt). Komikku groups duplicates by chapter number and prefers the current chapter, then its scanlator. Komori now carries that scanlation continuity forward, while keeping languages, explicit volumes and ambiguous extras separate. A readable local-source release ranks above an unavailable or external duplicate.

Display order and unread/downloaded filters are independent of the ascending reading queue. First reading is numeric chapter-first, so an unassigned-volume chapter 290 no longer sorts before volume 1 chapter 1. Newest-first only reverses the visible list. Library alphabetical sorting is optional; last activity remains the default.

MangaDex's [official API schema](https://api.mangadex.org/docs/static/api.yaml) exposes unavailable records separately. The feed now includes these records with visible labels, accepts all-language discovery, requests up to 500 releases per page, rejects incomplete pagination, and persists successful feed membership atomically. The `includeExternalUrl` tri-state filter is deliberately omitted: `1` selects only external chapters and `0` selects only hosted chapters.

On October 14, 2026, the One Punch-Man manga (`d8a959f7-648e-4c8d-8f23-f1f3f8e129f3`) returned 1,924 unique releases across all languages, 40 Indonesian releases and zero English releases, including when unavailable records were requested. The manga metadata still listed English among translation languages. These are source observations, not evidence of app deduplication hiding English chapters. No client-side algorithm can retrieve pages the source does not provide.

Next useful Komikku-inspired work remains local backup/restore, categories and batch library actions, followed by scheduled library updates with Wi-Fi/battery constraints. Tracking accounts, multiple source extensions and synchronization need a separate design; this build does not claim feature parity.

Additional library preferences were checked against [Komikku LibraryPreferences](https://github.com/komikku-app/komikku/blob/master/domain/src/main/java/tachiyomi/domain/library/service/LibraryPreferences.kt). Komori's completed downloads remain available when a later source feed omits or marks their chapters unavailable. The Downloaded view exposes every locally completed release for the chosen language, even when Smart Chapters or a group preference would otherwise select another release. This does not attempt to fetch removed pages; it uses files already stored on the device.
