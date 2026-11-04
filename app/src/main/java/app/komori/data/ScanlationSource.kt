package app.komori.data

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

val scanlationSources = setOf("asura", "hive")

/** Public comic pages only. Locked chapters remain unavailable; no novel or account API is used. */
class ScanlationSource(private val key: String, private val base: String, private val client: OkHttpClient) {
    private val asura = key == "asura"
    private val root = if (asura) "comics" else "series"
    private suspend fun document(route: String): Document = withContext(Dispatchers.IO) {
        require(route.startsWith('/') && !route.startsWith("//"))
        client.newCall(Request.Builder().url(base + route).header("Referer", "$base/").build()).readResponse { response ->
            if (!response.isSuccessful) throw httpFailure(response, key)
            if (response.header("cf-mitigated") == "challenge") throw SourceVerificationRequired(key)
            val body = response.body?.source() ?: throw IOException("Empty source response.")
            if (body.request(8L * 1024 * 1024 + 1)) throw ContentFailure("Source page exceeds 8 MB.")
            Jsoup.parse(body.readUtf8(), base + route).also {
                if (it.title().contains("Just a moment", true) || it.selectFirst("#cf-error-details") != null) throw SourceVerificationRequired(key)
            }
        }
    }
    private fun route(url: String): String? = url.toHttpUrlOrNull()?.takeIf { it.host == base.toHttpUrl().host }?.encodedPath
    private fun titleRoute(path: String) = Regex("/$root/[^/]+/?").matches(path)
    private fun props(d: Document, component: String): JSONObject? = d.select("astro-island[props]")
        .firstOrNull { it.attr("component-url").contains("/$component.") }?.let { decodeAstro(JSONObject(it.attr("props"))) as JSONObject }
    private fun decodeAstro(value: Any?): Any? = when (value) {
        is JSONObject -> JSONObject().also { result -> value.keys().forEach { k -> result.put(k, decodeAstro(value.get(k))) } }
        is JSONArray -> {
            val payload = value.opt(1)
            if (value.optInt(0, -1) == 1 && payload is JSONArray) JSONArray().also { result ->
                for (i in 0 until payload.length()) result.put(decodeAstro(payload.get(i)))
            } else if (value.optInt(0, -1) == 0) decodeAstro(payload) else throw ContentFailure("Unsupported comic metadata encoding.")
        }
        else -> value
    }
    private fun englishTags(tags: List<SourceTag> = emptyList()) = JSONArray((tags + SourceTag("en", "English", "language")).map {
        JSONObject().put("id", it.id).put("name", it.name).put("type", it.type)
    }).toString()

