package app.komori.data

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

val manhwaSources = setOf("manhwa18", "manhwa18cc")

class ManhwaSource(private val key: String, private val base: String, private val client: OkHttpClient) {
    private val com = key == "manhwa18"
    private val titlePath = if (com) "manga" else "webtoon"
    @Volatile private var knownGenres = comGenres
    private suspend fun document(path: String): Document = withContext(Dispatchers.IO) {
        require(path.startsWith('/') && !path.startsWith("//"))
        val request = Request.Builder().url(base + path).header("Referer", "$base/").build()
        client.newCall(request).readResponse { response ->
            if (!response.isSuccessful) throw httpFailure(response, key)
            if (response.header("cf-mitigated") == "challenge") throw SourceVerificationRequired(key)
            val body = response.body?.source() ?: throw IOException("Empty source response")
            if (body.request(4L * 1024 * 1024 + 1)) throw ContentFailure("Source page exceeds 4 MB.")
            Jsoup.parse(body.readUtf8(), base + path).also {
                if (it.title().contains("Just a moment", true) || it.selectFirst("#cf-error-details") != null)
                    throw SourceVerificationRequired(key)
            }
        }
    }
    private fun path(url: String): String? = url.toHttpUrlOrNull()?.takeIf { it.host == base.toHttpUrl().host }?.encodedPath
    private fun image(element: Element?): String {
        if (element == null) return ""
        for (attr in listOf("data-src", "data-bg", "src")) {
            val url = element.absUrl(attr)
            if (url.startsWith("https://")) return url
        }
        val style = Regex("url\\(['\"]?([^)'\"\\s]+)").find(element.attr("style"))?.groupValues?.get(1)
        return style?.let { element.baseUri().toHttpUrl().resolve(it)?.toString() }?.takeIf { it.startsWith("https://") }.orEmpty()
    }
    private fun raw(manga: Manga) = manga.title.endsWith(" raw", true) || sourcePath(manga.id).trimEnd('/').endsWith("-raw")

