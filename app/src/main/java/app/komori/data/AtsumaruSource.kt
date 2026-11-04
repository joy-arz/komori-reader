package app.komori.data

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

val atsumaruSources = setOf("atsu", "atsuadult")

/** The website's public comic API. Normal/adult IDs never share local ownership. */
class AtsumaruSource(private val key: String, private val client: OkHttpClient,
    private val base: String = "https://atsu.moe", private val media: String = "https://cdn.atsu.moe") {
    private val adult = key == "atsuadult"
    private val lock = Mutex()
    private data class Info(val manga: Manga, val groups: Map<String, String>, val at: Long)
    private val infoCache = object : LinkedHashMap<String, Info>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Info>?) = size > 16
    }
    @Volatile private var genres = emptyList<SourceTag>()

    private suspend fun json(path: String, parameters: Map<String, String> = emptyMap()): JSONObject = withContext(Dispatchers.IO) {
        val url = "$base$path".toHttpUrl().newBuilder().apply { parameters.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        client.newCall(Request.Builder().url(url).header("Referer", "$base/").build()).readResponse { response ->
            if (!response.isSuccessful) throw httpFailure(response, key)
            if (response.header("cf-mitigated") == "challenge") throw SourceVerificationRequired(key)
            val body = response.body?.source() ?: throw IOException("Empty Atsumaru response.")
            if (body.request(8L * 1024 * 1024 + 1)) throw ContentFailure("Atsumaru response exceeds 8 MB.")
            JSONObject(body.readUtf8()).also { if (it.optBoolean("success", true) == false) throw ContentFailure("Atsumaru could not complete this request.") }
        }
    }
    private fun identifier(value: String): String = value.also {
        require(it.matches(Regex("[A-Za-z0-9_-]{1,160}"))) { "Invalid Atsumaru identifier." }
    }
    private fun nativeManga(id: String): String {
        require(sourceKey(id) == key)
        val path = sourcePath(id)
        require(path.startsWith("/manga/") && path.count { it == '/' } == 2)
        return identifier(path.substringAfterLast('/'))
    }
    private fun quoted(value: String) = "`" + value.replace("`", "\\`") + "`"

    /** Keep absolute CDN URLs intact; relative API paths use the website's published CDN. */
    fun imageUrl(value: String): String {
        if (value.isBlank()) return ""
        val absolute = value.toHttpUrlOrNull()
        val url = if (absolute != null) absolute else {
            require(!value.startsWith("//") && !value.contains("://")) { "Invalid Atsumaru image URL." }
            "$media/".toHttpUrl().resolve(if (value.startsWith("/static/")) value else "/static/${value.removePrefix("static/").trimStart('/')}" )
                ?: throw ContentFailure("Invalid Atsumaru image location.")
        }
        require(url.isHttps || url.host == "localhost" || url.host == "127.0.0.1") { "Atsumaru images require HTTPS." }
        // Atsumaru publishes WebP alternatives for clients without AVIF decoding (Android 8–11).
        return if (url.encodedPath.endsWith(".avif", true)) url.newBuilder()
            .encodedPath(url.encodedPath.dropLast(5) + ".webp").build().toString() else url.toString()
    }
    private fun metadata(o: JSONObject): String {
        val tags = mutableListOf(SourceTag("en", "English", "language"), SourceTag(o.optString("type"), o.optString("type").replace("Manwha", "Manhwa"), "category"))
        o.optJSONArray("genres")?.objects()?.forEach { tags += SourceTag(it.getString("id"), it.getString("name")) }
        o.optJSONArray("genreIds")?.let { ids -> for (i in 0 until ids.length()) genres.find { it.id == ids.getString(i) }?.let { tags += it } }
        return JSONArray(tags.filter { it.id.isNotBlank() }.distinctBy { it.type to it.id }.map {
            JSONObject().put("id", it.id).put("name", it.name).put("type", it.type)
        }).toString()
    }
    private fun manga(o: JSONObject): Manga {
        val id = identifier(o.getString("id"))
        if (o.getBoolean("isAdult") != adult || o.optString("medium", "Comic") != "Comic") throw ContentFailure("This title belongs to another Atsumaru catalog.")
        val poster = o.opt("poster")
        val cover = when (poster) { is JSONObject -> poster.optString("image"); is String -> poster; else -> "" }
        val creators = o.optJSONArray("authors")?.let { authors ->
            JSONArray((0 until authors.length()).mapNotNull { n ->
                val author = authors.optJSONObject(n) ?: return@mapNotNull null
                val name = author.optString("name").trim()
                if (name.isBlank()) null else {
                    val role = if (author.optString("type").equals("Artist", true)) "artist" else "author"
                    val token = author.optString("id").takeUnless { it.isBlank() || it == "null" } ?: name
                    JSONObject().put("id", sourceId(key, "/$role/$token")).put("name", name).put("role", role)
                }
            })
        } ?: JSONArray()
        return Manga(sourceId(key, "/manga/$id"), o.getString("title"), Jsoup.parse(o.optString("synopsis")).text(),
            imageUrl(cover), creators.toString(), o.optString("status").lowercase(), metadata(o))
    }
    suspend fun browse(query: String, page: Int, filter: BrowseFilter, creator: Creator?): BrowsePage {
        require(page > 0 && filter.facetPath.isBlank())
        require(filter.language.isEmpty() || filter.language == "en")
        require(filter.tags.all { it.matches(Regex("[0-9]+")) })
        val clauses = mutableListOf("isAdult:=$adult", "hidden:!=true", "medium:=Comic")
        if (!adult) clauses += "mbContentRating:!=[`Erotica`,`Pornographic`]"
        filter.tags.forEach { clauses += "genreIds:=${quoted(it)}" }
        if (filter.category.isNotEmpty()) {
            require(sourceBrowseCategories(key).any { it.id == filter.category })
            clauses += "type:=${quoted(filter.category)}"
        }
        if (filter.status.isNotEmpty()) {
            val status = mapOf("ongoing" to "Ongoing", "completed" to "Completed", "hiatus" to "Hiatus", "cancelled" to "Canceled")[filter.status]
                ?: throw IllegalArgumentException("Unsupported Atsumaru status.")
            clauses += "status:=${quoted(status)}"
        }
        creator?.let {
            require(sourceKey(it.id) == key)
            val path = sourcePath(it.id)
            require(path.startsWith("/author/") || path.startsWith("/artist/"))
            clauses += "authors:=${quoted(it.name)}"
        }
        val response = json("/collections/manga/documents/search", mapOf("q" to query.ifBlank { "*" },
            "query_by" to "title,englishTitle,otherNames,authors,acronyms", "page" to "$page", "per_page" to "30",
            "sort_by" to when (filter.sort) { "Title" -> "title:asc"; "Recently added" -> "dateAdded:desc"; "Rating" -> "mbRating:desc"; else -> "views:desc" },
            "filter_by" to clauses.joinToString(" && ")))
        val hits = response.getJSONArray("hits").objects().map { it.getJSONObject("document") }
        val titles = hits.filter { it.opt("isAdult") is Boolean && it.getBoolean("isAdult") == adult && it.optString("medium", "Comic") == "Comic" }.map(::manga).distinctBy { it.id }
        val found = response.optInt("found", -1)
        return BrowsePage(titles, if (found >= 0) page.toLong() * 30 < found else hits.size == 30,
            paginationIds = hits.map { sourceId(key, "/manga/${identifier(it.getString("id"))}") })
    }
    suspend fun tags(): List<SourceTag> {
        val available = json("/api/explore/availableFilters").getJSONArray("genres").objects().map { SourceTag(it.getString("id"), it.getString("name")) }
        genres = available
        return available
    }
    private suspend fun info(id: String): Info = lock.withLock {
        val native = nativeManga(id)
        infoCache[id]?.takeIf { System.nanoTime() - it.at in 0 until 300_000_000_000L }?.let { return@withLock it }
        val page = json("/api/manga/page", mapOf("id" to native)).getJSONObject("mangaPage")
        val title = manga(page)
        if (title.id != id) throw ContentFailure("Atsumaru returned metadata for a different title.")
        Info(title, page.optJSONArray("scanlators")?.objects()?.associate { it.getString("id") to it.getString("name") }.orEmpty(), System.nanoTime())
            .also { infoCache[id] = it }
    }
    suspend fun details(manga: Manga): Manga = info(manga.id).manga
    suspend fun chapters(id: String): List<Chapter> {
        val native = nativeManga(id)
        val groups = try { info(id).groups } catch (e: ContentFailure) { throw e } catch (_: IOException) { emptyMap() }
        return json("/api/manga/allChapters", mapOf("mangaId" to native)).getJSONArray("chapters").objects().map { o ->
            val chapterId = sourceId(key, "/read/$native/${identifier(o.getString("id"))}")
            val group = o.optString("scanlationMangaId").takeUnless { it == "null" }.orEmpty()
            val relationships = JSONArray().put(JSONObject().put("type", "manga").put("id", id))
            if (group.isNotBlank()) relationships.put(JSONObject().put("type", "scanlation_group").put("id", sourceId(key, "/group/${identifier(group)}"))
                .put("attributes", JSONObject().put("name", groups[group] ?: "Unlabelled source release")))
            Chapter(chapterId, id, JSONObject().put("id", chapterId).put("attributes", JSONObject()
                .put("chapter", o.opt("number")).put("title", o.optString("title")).put("translatedLanguage", "en")
                .put("pages", o.optInt("pageCount", -1))).put("relationships", relationships).toString())
        }.distinctBy { it.id }
    }
    suspend fun pages(id: String): List<String> {
        require(sourceKey(id) == key)
        val path = sourcePath(id).split('/')
        require(path.size == 4 && path[1] == "read")
        val native = identifier(path[2]); val chapter = identifier(path[3])
        val data = json("/api/read/chapter", mapOf("mangaId" to native, "chapterId" to chapter)).getJSONObject("readChapter")
        if (data.getString("id") != chapter) throw ContentFailure("Atsumaru returned a different chapter.")
        return data.getJSONArray("pages").objects().sortedBy { it.getInt("number") }.map { imageUrl(it.getString("image")) }
            .ifEmpty { throw ContentFailure("This Atsumaru chapter has no readable images.") }
    }
}
