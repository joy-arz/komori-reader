# Library backup and restore

Open **Settings → App → Library backup**. **Export backup** creates a JSON file in a location chosen through Android's document picker. **Restore backup** opens a file and validates it before displaying a review dialog. Cancelling either picker or the review leaves the Library unchanged.

The file contains Library titles and their metadata, saved/liked/status flags, reading history and page/scroll positions, followed authors/artists, referenced chapter metadata, and per-title reader/scanlation preferences. Cached discovery feeds, device-wide settings, source cookies, verification sessions, image files and download queues are excluded. Restoring metadata on a new device does not create offline image downloads.

## Review and merge

The preview shows the backup date and originating app version, incoming title/save/like/follow/progress counts, and counts that already exist on this device. Matching is by the original source identifier, never by title text.

By default, existing values are kept and missing entries are added. Select **Use backup values for matching entries** to replace matching metadata, shelf flags, progress, creator follows and title preferences. Unrelated local entries remain. This choice applies to matching entries as a whole; it does not combine individual fields or select whichever position is furthest along.

The importer checks chapter ownership against chapter rows, reading positions, last-read references and existing offline download records. A conflicting title identity aborts the restore. Valid historical progress is retained when a source has edited a chapter's number or label since it was read.

All metadata changes commit in one Room transaction. Validation errors, identifier conflicts and database write failures leave the pre-restore metadata intact. Image files and WorkManager jobs are not modified. A failed export attempts to remove its newly created incomplete document; some document providers may not permit that cleanup.

## Format and limits

Format identifier: `komori.metadata.backup`. Format version: `1`. Files use UTF-8 JSON and include the originating app version independently of the format version.

| Limit | Maximum |
|---|---:|
| Encoded file size | 16 MiB |
| Titles, shelf entries, followed creators, title preferences | 10,000 of each |
| Chapter metadata and reading positions | 50,000 of each |
| JSON nesting | 24 levels |
| JSON values | 500,000 |

The limits apply together. An export can reach the encoded byte or structure limit before reaching the record-count limits. Export checks the database size/counts before materializing a snapshot, and the JSON writer enforces the encoded byte limit while escaping text. Import checks the byte limit while reading, then validates syntax, types, supported fields, duplicates, ranges and relationships.

Unsupported versions, duplicate object keys, nonstandard JSON, invalid UTF-8, unsafe identifiers, invalid web addresses and broken title ownership are rejected. Unavailable source identifiers retain their metadata and appear as a warning in the preview; restoring a backup does not add support for a removed source.

The JSON is readable metadata, including reading history. Choose a storage location appropriate for that data. Scheduled exports, retention management, device-wide settings backup and image/archive export remain separate roadmap items.