    suspend fun browse(query: String, page: Int, filter: BrowseFilter): BrowsePage {
        require(page > 0 && filter.facetPath.isBlank()) { "Invalid Manhwa18 browse request." }
        require(filter.language.isBlank() || sourceBrowseLanguages(key).any { it.id == filter.language }) { "Unsupported chapter language." }
        require(filter.category.isBlank() || sourceBrowseCategories(key).any { it.id == filter.category }) { "Unsupported category." }
        require(filter.status in setOf("", "ongoing", "completed", "hiatus")) { "Unsupported publication status." }
        val selectedGenres = if (com && filter.tags.any { !it.matches(Regex("\\d+")) }) {
            val available = tags()
            filter.tags.map { value ->
                if (value.matches(Regex("\\d+"))) value else {
                    require(Regex("/genre/[a-z0-9-]+/?").matches(value)) { "Invalid genre link." }
                    available.firstOrNull { it.name.lowercase().replace(Regex("\\s+"), "-") == value.trimEnd('/').substringAfterLast('/') }?.id
                        ?: throw IllegalArgumentException("This genre is no longer available. Refresh the source filters.")
                }
            }.toSet()
        } else filter.tags
        val url = if (com) "$base/tim-kiem".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", "$page")
            if (query.isNotBlank()) addQueryParameter("q", query)
            addQueryParameter("sort", when (filter.sort) { "Views" -> "top"; "Likes" -> "like"; "Title" -> "az"; "Recently added" -> "new"; else -> "update" })
            val genres = selectedGenres + listOfNotNull(filter.category.takeIf { it.isNotEmpty() }, "63".takeIf { filter.language == "ko" })
            require(genres.all { it.matches(Regex("\\d+")) }) { "Invalid Manhwa18 genre." }
            if (genres.isNotEmpty()) addQueryParameter("accept_genres", genres.joinToString(","))
            if (filter.status.isNotEmpty()) addQueryParameter("status", when (filter.status) { "ongoing" -> "1"; "hiatus" -> "2"; "completed" -> "3"; else -> throw IllegalArgumentException("Unsupported status") })
        }.build() else {
            val route = if (query.isNotBlank()) "/search" else {
                val genre = filter.tags.singleOrNull()
                require(genre == null || Regex("/webtoon-genre/[^/?#]+").matches(genre)) { "Invalid Manhwa18.cc genre." }
                (genre ?: if (filter.language == "ko" || filter.category == "raw") "/raw" else if (filter.status == "completed") "/completed" else "/webtoons") + "/$page"
            }
            "$base$route".toHttpUrl().newBuilder().apply {
                if (query.isNotBlank()) { addQueryParameter("q", query.replace('\'', '’')); addQueryParameter("page", "$page") }
                addQueryParameter("orderby", when (filter.sort) { "Popularity" -> "trending"; "Rating" -> "rating"; "Title" -> "alphabet"; "Recently added" -> "latest"; else -> "" })
            }.build()
        }
        val doc = document(url.toString().removePrefix(base))
        val unfiltered = parseBrowse(doc)
        var titles = unfiltered.filter { (filter.language.isBlank() || raw(it) == (filter.language == "ko")) &&
            (com || filter.category.isBlank() || raw(it) == (filter.category == "raw")) }
        // This source ignores genre/category/status during text search. Refine the fetched page explicitly.
        if (!com && (query.isNotBlank() || filter.tags.size > 1 || filter.status.isNotBlank()) && (filter.tags.isNotEmpty() || filter.category.isNotEmpty() || filter.status.isNotEmpty())) {
            val enriched = mutableListOf<Manga>()
            for (chunk in titles.chunked(2)) enriched += coroutineScope { chunk.map { async { details(it) } }.awaitAll() }
            titles = enriched.filter { manga ->
                val tags = manga.metadata().map { it.id }.toSet()
                filter.tags.all { it in tags } &&
                    (filter.category.isEmpty() || raw(manga) == (filter.category == "raw")) &&
                    (filter.status.isEmpty() || manga.status.equals(filter.status, true))
            }
        }
        val more = doc.selectFirst(if (com) ".pagination_wrap a.next" else "#blog-pager li.next a") != null
        if (!com && query.isNotBlank() && filter.sort == "Title (this page)")
            titles = titles.sortedBy { it.title.lowercase(java.util.Locale.ROOT) }
        return BrowsePage(titles, more, unfiltered.map { it.id })
    }

    fun parseBrowse(doc: Document): List<Manga> = doc.select(if (com) ".thumb-item-flow" else ".page-item, .page-item-detail, .home-item, .manga-item").mapNotNull { card ->
        val a = card.selectFirst(if (com) ".series-title a" else "h3 a") ?: return@mapNotNull null
        val route = path(a.absUrl("href"))?.takeIf { Regex("/$titlePath/[^/]+/?").matches(it) } ?: return@mapNotNull null
        Manga(sourceId(key, route), a.text(), "", image(card.selectFirst(if (com) ".lazy-bg, .img-in-ratio" else "img")))
    }.distinctBy { it.id }

    suspend fun tags(): List<SourceTag> {
        val d = document(if (com) "/tim-kiem" else "/")
        return if (com) d.select("[data-genre-id]").mapNotNull { e ->
            e.attr("data-genre-id").takeIf { it.matches(Regex("\\d+")) }?.let { SourceTag(it, e.text()) }
        }.ifEmpty { comGenres }.also { knownGenres = it } else d.select("a[href*=/webtoon-genre/]").mapNotNull {
            path(it.absUrl("href"))?.let { p -> SourceTag(p, it.text().trim()) }
        }.filter { it.name.isNotBlank() }.distinctBy { it.id }
    }

    suspend fun details(manga: Manga): Manga = parseDetails(document(sourcePath(manga.id)), manga)
    fun parseDetails(d: Document, manga: Manga): Manga {
        val tags = if (com) d.select(".series-information a[href*=/genre/]") else d.select(".genres-content a")
        val metadata = JSONArray()
        tags.forEach { a -> path(a.absUrl("href"))?.let { route ->
            val id = if (com) knownGenres.firstOrNull { it.name.equals(a.text().trim(), true) }?.id ?: route else route
            metadata.put(JSONObject().put("id", id).put("name", a.text().trim()).put("type", "tag"))
        } }
        val statusText = if (com) d.select(".info-item:has(.info-name:contains(Status)) .info-value").text()
            else d.select(".post-content_item:has(.summary-heading:contains(Status)) .summary-content, .minfo:contains(Status) i").text()
        val status = when (statusText.lowercase().replace(" ", "")) { "completed" -> "completed"; "ongoing" -> "ongoing"; "onhold" -> "hiatus"; else -> "" }
        return manga.copy(title = d.selectFirst(if (com) ".series-name a, .au-bento h1" else ".post-title h1")?.ownText()?.ifBlank { manga.title } ?: manga.title,
            cover = image(d.selectFirst(if (com) ".series-cover .img-in-ratio" else ".summary_image img")).ifBlank { manga.cover },
            description = d.selectFirst(if (com) ".summary-content" else ".dsct, .ss-manga, .post-content_item .entry-content")?.text().orEmpty(),
            tagsJson = metadata.toString(), status = status)
    }

    suspend fun chapters(id: String): List<Chapter> {
        val root = sourcePath(id).trimEnd('/')
        val d = document(root)
        return d.select(if (com) "div.au-chgrid a.au-chtile, ul.list-chapters > a" else "ul.row-content-chapter li a").mapNotNull { a ->
            val route = path(a.absUrl("href"))?.takeIf { it.startsWith("$root/") && it.removePrefix("$root/").trim('/').isNotEmpty() } ?: return@mapNotNull null
            val title = a.attr("data-name").ifBlank { a.attr("title") }.ifBlank { a.text() }
            val chapterId = sourceId(key, route)
            val number = Regex("(?i)(?:chapter|chap|ch\\.?)\\s*(\\d+(?:\\.\\d+)?)").find(title)?.groupValues?.get(1)
            val attributes = JSONObject().put("title", title).put("chapter", number)
                .put("translatedLanguage", if (root.endsWith("-raw")) "ko" else "en").put("pages", -1)
            Chapter(chapterId, id, JSONObject().put("id", chapterId).put("attributes", attributes)
                .put("relationships", JSONArray().put(JSONObject().put("type", "manga").put("id", id))).toString())
        }.distinctBy { it.id }.ifEmpty { throw ContentFailure("No chapter links returned by ${sourceNames[key]}. Refresh the title or verify the source.") }
    }
    suspend fun pages(id: String): List<String> = parsePages(document(sourcePath(id)))
        .ifEmpty { throw ContentFailure("No reader images returned by ${sourceNames[key]}.") }
    fun parsePages(d: Document): List<String> = d.select(if (com) "#chapter-content img" else ".read-content img")
        .map(::image).filter { it.isNotBlank() }.distinct()

    companion object {
        val comGenres = listOf("4" to "Adult", "9" to "Doujinshi", "17" to "Harem", "24" to "Manga", "26" to "Manhwa", "28" to "Mature", "33" to "NTR", "36" to "Romance", "57" to "Webtoon", "59" to "Action", "60" to "Comedy", "61" to "BL", "62" to "Horror", "63" to "Raw", "65" to "Art", "68" to "Drama", "174" to "Fantasy")
            .map { SourceTag(it.first, it.second) }
    }
}
