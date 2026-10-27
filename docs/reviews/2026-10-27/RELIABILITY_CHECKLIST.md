# Reliability and UI follow-up

Base/current audited main: `501ea97f3cd060395cf4ab8a495adfc7d5d3774a`. Fetched origin on October 27, 2026; origin/main and the clean working tree matched. No repository or ancestor AGENTS.md was found. No intervening commits exist since the supplied audit. Work branch: `codex/reliability-ui-followup`. No version, package, signing-secret or release/tag changes are authorized for this pass.

The supplied 168 JVM passes / six optional live skips, zero lint errors / 46 warnings, API 26/30 passes and API 36 BatchSelection failure are starting evidence, not rerun results.

| Priority | Item | Initial classification | Evidence / next acceptance | Status |
|---|---|---|---|---|
| 1 | Authors duplicate-looking controls | Confirmed UX defect | Filtering and sorting are separate actions using the same FilterList glyph. Inspect shared toolbar, management, selection and saved state; retain both actions with distinct presentation. | Investigating |
| 2 | Update operation coordination | Confirmed defect by control-flow demonstration | Cached update remains exposed while checking; download lacks the reverse busy guard and check replaces the entire state. Add operation ownership and delayed/out-of-order regressions. | Pending |
| 3 | Migration stale requests | Confirmed defect by control-flow demonstration | edit() does not cancel/invalidate; captured old state can publish after edits; selection rows are shared mutable state. Cover search, selection, close and Apply. | Pending |
| 4 | Filtered-author enrichment isolation | Confirmed defect by code-path demonstration | awaitAll propagates one detail failure to AuthorCatalog's page catch, so its cursor never advances. Preserve failures for targeted retry while advancing successful pages. | Pending |
| 5 | Updater/export orphan files | Confirmed ownership gap | Process death skips finally; updater does not reclaim orphan .part files. CBZ export creates comic-*.cbz in cache with finally-only deletion. Add ownership and scoped cleanup tests. | Pending |
| 6 | API 36 Authors multi-select | Acceptance/investigation; not yet a production defect | Re-run exact BatchSelection sequence repeatedly with screenshot, hierarchy, window/state and log evidence. Do not relax assertions/timeouts. | Pending |
| 7 | Real WorkManager lifecycle | Improvement requiring implementation/testing | Existing direct worker and queue tests do not prove real JobScheduler/process-death behavior. Add deterministic loopback HTTP fixtures and actual WorkManager work. | Pending |
| 8 | Source-specific page identity | Risk requiring demonstration | Current normalization only understands MangaDex /data/ and /data-saver/. Inspect adapters and demonstrate a delivery-only rotation before changing other sources. | Pending |
| 9 | Source diagnostics | Improvement requiring implementation | Stage/source context is lost in generic Connection reset messages. Preserve typed retry/verification behavior and redact optional detail. | Pending |
| 10a | Performance | Measurement/acceptance | Measure cold launch, session construction, 1,000-title / 50,000-progress fixture and bounded reader decode/cache behavior; distinguish emulator data. | Pending |
| 10b | Accessibility | Improvement + acceptance | Automate touched-screen semantics, 150% fonts, narrow/landscape/RTL; full TalkBack/manual phone acceptance stays separate. | Pending |
| Prior repairs | Reader pins, import cleanup, planned continuation, speculative accounting, exact-commit gates | Already implemented; do not reopen without evidence | Retain existing regression coverage. | Preserve |
| External | Live sites, challenges, physical 2 GB/battery restrictions | Real-device/external-service acceptance | No phone connected; only API 30 local system image currently installed. Cloud API 26/30/36 available. | Pending external acceptance |

## Per-defect record

Implementation entries will record trigger, root cause, user impact, intended behavior, focused commit, regression coverage and exact verification results here. Screenshots live alongside this report or in linked CI artifacts. Fixture results do not establish live-site or manufacturer behavior.

## Delivery checklist

- [ ] Reproduce/demonstrate current defects and record API 36 baseline diagnostics.
- [ ] Implement priorities 1–10 with focused commits and preserve existing features/security.
- [ ] JVM, lint, debug/test APK and release compilation; compare warnings with baseline.
- [ ] Targeted API 36 repeats, then relevant API 26/30/36 suite.
- [ ] Document before/after Authors screenshots, measurements, residual risks and external acceptance.
- [ ] Push focused branch, open/attach PR, inspect its build/device failures and fix introduced issues.
- [ ] Leave PR unmerged; do not publish or tag a release.