    suspend fun browse(query: String, page: Int, filter: BrowseFilter, creator: Creator? = null): BrowsePage {
        require(page > 0 && filter.facetPath.isBlank())
        require(creator == null || asura && sourceKey(creator.id) == key) { "This source does not expose an author catalog." }
        val url = "$base/${if (asura) "browse/comics" else "series"}".toHttpUrl().newBuilder()
            .addQueryParameter("page", "$page")
            .apply {
                if (creator != null) {
                    val path = sourcePath(creator.id)
                    require(path.startsWith("/author/") || path.startsWith("/artist/"))
                    addQueryParameter(path.split('/')[1], path.substringAfter('/', "").substringAfter('/'))
                }
                if (query.isNotBlank()) addQueryParameter(if (asura) "q" else "searchTerm", query)
                if (filter.tags.isNotEmpty()) addQueryParameter("genres", filter.tags.joinToString(","))
                if (filter.status.isNotBlank()) addQueryParameter(if (asura) "status" else "seriesStatus", if (asura) filter.status else filter.status.uppercase())
                if (asura) {
                    addQueryParameter("sort", when (filter.sort) { "Popularity" -> "popular"; "Title" -> "name"; else -> "update" })
                    addQueryParameter("order", if (filter.sort == "Title") "asc" else "desc")
                } else {
                    addQueryParameter("sortBy", when (filter.sort) { "Popularity" -> "popular"; else -> "latest_chapters" })
                    addQueryParameter("sortDirection", "desc")
                }
            }.build()
        return parseBrowse(document(url.toString().removePrefix(base)), page)
    }
    fun parseBrowse(d: Document, page: Int): BrowsePage {
        val titles = d.select("a[href]").mapNotNull { a ->
            val path = route(a.absUrl("href"))?.takeIf(::titleRoute) ?: return@mapNotNull null
            val image = a.selectFirst("img[src]") ?: return@mapNotNull null
            val title = image.attr("alt").ifBlank { a.attr("title") }.trim()
            if (title.isBlank()) return@mapNotNull null
            Manga(sourceId(key, path.trimEnd('/')), title, "", image.absUrl("src"), tagsJson = englishTags())
        }.distinctBy { it.id }
        val totalPages = props(d, "BrowseFilters")?.optInt("initialTotalPages", 0) ?: 0
        val more = totalPages > page || d.select("a[href]").any {
            it.absUrl("href").toHttpUrlOrNull()?.let { url -> url.host == base.toHttpUrl().host && url.queryParameter("page")?.toIntOrNull()?.let { n -> n > page } == true } == true
        }
        return BrowsePage(titles, more)
    }
    suspend fun tags(): List<SourceTag> = parseTags(document(if (asura) "/browse/comics" else "/series"))
    fun parseTags(d: Document): List<SourceTag> {
        if (asura) return props(d, "BrowseFilters")?.optJSONArray("availableGenres")?.objects()?.map { SourceTag(it.get("id").toString(), it.getString("name")) }.orEmpty()
        val links = d.select("a[href*=genres]").mapNotNull { a ->
            val id = a.absUrl("href").toHttpUrlOrNull()?.queryParameter("genres") ?: return@mapNotNull null
            a.text().trim().takeIf { it.isNotBlank() }?.let { SourceTag(id, it) }
        }.distinctBy { it.id }
        val literal = "\"(?:[^\"\\\\]|\\\\.)*\""
        val genres = Regex("\\{id:([0-9]+),name:($literal),color:\"")
        val hydrated = d.select("script[data-tsr-stream-part]").flatMap { script ->
            genres.findAll(script.data()).map { match ->
                SourceTag("+${match.groupValues[1]}", JSONArray("[${match.groupValues[2]}]").getString(0))
            }.toList()
        }
        return (links + hydrated).distinctBy { it.id }
    }
    suspend fun details(manga: Manga): Manga = parseDetails(document(sourcePath(manga.id)), manga)
    fun parseDetails(d: Document, manga: Manga): Manga {
        val data = props(d, "DescriptionModal")
        val tags = if (data != null) data.optJSONArray("genres")?.objects()?.map { SourceTag(it.get("id").toString(), it.getString("name")) }.orEmpty()
            else d.select("[itemprop=genre]").mapNotNull { a -> a.absUrl("href").toHttpUrlOrNull()?.queryParameter("genres")?.let { SourceTag(it, a.text()) } }
        val creators = JSONArray()
        for (role in listOf("author", "artist")) {
            val name = data?.optString(role).orEmpty().takeUnless { it == "null" }.orEmpty()
            if (name.isNotBlank()) creators.put(JSONObject().put("id", sourceId(key, "/$role/$name")).put("name", name).put("role", role))
        }
        val status = data?.optString("status") ?: d.select("dl > div").firstOrNull { it.selectFirst("dt")?.text() == "Status" }?.selectFirst("dd")?.text().orEmpty()
        return manga.copy(title = d.selectFirst("h1")?.text()?.trim()?.ifBlank { manga.title } ?: manga.title,
            description = data?.optString("description")?.let { Jsoup.parse(it).text() }
                ?: d.selectFirst("[itemprop=description]")?.text() ?: d.selectFirst("meta[name=description]")?.attr("content") ?: manga.description,
            cover = data?.optString("coverUrl")?.takeIf { it.startsWith("https://") }
                ?: d.selectFirst("img[alt^='Cover of']")?.absUrl("src") ?: manga.cover,
            creatorsJson = creators.toString(), status = status.lowercase(), tagsJson = englishTags(tags))
    }
    suspend fun chapters(id: String): List<Chapter> = parseChapters(document(sourcePath(id)), id)
    fun parseChapters(d: Document, id: String): List<Chapter> {
        val path = sourcePath(id).trimEnd('/')
        require(titleRoute(path))
        fun row(route: String, number: String, title: String, pages: Int = -1, locked: Boolean = false): Chapter {
            val chapter = sourceId(key, route)
            return Chapter(chapter, id, JSONObject().put("id", chapter).put("attributes", JSONObject().put("chapter", number)
                .put("title", title).put("externalUrl", if (locked) base + route else JSONObject.NULL).put("pages", pages).put("translatedLanguage", "en").put("isUnavailable", locked))
                .put("relationships", JSONArray().put(JSONObject().put("type", "manga").put("id", id))).toString())
        }
        val rows = if (asura) {
            props(d, "ChapterListReact")?.optJSONArray("chapters")?.objects()?.map { c ->
                row("$path/chapter/${c.get("number")}", c.get("number").toString(), c.optString("title").takeUnless { it == "null" }.orEmpty(), c.optInt("page_count", -1), c.optBoolean("is_premium"))
            }.orEmpty()
        } else {
            // TanStack SSR serializes the full chapter list, while the rendered list contains only 25 rows.
            // Read only literal chapter records, without evaluating script or inventing chapter URLs.
            val literal = "\"(?:[^\"\\\\]|\\\\.)*\""
            val pattern = Regex("\\{id:[0-9]+,slug:($literal),number:([0-9]+(?:\\.[0-9]+)?),title:($literal|null),([^{}]*?)mangaPost:")
            d.select("script[data-tsr-stream-part]").flatMap { script -> pattern.findAll(script.data()).mapNotNull { match ->
                val slug = JSONArray("[${match.groupValues[1]}]").getString(0)
                if (!Regex("chapter-[A-Za-z0-9._-]+").matches(slug)) null else row("$path/$slug", match.groupValues[2],
                    JSONArray("[${match.groupValues[3]}]").optString(0).takeUnless { it == "null" }.orEmpty(), locked = match.groupValues[4].contains("isLocked:!0"))
            }.toList() }
        }
        val distinct = rows.distinctBy { it.id }
        val expected = if (asura) props(d, "ChapterListReact")?.optInt("totalChapters", 0) ?: 0
            else d.select("dl > div").firstOrNull { it.selectFirst("dt")?.text() == "Chapters" }?.selectFirst("dd")?.text()?.replace(",", "")?.toIntOrNull() ?: 0
        if (distinct.isEmpty() || distinct.size < expected) throw ContentFailure("No complete chapter list returned by ${sourceNames[key]}. Refresh the title or verify the source.")
        return distinct
    }
    suspend fun pages(id: String): List<String> = parsePages(document(sourcePath(id)))
    fun parsePages(d: Document): List<String> {
        if (props(d, "ChapterReader")?.optBoolean("isLocked") == true) throw ContentFailure("This chapter is locked on AsuraScans. Open the source website for access information.")
        return d.select(if (asura) "img[data-page-index]" else "img[data-reader-page-image=true]")
            .map { it.absUrl("src") }.filter { it.toHttpUrlOrNull()?.isHttps == true }.distinct()
            .ifEmpty { throw ContentFailure("No public reader images returned by ${sourceNames[key]}. This chapter may require source access.") }
    }
}
