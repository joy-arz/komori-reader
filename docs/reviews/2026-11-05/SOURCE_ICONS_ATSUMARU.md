# 0.8.16 — source artwork and Atsumaru catalogs

Baseline: `2ac3337` (main, following the signed v0.8.15 release). Existing iOS work and October 30 review notes were left untouched.

## Implementation

- Bundle actual source website artwork, with a green circular frame in Discover, Settings, Authors source filters and cover source badges. Original artwork colors remain recognizable. No new dependency or runtime favicon service. Decorative icons retain the parent action's accessible name and selection state. See `docs/images/source-icons/provenance.json` for URLs and hashes.
- Add regular Atsumaru after WeebCentral in the default order. Fresh installations enable regular Atsumaru; existing enablement choices and explicitly arranged full ordering survive upgrades.
- Add a distinct Atsumaru 18+ source, initially disabled, using the existing shared affirmative age confirmation. Normal and adult local IDs remain isolated. Public catalog requests enforce their adult flag and Comic medium; novels are excluded.
- Use native public indexed search and genre/category/status filters with popularity, newest-added, rating and title ranking. Fetch complete chapter releases and page manifests rather than the truncated details preview. Preserve alternate groups and absolute delivery URLs, resolve relative media through the site's published CDN, and use its WebP alternative for Android versions without AVIF decoding.
- Fix confirmed creator integration gap: Asura's existing adapter and title metadata supported authors, but ReaderViewModel's whitelist rejected its catalog. Permit Asura and both Atsumaru catalogs and map source ranking explicitly.
- Keep updater, signing identity, package identity, reader navigation and download coordination unchanged.

## Evidence and local validation

Public site HTML and JavaScript confirmed the native endpoints and CDN. Anonymous public API responses were obtained with ordinary browser headers; no verification or TLS bypass was used. Captures stayed outside the repository.

- Search fixture captured from `/collections/manga/documents/search`: 189 matches for the probe query, 30 records on the first page.
- Public adult catalog response: 6,252 records at observation time, with `isAdult=true`. No adult page images fetched.
- One regular title: truncated details preview versus 152 complete alternate chapter releases; chapter endpoint returned 65 ordered page records.
- Published WebP page URL: HTTP HEAD 200 and `image/webp`; no page image committed.
- JVM: 219 total, 212 passed, seven optional tests skipped, zero failures/errors. Public API capture parser check enabled and passed. Fixtures cover catalog separation, query escaping, paging, filters, alternates, page ordering, ownership validation, URL preservation, defaults, ordering and consent.
- Debug, instrumented-test APK and minified release compilation: passed.
- Android lint: zero errors, 50 warnings (same count as preceding local baseline).
- Python release/scan scripts: ten tests passed.
- Final API 30 affected UI/integration results and remote CI: recorded below after execution.

## Acceptance boundaries

API response inspection, deterministic fixture parsing, local emulator UI checks and release compilation are separate evidence. No physical low-memory phone, OEM background restrictions, complete live reading/download session or logged-in/premium source entitlement was validated in this change. Source availability and catalog counts can change externally. Locked or unavailable content is not bypassed.
