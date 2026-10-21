package app.komori.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

const val MAX_BACKUP_BYTES = 16 * 1024 * 1024

/**
 * Only user metadata; session material, feeds, device settings and file/download state are
 * excluded.
 */
data class MetadataBackup(
    val createdAt: Long,
    val appVersion: String,
    val manga: List<Manga>,
    val shelf: List<Shelf>,
    val chapters: List<Chapter>,
    val progress: List<Progress>,
    val creators: List<Creator>,
    val preferences: List<TitlePreferences>,
    val categories: List<LibraryCategory> = emptyList(),
    val memberships: List<CategoryMembership> = emptyList(),
    val links: List<MigrationLink> = emptyList(),
)

object BackupCodec {
    private const val FORMAT = "komori.metadata.backup"
    private const val VERSION = 2
    private val idPattern = Regex("[A-Za-z0-9_-]+(?::[A-Za-z0-9_-]+)?")

    fun read(input: InputStream): MetadataBackup {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        var count = input.read(buffer)
        while (count != -1) {
            if (output.size() + count > MAX_BACKUP_BYTES)
                invalid("Backup exceeds the 16 MiB limit.")
            output.write(buffer, 0, count)
            count = input.read(buffer)
        }
        return decode(output.toByteArray())
    }

    fun decode(bytes: ByteArray): MetadataBackup {
        if (bytes.size > MAX_BACKUP_BYTES) invalid("Backup exceeds the 16 MiB limit.")
        try {
            val root = StrictJson(utf8(bytes)).document()
            if (root.text("format", 64) != FORMAT) invalid("This is not a Komori metadata backup.")
            val version = root.integer("version", 1, Long.MAX_VALUE)
            if (version > VERSION)
                invalid("This backup version is not supported. Update Komori before restoring it.")
            root.fields(*(listOf("format", "version", "createdAt", "appVersion", "manga", "shelf", "chapters", "progress", "creators", "preferences") +
                if (version >= 2) listOf("categories", "memberships", "links") else emptyList()).toTypedArray())
            val manga =
                root.rows("manga", 10_000).map { o ->
                    o.fields("id", "title", "description", "cover", "creators", "status", "tags")
                    val creators = o.rows("creators", 1000).map(::creator)
                    unique(creators.map { "${it.id}|${it.role}" }, "title creator")
                    val tags =
                        o.rows("tags", 2000).map { tag ->
                            tag.fields("id", "name", "type")
                            val id = tag.text("id", 4096, false)
                            if (id.startsWith('/')) safePath(id) else safeId(id)
                            JSONObject()
                                .put("id", id)
                                .put("name", tag.text("name", 1024, false))
                                .put(
                                    "type",
                                    tag.choice(
                                        "type",
                                        setOf("tag", "parody", "language", "category"),
                                    ),
                                )
                        }
                    unique(
                        tags.map { "${it.getString("id")}|${it.getString("type")}" },
                        "title tag",
                    )
                    Manga(
                        o.id("id"),
                        o.text("title", 4096, false),
                        o.text("description", 262_144),
                        o.url("cover"),
                        JSONArray(creators.map(::creatorJson)).toString(),
                        o.text("status", 128),
                        JSONArray(tags).toString(),
                    )
                }
            val shelf =
                root.rows("shelf", 10_000).map { o ->
                    o.fields("mangaId", "saved", "liked", "status", "lastRelease", "touched")
                    Shelf(
                        o.id("mangaId"),
                        o.boolean("saved"),
                        o.boolean("liked"),
                        o.choice(
                            "status",
                            setOf("Plan to read", "Reading", "On hold", "Completed", "Dropped"),
                        ),
                        o.nullableId("lastRelease"),
                        o.integer("touched", 0, Long.MAX_VALUE),
                    )
                }
            val chapters = root.rows("chapters", 50_000).map(::chapter)
            val progress =
                root.rows("progress", 50_000).map { o ->
                    o.fields("releaseId", "mangaId", "chapterKey", "page", "completed", "scroll")
                    val scroll =
                        (o.opt("scroll") as? Number)?.toDouble()?.takeIf {
                            it.isFinite() && it in 0.0..1.0
                        } ?: invalid("Invalid reading scroll position.")
                    Progress(
                        o.id("releaseId"),
                        o.id("mangaId"),
                        o.text("chapterKey", 8192, false),
                        o.integer("page", 0, 100_000).toInt(),
                        o.boolean("completed"),
                        scroll.toFloat(),
                    )
                }
            val creators = root.rows("creators", 10_000).map(::creator)
            val preferences =
                root.rows("preferences", 10_000).map { o ->
                    o.fields(*(listOf("mangaId", "groupId", "strict", "readerMode") + if (version >= 2) listOf("language") else emptyList()).toTypedArray())
                    val group = o.text("groupId", 4096).also { if (it.isNotEmpty()) safeId(it) }
                    TitlePreferences(
                        o.id("mangaId"),
                        group,
                        o.boolean("strict"),
                        o.choice("readerMode", setOf("", "ltr", "rtl", "webtoon")),
                        if (version >= 2) o.text("language", 64).also {
                            if (!it.matches(Regex("(?:[a-z]{2,3}(?:-[A-Za-z]{2,8})*|\\*|)"))) invalid("Invalid per-title language.")
                        } else "",
                    )
                }
            val result =
                MetadataBackup(
                    root.integer("createdAt", 0, Long.MAX_VALUE),
                    root.text("appVersion", 128, false),
                    manga,
                    shelf,
                    chapters,
                    progress,
                    creators,
                    preferences,
                    if (version >= 2) root.rows("categories", 1000).map { o ->
                        o.fields("id", "name"); LibraryCategory(o.id("id"), o.text("name", 100, false))
                    } else emptyList(),
                    if (version >= 2) root.rows("memberships", 50000).map { o ->
                        o.fields("mangaId", "categoryId"); CategoryMembership(o.id("mangaId"), o.id("categoryId"))
                    } else emptyList(),
                    if (version >= 2) root.rows("links", 10000).map { o ->
                        o.fields("fromId", "toId", "createdAt"); MigrationLink(o.id("fromId"), o.id("toId"), o.integer("createdAt", 0, Long.MAX_VALUE))
                    } else emptyList(),
                )
            relationships(result)
            return result
        } catch (e: InvalidBackup) {
            throw e
        } catch (e: Exception) {
            throw InvalidBackup("Backup fields are invalid or incomplete.")
        }
    }

