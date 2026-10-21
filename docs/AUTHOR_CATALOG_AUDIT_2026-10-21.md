# HentaiZap author catalog completeness

Checked October 21, 2026 at 08:14 UTC. App baseline: GitHub main `b577b5b`, published app `v0.7.4` from `c8c9d5f`. This is a reproduced source/catalog defect, not an implemented fix.

## Finding

HentaiZap exposes inconsistent artist-index and search results. Komori uses the native artist-index route for both typed Author searches and followed-author browsing, so it inherits the incomplete listing.

Fresh HTML-only requests returned HTTP 200 for all three routes:

| Request | Observed result |
|---|---|
| [Native artist listing](https://hentaizap.com/artist/kanamirubon/) | One gallery: `1580076`. No next-page link. |
| [Name search](https://hentaizap.com/search/?key=kanamirubon) | Twelve gallery cards: `1665355`, `1665354`, `1665353`, `1665352`, `1665351`, `1665350`, `1665349`, `1665348`, `1665347`, `1665346`, `1664521`, `1580076`. |
| [User's missing gallery](https://hentaizap.com/gallery/1665352/) | Its gallery metadata includes the exact artist path `/artist/kanamirubon/`. |

Other inspected missing examples (`1665355`, `1665354`, `1664521`) also carried that exact artist link. Search counts can change with uploads and query options; twelve describes this request, not a permanent total. No artwork or reader images were needed for this diagnosis.

## App behavior

In `app/src/main/java/app/komori/data/Sources.kt`, `WebSource.browse` constructs `/artist/<slug>/` for typed Author search and decodes the stored creator path for followed-author browsing. It reads the listing and its pagination links. The Popular choice instead reads `/artist/<slug>/popular/`; that route also returned one work during this audit.

`parseDetails` already reads the gallery's author/artist metadata. The missing example is therefore not evidence that metadata parsing failed. The single-work artist page has no continuation to fetch: merely increasing page size or allowing more scrolling cannot recover these omitted entries. HTTP 200 responses and the matching artist link distinguish this example from the earlier 403/connection-reset reports.

The app needs a completeness fallback. The site's artist index and keyword search disagree; the site's internal indexing cause has not been established. Do not claim an indexing delay, cache fault or deleted uploads without further evidence.

## Required fix and acceptance criteria

- Supplement native author listings with source search candidates in both Discover Author search and followed-author feeds. Preserve native-only works as well as search-only works, and deduplicate by source gallery identity.
- Confirm candidate ownership against the gallery's exact source-scoped artist/author metadata before attributing a work. A name appearing in a title is not proof. Search prefixes alone are insufficient: `title:kanamirubon`, `artist:kanamirubon` and `author:kanamirubon` all returned the same expanded result set in separate probes.
- Bound metadata request concurrency and cache lifetime/size; surface access/verification errors rather than silently discarding candidates or presenting an incomplete result as complete.
- Preserve latest/popularity behavior and page traversal. After ownership filtering, an empty visible page can still have later matches. Current `PaginationPolicy` and `AuthorCatalog` stop when no new visible titles arrive, so test filtered-empty continuation and repeated-page protection explicitly.
- Cover multi-author works, encoded creator names, exact path ownership, false-positive name matches, native/search duplicates, later pages, inaccessible detail pages and both UI entry points with fixtures.
- Live regression: gallery `1665352` appears under Kanamirubon in typed Author search and in the followed-author feed; compare against current site results without claiming permanent exhaustive coverage.

Until that fix ships, ordinary Title/name search for `kanamirubon` exposes additional source search results. Those are keyword results, not an automatically verified complete author catalog. Following again or resetting the source session does not change the incomplete artist listing observed here.

## Local fix validation, October 21

The pending backlog implementation supplements HentaiZap's native artist listing with name-search candidates, then checks each candidate's detail metadata for the exact artist path before accepting it. Raw candidate IDs drive pagination even when a page has no verified matches. Metadata verification uses bounded concurrency and a short-lived in-memory cache.

`KOMORI_LIVE_SOURCE_AUDIT=1 ./gradlew testDebugUnitTest` passed the reported case: 19 verified Kanamirubon works on the current live response, including gallery 1665352. Fixture checks also reject unrelated name matches and retain continuation through filtered-empty pages. The observed count differs from the earlier reproduction; the result is a point-in-time verification, not proof that the site's catalog is universally complete. Release/device integration is still pending in this checkpoint.
