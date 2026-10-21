# Library backup and restore

Open **Settings → App → Library backup**. **Export backup** creates a JSON file in a location chosen through Android's document picker. **Restore backup** opens a file and validates it before displaying a review dialog. Cancelling either picker or the review leaves the Library unchanged.

The file contains Library titles and their metadata, saved/liked/status flags, reading history and page/scroll positions, followed authors/artists, referenced chapter metadata, and per-title reader/scanlation/language preferences, categories and memberships, and reviewed source/edition links. Cached discovery feeds, device-wide settings, source cookies, verification sessions, image files and download queues are excluded. Restoring metadata on a new device does not create offline image downloads.

## Review and merge

The preview shows the backup date and originating app version, incoming title/save/like/follow/progress counts, and counts that already exist on this device. Matching is by the original source identifier, never by title text.

By default, existing values are kept and missing entries are added. Select **Use backup values for matching entries** to replace matching metadata, shelf flags, progress, creator follows and title preferences. Unrelated local entries remain. This choice applies to matching entries as a whole; it does not combine individual fields or select whichever position is furthest along.

The importer checks chapter ownership against chapter rows, reading positions, last-read references and existing offline download records. A conflicting title identity aborts the restore. Valid historical progress is retained when a source has edited a chapter's number or label since it was read.

All metadata changes commit in one Room transaction. Validation errors, identifier conflicts and database write failures leave the pre-restore metadata intact. Image files and WorkManager jobs are not modified. A failed export attempts to remove its newly created incomplete document; some document providers may not permit that cleanup.

## Format and limits

Format identifier: `komori.metadata.backup`. Format version: `2`. Version 1 imports remain supported; missing organization fields use empty defaults. Files use UTF-8 JSON and include the originating app version independently of the format version.

| Limit | Maximum |
|---|---:|
| Encoded file size | 16 MiB |
| Titles, shelf entries, followed creators, title preferences | 10,000 of each |
| Chapter metadata and reading positions | 50,000 of each |
| Categories / category memberships / edition links | 1,000 / 50,000 / 10,000 |
| JSON nesting | 24 levels |
| JSON values | 500,000 |

The limits apply together. An export can reach the encoded byte or structure limit before reaching the record-count limits. Export checks the database size/counts before materializing a snapshot, and the JSON writer enforces the encoded byte limit while escaping text. Import checks the byte limit while reading, then validates syntax, types, supported fields, duplicates, ranges and relationships.

Unsupported versions, duplicate object keys, nonstandard JSON, invalid UTF-8, unsafe identifiers, invalid web addresses and broken title ownership are rejected. Unavailable source identifiers retain their metadata and appear as a warning in the preview; restoring a backup does not add support for a removed source.

The JSON is readable metadata, including reading history. Choose a storage location appropriate for that data. Device-wide settings remain outside the backup format. Completed chapters can be exported as CBZ separately. Imported local pages are excluded from metadata backups and must be kept as separate CBZ/folder copies.


## Automatic metadata backups (0.8.1)

Choose **Settings → App → Library backup → Choose backup folder**, grant lasting read/write access, then choose Daily or Weekly. Off disables periodic work. **Back up now** runs once in the chosen folder even when the schedule is Off. WorkManager persists the schedule across app restarts; Android may defer it for battery/storage conditions. Cloud document providers require their own connectivity and account access. No global storage permission is requested.

Keep 3, 7 or 14 verified automatic backups. New files are read back, compared byte-for-byte and parsed before retention runs. Only names in Komori's `komori-auto-<timestamp>-<uuid>.json` format are candidates; manual exports and unrelated documents remain. Older unreadable files do not count toward the verified retention target. Verification/pruning failures appear in the backup status, alongside the last successful timestamp. Re-select a folder if its access expires.

Automatic files use the same format and reviewed restore flow as manual exports. Choose **Restore backup**, open the chosen backup folder and select a JSON file. Metadata and exclusions are identical; local comics/downloaded pages still need separate copies. Automatic backup settings are opt-in and device-specific.