    /**
     * Round-trip validation also prevents exporting a damaged/inconsistent local metadata snapshot.
     */
    fun encode(backup: MetadataBackup): ByteArray {
        if (
            backup.manga.size > 10_000 ||
                backup.shelf.size > 10_000 ||
                backup.chapters.size > 50_000 ||
                backup.progress.size > 50_000 ||
                backup.creators.size > 10_000 ||
                backup.preferences.size > 10_000 || backup.categories.size > 1000 || backup.memberships.size > 50000 || backup.links.size > 10000
        )
            invalid("Backup exceeds the supported entry limits.")
        val root =
            JSONObject()
                .put("format", FORMAT)
                .put("version", VERSION)
                .put("createdAt", backup.createdAt)
                .put("appVersion", backup.appVersion)
                .put(
                    "manga",
                    JSONArray(
                        backup.manga.map { row ->
                            JSONObject()
                                .put("id", row.id)
                                .put("title", row.title)
                                .put("description", row.description)
                                .put("cover", row.cover)
                                .put("status", row.status)
                                .put("creators", JSONArray(row.creatorsJson))
                                .put(
                                    "tags",
                                    JSONArray(
                                        JSONArray(row.tagsJson).objects().map { tag ->
                                            JSONObject()
                                                .put("id", tag.getString("id"))
                                                .put("name", tag.getString("name"))
                                                .put("type", tag.optString("type", "tag"))
                                        }
                                    ),
                                )
                        }
                    ),
                )
                .put(
                    "shelf",
                    JSONArray(
                        backup.shelf.map { row ->
                            JSONObject()
                                .put("mangaId", row.mangaId)
                                .put("saved", row.saved)
                                .put("liked", row.liked)
                                .put("status", row.status)
                                .put("lastRelease", row.lastRelease ?: JSONObject.NULL)
                                .put("touched", row.touched)
                        }
                    ),
                )
                .put("chapters", JSONArray(backup.chapters.map(::chapterJson)))
                .put(
                    "progress",
                    JSONArray(
                        backup.progress.map { row ->
                            JSONObject()
                                .put("releaseId", row.releaseId)
                                .put("mangaId", row.mangaId)
                                .put("chapterKey", row.chapterKey)
                                .put("page", row.page)
                                .put("completed", row.completed)
                                .put("scroll", row.scroll)
                        }
                    ),
                )
                .put("creators", JSONArray(backup.creators.map(::creatorJson)))
                .put(
                    "preferences",
                    JSONArray(
                        backup.preferences.map { row ->
                            JSONObject()
                                .put("mangaId", row.mangaId)
                                .put("groupId", row.groupId)
                                .put("strict", row.strict)
                                .put("readerMode", row.readerMode).put("language", row.language)
                        }
                    ),
                )
        root.put("categories", JSONArray(backup.categories.map { JSONObject().put("id", it.id).put("name", it.name) }))
        root.put("memberships", JSONArray(backup.memberships.map { JSONObject().put("mangaId", it.mangaId).put("categoryId", it.categoryId) }))
        root.put("links", JSONArray(backup.links.map { JSONObject().put("fromId", it.fromId).put("toId", it.toId).put("createdAt", it.createdAt) }))
        val bytes = boundedJson(root)
        decode(bytes)
        return bytes
    }

