Komori 0.8.19 improves filtered browsing, MangaDex metadata and custom-library visibility, with less database work during reading.

- Keep successful filtered Discover results when an individual title's metadata fails. Show a partial-results message and retry only unresolved titles; keep later pages accessible, preserve source order after retries, and cancel obsolete retries when a search changes.
- Retain MangaDex genre tags and origin metadata for title details and Authors filters. Refresh older title records instead of keeping permanently empty metadata.
- Bound MangaDex API responses to 8 MiB after decompression before JSON parsing, including chunked responses.
- Observe Library unread counts only while needed. Reuse the active chapter index and completion snapshots so page and scroll updates do not recalculate full-library counts; preserve duplicate-release grouping and language overrides.
- Default custom libraries to All titles. Keep unsaved members visible, retain optional Saved/Liked/History/Downloaded intersections, show active restrictions, and reset to the unrestricted custom-library view.

