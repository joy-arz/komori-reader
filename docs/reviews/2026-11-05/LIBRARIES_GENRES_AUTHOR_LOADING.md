# Title libraries, regular-source genres, Authors responsiveness and HiveToons

Baseline: `4e1815399594120bb579d30031fcd5cf64593778`, following v0.8.16. Unrelated `.gitignore`, October 30 review edits and untracked iOS work are preserved. No package, signing, version, reader-session or download-worker changes.

## Checklist and findings

| Request | Classification / trigger | Implementation |
|---|---|---|
| Libraries beside Save | Missing title-detail action; previously custom assignment required batch selection. | Open the existing transactional library picker directly for the current title. Multiple assignment and library creation remain available. Apply auto-saves; Cancel does not mutate. Preserve the cross-source duplicate warning before opening the picker for an unsaved duplicate. |
| Better regular-source genres | Confirmed: WeebCentral offered only 17 hard-coded tags and discarded native detail genres/category. Its native search page exposes 38 tags. Category/language detail chips also used the generic tag route. | Fetch/cache successful native Weeb/Demonic taxonomies for ten minutes, extract title genres and native Weeb type/status, replace stale genre metadata, route typed chips correctly, and send native Weeb category/status parameters. Add selected genre chips, remove/clear and explicit loading/error/retry controls. Preserve adult-source metadata parsing, facet behavior and the Weeb `adult=False` request. |
| Authors All with 100+ follows | Confirmed from implementation and blocked-request fixture: three creator pages were fetched serially, with nothing published until all three completed. Switching away discarded the loaded catalog. | Keep the existing three-creator round-robin paging, run at most two catalog/enrichment operations concurrently and publish each completed creator page. Keep up to three off-screen catalogs of at most 300 retained titles each. All/individual navigation can restore those results; manual refresh invalidates the cache. Larger catalogs are evicted to bound extra memory. Never request all 100+ creators on first paint. |
| HiveToons cannot connect | Confirmed HTTP trace: `/series` returned `301 Location: http://hivetoons.org/series/`, then redirected back to HTTPS. Android disallows that cleartext hop. | Request Hive's canonical HTTPS trailing-slash document paths directly, retaining query parameters and stable stored source IDs. Use the actual request URL as the HTML base. No cleartext, TLS, DNS, certificate or verification relaxation. |

## Regression coverage and evidence

- JVM: 225 total, 217 passed, eight optional tests skipped, zero failures/errors, with `KOMORI_GENRE_CAPTURE=/tmp` enabled. Captures remain outside the repository.
- Native captured pages: Weeb taxonomy 38 tags; sample details seven genres and Manga category. Hive catalog/genre/details parse; all 28 sample releases and 40 distinct chapter-1 image URLs parse. No image bytes fetched.
- Synthetic 120-creator test: a fast creator publishes while another stays blocked; at most two provider calls active; first batch calls at most three creators; continued fair paging reaches all 120 exactly once. This proves scheduling/first-result behavior, not physical-phone latency or a 120-source live benchmark.
- Debug/test APK and minified release compile passed. Lint: zero errors, 50 warnings, unchanged from baseline.
- API 30 opt-in real Hive test through the app's networking client and Android cleartext policy: catalog 1, genres 3, chapters 28, page manifest 32; zero image bytes. Locked chapters are not bypassed.
- Final affected API 30 run: **11/11 passed**, zero skipped/failures. Covers multi-library save/cancel/unassignment, duplicate confirmation/cancel, 48 dp touch bounds, selected genre remove/clear/retry, 120-creator incremental/cache/refresh behavior, Authors filters/navigation, source facets/drag and the opt-in live Hive request.
- Ten Python release/scan checks passed. [Cloud build](https://github.com/joy-arz/komori-reader/actions/runs/41963830482): 216 JVM passed, nine optional tests skipped, zero failures. [API 26/30/34/36 matrix](https://github.com/joy-arz/komori-reader/actions/runs/41963830487) and [repeated API 36 selection](https://github.com/joy-arz/komori-reader/actions/runs/41963830532) passed on the first attempt.

## UI evidence

These are API 30 fixture screens using the app palette/type system. They contain synthetic titles/empty chapter data and three representative native genres; they are not live-source reading screenshots.

![Title Libraries action beside Save](../../images/source-controls/title-libraries-api30.png)

![Selected non-adult genres](../../images/source-controls/genre-filters-api30.png)

An initial new UI test measured the 40 dp painted Material icon-button bounds instead of its 48 dp expanded touch bounds. The assertion was corrected to check the actual touch width/height; the production control was not shrunk or enlarged to satisfy a drawing-bounds test. The title picker tests retain assignment, cancellation and duplicate safeguards. The Authors filter UI test now verifies typed input and waits for the applied filter state, with screenshot/semantics/state capture on failure; no timeout was increased and no assertion was removed.

## Remaining acceptance

The API 30 network result is emulator acceptance from this network, not the user's Android 14 phone/network or an OEM battery check. Full live reading/download sessions, physical low-memory measurements and all source-specific taxonomy changes remain external acceptance. Missing metadata is not invented, nor are unsupported filters advertised. The user confirmed the phone displays “Connection interrupted/reset,” consistent with the generic transport wrapper for the blocked redirect; phone/network acceptance remains pending. The implementation was delivered in PR #13, merged as `0b5950e9e7d0189aa3f3b091f67c6751a00fa141` following the user’s merge/build request. The next signed version is 0.8.17; publication remains gated on checks for the exact release commit.

## 0.8.17 delivery

- PR #13 merged as `0b5950e9e7d0189aa3f3b091f67c6751a00fa141`. Release tag `v0.8.17` targets preparation commit `0a803e6e2a79b27c7e75ff8a6a9a1775a1451301`; app source and instrumentation are identical to the reviewed PR, with version configuration and documentation updated.
- [Exact-commit Android checks](https://github.com/joy-arz/komori-reader/actions/runs/41966681786): passed. [API 26/30/34/36 device matrix](https://github.com/joy-arz/komori-reader/actions/runs/41966681787): all passed. [Signed release and scan](https://github.com/joy-arz/komori-reader/actions/runs/41966723978): passed on the first attempt.
- [Public v0.8.17](https://github.com/joy-arz/komori-reader/releases/tag/v0.8.17): version code 140, package `app.komori.reader`, 2,830,512 bytes, SHA256 `f730c765a6b74672a1af6a5afc74dcb62d08b978b0db208e6091d15ae5e59ca8`. Published digest/size manifests match; release body matches the complete checked-in release notes.
- Original certificate SHA256 `0f654f439d6059cf8caea9fa1d5403342cc47b1b5a8228bf90e2858ec423d5fd` verified. Public APK is byte-identical to the tested signed candidate.
- API 30 signed 0.8.16→0.8.17 install with the disposable schema-5 fixture: passed schema 5→7 migration, saved/liked/progress retention, process-death reader restoration and offline page navigation. Initial probe could not start because the old Downloads APK had been removed; re-execution used the cached published baseline. This was a local fixture-path precondition, not an application failure.
- VirusTotal: zero malicious, zero suspicious, 68 undetected and seven unsupported engines. README scan evidence and download links updated automatically.
- Android 14 phone/network Hive acceptance and full live reading/download sessions remain pending as stated above. No unrelated local iOS/review changes were committed.