    private fun creator(o: JSONObject): Creator {
        o.fields("id", "name", "role")
        return Creator(
            o.id("id"),
            o.text("name", 4096, false),
            o.choice("role", setOf("author", "artist")),
        )
    }

    private fun creatorJson(row: Creator) =
        JSONObject().put("id", row.id).put("name", row.name).put("role", row.role)

    private fun chapterJson(row: Chapter): JSONObject {
        val r = row.release()
        if (r.id != row.id || r.mangaId != row.mangaId)
            invalid("Chapter identity does not match its metadata.")
        val groups =
            JSONObject(row.json)
                .getJSONArray("relationships")
                .objects()
                .filter { it.optString("type") == "scanlation_group" }
                .map { group ->
                    JSONObject()
                        .put("id", group.getString("id"))
                        .put(
                            "name",
                            group.optJSONObject("attributes")?.optString("name") ?: "Unknown group",
                        )
                }
        return JSONObject()
            .put("id", row.id)
            .put("mangaId", row.mangaId)
            .put("number", r.number ?: JSONObject.NULL)
            .put("volume", r.volume ?: JSONObject.NULL)
            .put("title", r.title)
            .put("language", r.language)
            .put("groups", JSONArray(groups))
            .put("pages", r.pages)
            .put("externalUrl", r.externalUrl ?: JSONObject.NULL)
            .put("unavailable", r.unavailable)
    }

    private fun chapter(o: JSONObject): Chapter {
        o.fields(
            "id",
            "mangaId",
            "number",
            "volume",
            "title",
            "language",
            "groups",
            "pages",
            "externalUrl",
            "unavailable",
        )
        fun chapterNumber(field: String): String? =
            o.nullableText(field, 128)?.also { text ->
                text.toBigDecimalOrNull()?.let {
                    if (it.scale().toLong() !in -1000L..1000L || it.precision() > 128)
                        invalid("Chapter numbering exceeds the supported range.")
                }
            }
        val id = o.id("id")
        val mangaId = o.id("mangaId")
        val groups =
            o.rows("groups", 1000).map { group ->
                group.fields("id", "name")
                JSONObject()
                    .put("id", group.id("id"))
                    .put("type", "scanlation_group")
                    .put("attributes", JSONObject().put("name", group.text("name", 4096)))
            }
        unique(groups.map { it.getString("id") }, "scanlation group")
        val external = o.nullableText("externalUrl", 8192)?.also(::safeUrl)
        val attributes =
            JSONObject()
                .put("chapter", chapterNumber("number") ?: JSONObject.NULL)
                .put("volume", chapterNumber("volume") ?: JSONObject.NULL)
                .put("title", o.text("title", 4096))
                .put("translatedLanguage", o.text("language", 32))
                .put("pages", o.integer("pages", -1, 100_000))
                .put("externalUrl", external ?: JSONObject.NULL)
                .put("isUnavailable", o.boolean("unavailable"))
        val relationships = JSONArray().put(JSONObject().put("id", mangaId).put("type", "manga"))
        groups.forEach { relationships.put(it) }
        return Chapter(
            id,
            mangaId,
            JSONObject()
                .put("id", id)
                .put("attributes", attributes)
                .put("relationships", relationships)
                .toString(),
        )
    }

    private fun relationships(backup: MetadataBackup) {
        unique(backup.manga.map { it.id }, "title")
        unique(backup.shelf.map { it.mangaId }, "shelf entry")
        unique(backup.chapters.map { it.id }, "chapter")
        unique(backup.progress.map { it.releaseId }, "reading position")
        unique(backup.creators.map { it.id }, "followed creator")
        unique(backup.preferences.map { it.mangaId }, "title preference")
        unique(backup.categories.map { it.id }, "category")
        unique(backup.memberships.map { "${it.mangaId}|${it.categoryId}" }, "category membership")
        unique(backup.links.map { "${it.fromId}|${it.toId}" }, "migration link")
        val categoryIds = backup.categories.map { it.id }.toSet()
        val titles = backup.manga.map { it.id }.toSet()
        if (backup.memberships.any { it.mangaId !in titles || it.categoryId !in categoryIds } ||
            backup.links.any { it.fromId !in titles || it.toId !in titles || it.fromId == it.toId })
            invalid("Category or migration link refers to missing metadata.")
        val chapters = backup.chapters.associateBy { it.id }
        val positions = backup.progress.associateBy { it.releaseId }
        for (id in
            backup.shelf.map { it.mangaId } +
                backup.chapters.map { it.mangaId } +
                backup.progress.map { it.mangaId } +
                backup.preferences.map { it.mangaId }) {
            if (id !in titles)
                invalid("Backup refers to a title that is missing from its metadata.")
        }
        for (row in backup.progress) {
            if (!row.chapterKey.startsWith("${row.mangaId}|"))
                invalid("Reading position belongs to a different title.")
            chapters[row.releaseId]?.let {
                // Source edits may change chapter labels after a position was saved.
                if (it.mangaId != row.mangaId)
                    invalid("Reading position does not match its chapter.")
            }
        }
        for (row in backup.shelf) row.lastRelease?.let { id ->
            if (
                chapters[id]?.mangaId?.let { it != row.mangaId } == true ||
                    positions[id]?.mangaId?.let { it != row.mangaId } == true
            )
                invalid("Last-read chapter belongs to a different title.")
        }
    }

    private fun unique(ids: List<String>, label: String) {
        if (ids.toSet().size != ids.size) invalid("Duplicate $label in backup.")
    }

    private fun JSONObject.fields(vararg expected: String) {
        if (keys().asSequence().toSet() != expected.toSet())
            invalid("Backup contains missing or unsupported fields.")
    }

    private fun JSONObject.text(key: String, max: Int, empty: Boolean = true): String =
        (opt(key) as? String)?.takeIf {
            it.length <= max && (empty || it.isNotBlank()) && '\u0000' !in it
        } ?: invalid("Invalid $key text in backup.")

    private fun JSONObject.nullableText(key: String, max: Int): String? =
        if (opt(key) === JSONObject.NULL) null else text(key, max, false)

    private fun JSONObject.boolean(key: String): Boolean =
        opt(key) as? Boolean ?: invalid("Invalid $key flag in backup.")

    private fun JSONObject.integer(key: String, min: Long, max: Long): Long =
        (opt(key) as? Long)?.takeIf { it in min..max } ?: invalid("Invalid $key integer in backup.")

    private fun JSONObject.choice(key: String, values: Set<String>): String =
        text(key, 128).takeIf { it in values } ?: invalid("Unsupported $key value in backup.")

    private fun JSONObject.rows(key: String, limit: Int): List<JSONObject> {
        val array = opt(key) as? JSONArray ?: invalid("Invalid $key list in backup.")
        if (array.length() > limit) invalid("Too many $key entries in backup.")
        return array.objects()
    }

    private fun JSONObject.id(key: String): String = text(key, 4096, false).also(::safeId)

    private fun JSONObject.nullableId(key: String): String? =
        nullableText(key, 4096)?.also(::safeId)

    private fun JSONObject.url(key: String): String =
        text(key, 8192).also { if (it.isNotEmpty()) safeUrl(it) }

    private fun safeUrl(value: String) {
        val uri = runCatching { URI(value) }.getOrNull()
        if (
            uri?.scheme !in listOf("https", "http") ||
                uri?.host.isNullOrEmpty() ||
                uri?.rawUserInfo != null
        )
            invalid("Backup contains an invalid web address.")
    }

    private fun safeId(value: String) {
        if (!value.matches(idPattern))
            invalid("Backup contains an unsafe identifier.")
        if (':' in value) {
            val encoded = value.substringAfter(':')
            val bytes =
                runCatching { Base64.getUrlDecoder().decode(encoded) }.getOrNull()
                    ?: invalid("Backup contains an invalid source identifier.")
            if (Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) != encoded)
                invalid("Backup contains a noncanonical source identifier.")
            safePath(utf8(bytes))
        }
    }

    private fun safePath(path: String) {
        if (
            !path.startsWith('/') ||
                path.startsWith("//") ||
                '\\' in path ||
                path.any { it < ' ' } ||
                path.substringBefore('?').substringBefore('#').split('/').any {
                    it == ".." || it == "."
                }
        )
            invalid("Backup contains an unsafe source path.")
    }

    private fun utf8(bytes: ByteArray): String =
        try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: Exception) {
            invalid("Backup is not valid UTF-8 text.")
        }

    private fun invalid(message: String): Nothing = throw InvalidBackup(message)
}
