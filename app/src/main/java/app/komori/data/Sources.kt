package app.komori.data

import java.io.IOException
import java.text.Normalizer
import java.util.Base64
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

val sourceNames =
    linkedMapOf(
        "demonic" to "DemonicScans",
        "weeb" to "WeebCentral",
        "mangadex" to "MangaDex",
        "zap" to "HentaiZap",
        "imhentai" to "ImHentai",
        "nhentai" to "Nhentai",
    )

fun sourceKey(id: String) = id.substringBefore(':', "mangadex")

fun sourcePageUrl(id: String): String =
    when (sourceKey(id)) {
        "demonic" -> "https://demonicscans.org" + sourcePath(id)
        "weeb" -> "https://weebcentral.com" + sourcePath(id)
        "zap" -> "https://hentaizap.com" + sourcePath(id)
        "imhentai" -> "https://imhentai.xxx" + sourcePath(id)
        "nhentai" -> "https://nhentai.net" + sourcePath(id)
        else -> "https://mangadex.org/title/$id"
    }

fun sourceName(id: String) = sourceNames[sourceKey(id)] ?: "Unknown source"

fun sourceOrder(value: String) =
    (value.split(',').filter { it in sourceNames } + sourceNames.keys).distinct()

fun sourceId(source: String, path: String) =
    "$source:" +
        Base64.getUrlEncoder().withoutPadding().encodeToString(path.toByteArray(Charsets.UTF_8))

fun sourcePath(id: String): String =
    String(Base64.getUrlDecoder().decode(id.substringAfter(':')), Charsets.UTF_8)

fun normalizedTitle(title: String): String =
    Normalizer.normalize(title, Normalizer.Form.NFKC).lowercase(Locale.ROOT).filter {
        it.isLetterOrDigit()
    }

fun duplicateTitles(manga: Manga, library: List<Manga>, shelf: List<Shelf>): List<Manga> {
    val saved = shelf.filter { it.saved }.map { it.mangaId }.toSet()
    val name = normalizedTitle(manga.title)
    return library.filter {
        it.id != manga.id &&
            it.id in saved &&
            name.isNotEmpty() &&
            normalizedTitle(it.title) == name
    }
}

data class BrowseFilter(
    val sort: String = "",
    val tags: Set<String> = emptySet(),
    val searchType: String = "Title",
    val facetPath: String = "",
)

data class SourceTag(val id: String, val name: String)

data class BrowsePage(val titles: List<Manga>, val hasMore: Boolean)

/** Existing MangaDex UUIDs stay unchanged; other source IDs encode paths safely for local files. */
class Sources(val client: OkHttpClient) {
    private val dex = MangaDex(client)
    private val demonic = WebSource("demonic", "https://demonicscans.org", client)
    private val weeb = WebSource("weeb", "https://weebcentral.com", client)
    private val zap = WebSource("zap", "https://hentaizap.com", client)
    private val imhentai = WebSource("imhentai", "https://imhentai.xxx", client)
    private val nhentai = WebSource("nhentai", "https://nhentai.net", client)

    private fun web(key: String) =
        when (key) {
            "demonic" -> demonic
            "weeb" -> weeb
            "zap" -> zap
            "imhentai" -> imhentai
            "nhentai" -> nhentai
            else ->
                throw IOException(
                    "This source is no longer available. Choose another source."
                )
        }

    fun sorts(key: String): List<String> =
        when (key) {
            "demonic" -> listOf("Views", "Recently added", "Title")
            "weeb" ->
                listOf("Popularity", "Subscribers", "Latest Updates", "Recently Added", "Alphabet")
            "mangadex" -> listOf("Popularity", "Rating", "Latest updates", "Title")
            "zap", "imhentai" -> listOf("Latest", "Popular", "Top Rated", "Downloaded")
            "nhentai" -> listOf("Latest", "Popular today", "Popular this week", "Popular this month", "Popular all time")
            else -> emptyList()
        }

    suspend fun browse(
        key: String,
        query: String,
        page: Int,
        filter: BrowseFilter,
        creator: Creator?,
    ): BrowsePage =
        if (key == "mangadex")
            dex.browse(query, (page - 1) * 30, creator, filter).let {
                BrowsePage(it, it.size == 30)
            }
        else web(key).browse(query, page, filter, creator)

    suspend fun tags(key: String): List<SourceTag> =
        if (key == "mangadex") dex.tags() else web(key).tags()

    suspend fun details(manga: Manga): Manga =
        if (sourceKey(manga.id) == "mangadex") manga else web(sourceKey(manga.id)).details(manga)

    suspend fun chapters(id: String, language: String) =
        if (sourceKey(id) == "mangadex") dex.chapters(id, language)
        else web(sourceKey(id)).chapters(id)

    suspend fun languages(id: String) =
        if (sourceKey(id) == "mangadex") dex.languages(id) else listOf("en")

    suspend fun pages(id: String, saver: Boolean) =
        if (sourceKey(id) == "mangadex") dex.pages(id, saver) else web(sourceKey(id)).pages(id)

    fun imageRequest(url: String, id: String): Request =
        Request.Builder()
            .url(url)
            .apply {
                when (sourceKey(id)) {
                    "demonic" -> header("Referer", "https://demonicscans.org/")
                    "weeb" -> header("Referer", "https://weebcentral.com/")
                    "zap" -> header("Referer", "https://hentaizap.com/")
                    "imhentai" -> header("Referer", "https://imhentai.xxx/")
                    "nhentai" -> header("Referer", "https://nhentai.net/")
                }
            }
            .build()
}

class WebSource(
    private val key: String,
    private val base: String,
    private val client: OkHttpClient,
) {
    private suspend fun document(path: String, form: FormBody? = null): Document =
        withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url(base + path)
                    .header("Referer", "$base/")
                    .apply { if (form != null) post(form) }
                    .build()
            client.newCall(request).readResponse {
                if (!it.isSuccessful)
                    throw SourceHttpException(it.code, key)
                val body = it.body ?: throw IOException("Empty source response")
                val stream = body.source()
                if (stream.request(4L * 1024 * 1024 + 1))
                    throw IOException("The source returned an unexpectedly large page.")
                val html = stream.readUtf8()
                val doc = Jsoup.parse(html, base + path)
                if (
                    doc.title().contains("Just a moment", true) ||
                        doc.selectFirst("#cf-error-details") != null
                )
                    throw SourceVerificationRequired(key)
                doc
            }
        }

    suspend fun tags(): List<SourceTag> {
        if (key == "nhentai")
            return document("/tags/").select("a[href^=/tag/]").mapNotNull { a ->
                metadataLink(a, "tag")?.let { SourceTag(it.first, it.second) }
            }.distinctBy { it.id }
        if (key == "zap" || key == "imhentai") return galleryCategories
        if (key == "weeb")
            return listOf(
                    "Action",
                    "Adventure",
                    "Comedy",
                    "Drama",
                    "Fantasy",
                    "Historical",
                    "Horror",
                    "Isekai",
                    "Martial Arts",
                    "Mecha",
                    "Mystery",
                    "Romance",
                    "School Life",
                    "Sci-fi",
                    "Slice of Life",
                    "Sports",
                    "Supernatural",
                )
                .map { SourceTag(it, it) }
        return document("/advanced.php").select("input[name=\"genres[]\"]").map {
            SourceTag(it.attr("value"), it.parent()!!.text())
        }
    }

    suspend fun browse(
        query: String, page: Int, filter: BrowseFilter, creator: Creator? = null,
    ): BrowsePage {
        if (key in listOf("zap", "nhentai", "imhentai")) {
            val facet = when {
                creator != null -> {
                    require(sourceKey(creator.id) == key) { "Creator belongs to another source." }
                    sourcePath(creator.id)
                }
                filter.facetPath.isNotEmpty() -> filter.facetPath
                query.isNotBlank() && filter.searchType != "Title" -> {
                    val type = if (filter.searchType == "Author") "artist" else "tag"
                    "$base/".toHttpUrl().newBuilder().addPathSegment(type)
                        .addPathSegment(query.trim().lowercase(Locale.ROOT).replace(' ', '-'))
                        .addPathSegment("").build().encodedPath
                }
                else -> ""
            }
            if (facet.isNotEmpty()) {
                require(Regex("/(?:artist|author|tag)/[^/?#]+/").matches(facet)) { "Invalid metadata path." }
                val url = "$base$facet".toHttpUrl().newBuilder()
                    .addQueryParameter("page", page.toString())
                addGallerySort(url, filter.sort)
                val d = document(url.build().toString().removePrefix(base))
                return BrowsePage(parseBrowse(d), hasNext(d, page))
            }
        }
        if (key == "nhentai") {
            val terms = (listOf(query) + filter.tags.map { path ->
                val slug = path.removePrefix("/tag/").trimEnd('/')
                "tag:" + slug
            }).filter { it.isNotBlank() }.joinToString(" ")
            val url = "$base${if (terms.isBlank()) "/" else "/search/"}".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
            if (terms.isNotBlank()) url.addQueryParameter("q", terms)
            addGallerySort(url, filter.sort)
            val d = document(url.build().toString().removePrefix(base))
            return BrowsePage(parseBrowse(d), hasNext(d, page))
        }
        if (key == "zap" || key == "imhentai") return browseGallery(query, page, filter)
        val doc =
            if (key == "demonic") {
                if (query.isNotBlank()) {
                    if (filter.tags.isNotEmpty())
                        throw IOException(
                            "DemonicScans supports title search or genre browsing separately. Clear the title to browse tags."
                        )
                    if (page > 1) return BrowsePage(emptyList(), false)
                    document("/search.php?manga=" + java.net.URLEncoder.encode(query, "UTF-8"))
                } else {
                    val form =
                        FormBody.Builder()
                            .add("status", "all")
                            .add("submit", "Search")
                            .add(
                                "orderby",
                                when (filter.sort) {
                                    "Recently added" -> "ID DESC"
                                    "Title" -> "NAME ASC"
                                    else -> "VIEWS DESC"
                                },
                            )
                    filter.tags.forEach { form.add("genres[]", it) }
                    document("/advanced.php?list=$page", form.build())
                }
            } else {
                val url =
                    "$base/search/data"
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("text", query)
                        .addQueryParameter("limit", "30")
                        .addQueryParameter("offset", ((page - 1) * 30).toString())
                        .addQueryParameter("display_mode", "Full Display")
                        .addQueryParameter("sort", filter.sort.ifEmpty { "Popularity" })
                        .addQueryParameter(
                            "order",
                            if (filter.sort == "Alphabet") "Ascending" else "Descending",
                        )
                        .addQueryParameter("adult", "False")
                filter.tags.forEach { url.addQueryParameter("included_tag", it) }
                document(url.build().toString().removePrefix(base))
            }
        val titles = parseBrowse(doc)
        return BrowsePage(
            titles,
            if (key == "demonic")
                query.isBlank() &&
                    doc.select("a[href*=list=]").any { it.text().trim().equals("Next", true) }
            else doc.selectFirst("button") != null,
        )
    }

    fun parseBrowse(doc: Document): List<Manga> {
        if (key == "nhentai")
            return doc.select(".gallery > a.cover[href]").mapNotNull { link ->
                val path = link.absUrl("href").toHttpUrl().encodedPath
                val title = link.selectFirst(".caption")?.text().orEmpty()
                val cover = link.selectFirst("img")?.absUrl("src").orEmpty()
                if (!Regex("/g/\\d+/").matches(path) || title.isBlank() || !cover.startsWith("https://")) null
                else Manga(sourceId(key, path), title, "", cover)
            }.distinctBy { it.id }
        if (key == "zap" || key == "imhentai") return parseGalleryBrowse(doc)
        return doc.select(
                if (key == "demonic") "a[href^=/manga/]:has(img)"
                else
                    "article section a[href*=/series/]:has(img), article section a[href*=/series/]:has(source)"
            )
            .mapNotNull { a ->
                val img = a.selectFirst("img")
                val title =
                    if (key == "demonic")
                        a.attr("title")
                            .ifBlank { img?.attr("alt").orEmpty() }
                            .ifBlank { a.selectFirst(".seach-right > div")?.text().orEmpty() }
                    else
                        a.selectFirst("div:not([class]):last-child")?.text().orEmpty().ifBlank {
                            img?.attr("alt").orEmpty()
                        }
                if (title.isBlank()) null
                else
                    Manga(
                        sourceId(key, a.attr("abs:href").removePrefix(base)),
                        title,
                        "",
                        img?.absUrl("src").orEmpty().ifBlank {
                            a.selectFirst("source")?.absUrl("srcset").orEmpty().substringBefore(' ')
                        },
                    )
            }
            .distinctBy { it.id }
    }

    suspend fun details(manga: Manga): Manga {
        return parseDetails(manga, document(sourcePath(manga.id)))
    }

    fun parseDetails(manga: Manga, d: Document): Manga {
        val metadata = galleryMetadata(d)
        if (key == "nhentai") {
            val description = d.selectFirst("meta[name=description]")?.attr("content").orEmpty()
            val tags = d.select("#tags a[href^=/tag/] .name").eachText()
            return manga.copy(
                creatorsJson = metadata.first,
                tagsJson = metadata.second,
                title = d.selectFirst("#info h1.title")?.text()?.ifBlank { manga.title } ?: manga.title,
                cover = d.selectFirst("#cover img")?.absUrl("src")?.ifBlank { manga.cover } ?: manga.cover,
                description = listOf(description, tags.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Tags: "))
                    .filterNotNull().filter { it.isNotBlank() }.joinToString("\n\n").ifBlank { manga.description },
            )
        }
        if (key == "zap" || key == "imhentai") {
            val cover = d.selectFirst(if (key == "zap") ".hz-gallery-cover img" else ".left_cover img")
            return manga.copy(
                creatorsJson = metadata.first,
                tagsJson = metadata.second,
                title = d.selectFirst(if (key == "zap") "#gallery-title" else ".right_details h1")
                    ?.text()?.ifBlank { manga.title } ?: manga.title,
                description = d.selectFirst("meta[name=description]")?.attr("content")
                    ?.ifBlank { manga.description } ?: manga.description,
                cover = cover?.let { it.absUrl(if (it.hasAttr("data-src")) "data-src" else "src") }
                    ?.ifBlank { manga.cover } ?: manga.cover,
            )
        }
        val description =
            if (key == "demonic") d.selectFirst(".genres-list ~ .white-font")?.text()
            else d.selectFirst("li:has(strong:contains(Description)) > p")?.text()
        return manga.copy(description = description ?: manga.description)
    }

    private fun metadataLink(a: org.jsoup.nodes.Element, type: String): Pair<String, String>? {
        val url = a.absUrl("href").toHttpUrlOrNull() ?: return null
        if (url.host != base.toHttpUrl().host ||
            !Regex("/$type/[^/?#]+/").matches(url.encodedPath)) return null
        val label = a.clone().apply { select(".count, .badge").remove() }
        val name = (a.selectFirst(".name")?.text() ?: label.text())
            .replace(Regex("\\s+\\(\\d[\\d,.]*\\)$"), "").trim()
        return if (name.isBlank()) null else url.encodedPath to name
    }

    private fun galleryMetadata(d: Document): Pair<String, String> {
        val creators = JSONArray()
        val tags = JSONArray()
        val seen = mutableSetOf<String>()
        val metadata = if (key == "zap") d.clone().apply {
            select("header, footer, nav, aside, .hz-gallery-results, .hz-popular, .related-galleries, .hz-gallery-related").remove()
        } else d
        val selector = when (key) {
            "nhentai" -> "#tags a[href]"
            "zap" -> "a[href]"
            else -> ".right_details a[href]"
        }
        metadata.select(selector).forEach { a ->
            for (type in listOf("artist", "author", "tag")) {
                val (path, name) = metadataLink(a, type) ?: continue
                if (!seen.add(path)) continue
                if (type == "tag") tags.put(JSONObject().put("id", path).put("name", name))
                else creators.put(JSONObject().put("id", sourceId(key, path))
                    .put("name", name).put("role", type))
            }
        }
        return creators.toString() to tags.toString()
    }

    private fun addGallerySort(url: HttpUrl.Builder, sort: String) {
        if (key == "nhentai") {
            val value = when (sort) {
                "Popular today" -> "popular-today"
                "Popular this week" -> "popular-week"
                "Popular this month" -> "popular-month"
                "Popular all time" -> "popular"
                else -> ""
            }
            if (value.isNotEmpty()) url.addQueryParameter("sort", value)
        } else {
            val value = when (sort) {
                "Popular" -> "pp"
                "Top Rated" -> "tr"
                "Downloaded" -> "dl"
                else -> "lt"
            }
            url.addQueryParameter(value, "1")
        }
    }

    private fun hasNext(d: Document, page: Int): Boolean =
        d.selectFirst("a[rel=next], .pagination a.next[href]") != null ||
            d.select(".pagination a[href]").any {
                it.absUrl("href").toHttpUrlOrNull()?.queryParameter("page")?.toIntOrNull() == page + 1
            }

    suspend fun chapters(id: String): List<Chapter> {
        val path = sourcePath(id)
        if (key == "nhentai") {
            if (!Regex("/g/\\d+/").matches(path)) throw IOException("Invalid Nhentai gallery path.")
            val link = document(path).selectFirst("#cover > a[href]")
                ?: throw IOException("No reader link returned by Nhentai.")
            val reader = link.absUrl("href").toHttpUrl()
            if (reader.host != base.toHttpUrl().host || reader.encodedPath != "${path}1/")
                throw IOException("Unverified reader link returned by Nhentai.")
            return listOf(chapter(sourceId(key, reader.encodedPath), id, "Full gallery", null))
        }
        if (key == "zap" || key == "imhentai") {
            val d = document(path)
            val link = d.selectFirst(if (key == "zap") "a.hz-gallery-read" else ".left_cover > a")
                ?: throw IOException("No reader link returned by ${sourceNames[key]}.")
            val reader = link.absUrl("href").toHttpUrl()
            val prefix = if (key == "zap") "g" else "view"
            if (reader.host != base.toHttpUrl().host ||
                !Regex("/$prefix/\\d+/1/").matches(reader.encodedPath))
                throw IOException("Unverified reader link returned by ${sourceNames[key]}.")
            return listOf(chapter(sourceId(key, reader.encodedPath), id, "Full gallery", null))
        }
        val d =
            document(
                if (key == "demonic") path
                else
                    "/series/${path.substringAfter("/series/").substringBefore('/')}/full-chapter-list"
            )
        val links =
            d.select(if (key == "demonic") "a.chplinks" else "div[x-data] > a[href*=/chapters/]")
        if (links.isEmpty())
            throw IOException(
                "No chapter links returned by ${sourceNames[key]}. The source may have changed; your saved list is retained."
            )
        return links
            .map { a ->
                val url = a.absUrl("href")
                val title =
                    if (key == "demonic") a.text()
                    else a.selectFirst("span.flex > span")?.text() ?: a.text()
                val number =
                    if (key == "demonic") url.toHttpUrl().queryParameter("chapter")
                    else
                        Regex("(?i)(?:chapter|ch\\.)\\s*(\\d+(?:\\.\\d+)?)")
                            .find(title)
                            ?.groupValues
                            ?.get(1)
                chapter(sourceId(key, url.removePrefix(base)), id, title, number)
            }
            .distinctBy { it.id }
    }

    suspend fun pages(id: String): List<String> {
        val path = sourcePath(id)
        if (key == "nhentai") {
            if (!Regex("/g/\\d+/1/").matches(path)) throw IOException("Invalid Nhentai reader path.")
            return parsePages(document(path))
        }
        if (key == "zap") return parsePages(document(path))
        if (key == "imhentai") {
            if (!Regex("/view/\\d+/1/").matches(path))
                throw IOException("Invalid ImHentai reader path.")
            val first = document(path)
            val total = first.selectFirst("input#pages")?.attr("value")?.toIntOrNull()
                ?.takeIf { it in 1..10000 }
                ?: throw IOException("No valid page count returned by ImHentai.")
            return (1..total).map { page ->
                val d = if (page == 1) first else document(path.removeSuffix("1/") + "$page/")
                if (d.selectFirst("img#gimg.image_$page") == null)
                    throw IOException("ImHentai did not return reader page $page.")
                parsePages(d).singleOrNull()
                    ?: throw IOException("No image returned for ImHentai page $page.")
            }
        }
        val d =
            document(
                if (key == "demonic") path
                else "$path/images?is_prev=False&reading_style=long_strip"
            )
        return parsePages(d).ifEmpty {
            throw IOException(
                "No reader images returned by ${sourceNames[key]}. Please retry later."
            )
        }
    }

    fun parsePages(d: Document): List<String> {
        if (key == "nhentai") return parseNhentaiPages(d)
        if (key == "zap") return parseZapPages(d)
        if (key == "imhentai")
            return d.select("img#gimg").map { it.absUrl("src") }.filter { it.startsWith("https://") }
        return d.select(if (key == "demonic") "img.imgholder[alt]" else "section[x-data~=scroll] > img")
            .map { it.absUrl("src") }
            .filter { it.startsWith("https://") }
            .distinct()
    }

    private val galleryCategories = listOf(
        SourceTag("m", "Manga"),
        SourceTag("d", "Doujinshi"),
        SourceTag("w", "Western"),
        SourceTag("i", "Image Set"),
        SourceTag("a", "Artist CG"),
        SourceTag("g", "Game CG"),
    )

    private suspend fun browseGallery(query: String, page: Int, filter: BrowseFilter): BrowsePage {
        val url = if (query.isBlank() && filter.tags.isEmpty() &&
            (filter.sort.isEmpty() || filter.sort == "Latest")) {
            "$base/".toHttpUrl().newBuilder().addQueryParameter("page", page.toString())
        } else {
            val builder = "$base/search/".toHttpUrl().newBuilder()
                .addQueryParameter("key", query)
                .addQueryParameter("page", page.toString())
            if (key == "zap") builder.addQueryParameter("filter", "yes")
            val sort = when (filter.sort) {
                "Popular" -> "pp"
                "Top Rated" -> "tr"
                "Downloaded" -> "dl"
                else -> "lt"
            }
            listOf("lt", "pp", "tr", "dl").forEach {
                if (it == sort || key == "imhentai")
                    builder.addQueryParameter(it, if (it == sort) "1" else "0")
            }
            galleryCategories.forEach {
                val selected = filter.tags.isEmpty() || it.id in filter.tags
                if (selected || key == "imhentai")
                    builder.addQueryParameter(it.id, if (selected) "1" else "0")
            }
            listOf("en", "jp", "es", "fr", "kr", "de", "ru").forEach {
                builder.addQueryParameter(it, "1")
            }
            builder
        }
        val d = document(url.build().toString().removePrefix(base))
        val more = if (key == "zap") d.selectFirst("a[rel=next]") != null
            else d.select(".pagination li:not(.disabled) a[href]").any {
                it.absUrl("href").toHttpUrl().queryParameter("page")?.toIntOrNull() == page + 1
            }
        return BrowsePage(parseGalleryBrowse(d), more)
    }

    private fun parseGalleryBrowse(d: Document): List<Manga> =
        d.select(if (key == "zap") ".hz-gallery-results article.hz-gallery-card" else ".galleries .thumb")
            .mapNotNull { card ->
                val link = card.selectFirst(if (key == "zap") ".hz-gallery-card__title a" else ".gallery_title a")
                    ?: return@mapNotNull null
                val img = card.selectFirst(if (key == "zap") ".hz-gallery-card__cover img" else ".inner_thumb img")
                    ?: return@mapNotNull null
                val path = link.absUrl("href").toHttpUrl().encodedPath
                if (!Regex("/gallery/\\d+/").matches(path) || link.text().isBlank()) return@mapNotNull null
                Manga(sourceId(key, path), link.text(), "",
                    img.absUrl(if (img.hasAttr("data-src")) "data-src" else "src"))
            }.distinctBy { it.id }

    private fun parseZapPages(d: Document): List<String> {
        val reader = d.selectFirst("#readerApp[data-reader-image-base][data-reader-total]")
            ?: throw IOException("No page manifest returned by HentaiZap.")
        if (reader.attr("data-reader-locked") == "1")
            throw IOException("HentaiZap requires access verification for this gallery.")
        val total = reader.attr("data-reader-total").toIntOrNull()?.takeIf { it in 1..10000 }
            ?: throw IOException("No valid page count returned by HentaiZap.")
        val json = d.selectFirst("script#readerPagesJson[type=application/json]")?.data()
            ?: throw IOException("No page manifest returned by HentaiZap.")
        val manifest = try { JSONArray(json).objects().sortedBy { it.getInt("page") } }
            catch (e: org.json.JSONException) { throw IOException("Invalid HentaiZap page manifest.", e) }
        if (manifest.size != total || manifest.map { it.optInt("page") } != (1..total).toList())
            throw IOException("Incomplete HentaiZap page manifest.")
        val imageBase = reader.absUrl("data-reader-image-base").trimEnd('/')
        if (!imageBase.startsWith("https://")) throw IOException("Invalid HentaiZap image base.")
        val pages = manifest.map {
            val ext = it.optString("ext")
            if (ext !in listOf("jpg", "jpeg", "png", "webp", "gif", "avif"))
                throw IOException("Unsupported HentaiZap image extension.")
            "$imageBase/${it.getInt("page")}.$ext"
        }
        if (d.selectFirst("img#readerImg")?.absUrl("src") != pages.first())
            throw IOException("HentaiZap image and manifest do not match.")
        return pages
    }

    private fun parseNhentaiPages(d: Document): List<String> {
        val back = d.selectFirst(".reader-bar a.go-back[href]")?.absUrl("href")
            ?: throw IOException("No gallery identity returned by Nhentai.")
        val galleryId = Regex("/g/(\\d+)/").matchEntire(back.toHttpUrl().encodedPath)?.groupValues?.get(1)
            ?: throw IOException("Invalid Nhentai gallery identity.")
        val data = d.select("script[type=application/json][data-sveltekit-fetched][data-url]")
            .firstOrNull { it.attr("data-url") == "/api/v2/galleries/$galleryId" }?.data()
            ?: throw IOException("No page manifest returned by Nhentai.")
        try {
            val response = JSONObject(data)
            if (response.optInt("status") != 200) throw IOException("Nhentai page manifest is unavailable.")
            val gallery = JSONObject(response.getString("body"))
            if (gallery.optString("id") != galleryId) throw IOException("Nhentai gallery identity does not match.")
            val total = gallery.optInt("num_pages").takeIf { it in 1..10000 }
                ?: throw IOException("No valid page count returned by Nhentai.")
            val manifest = gallery.getJSONArray("pages").objects().sortedBy { it.getInt("number") }
            if (manifest.size != total || manifest.map { it.getInt("number") } != (1..total).toList())
                throw IOException("Incomplete Nhentai page manifest.")
            val image = d.selectFirst("#image-container img")?.absUrl("src")
                ?.takeIf { it.startsWith("https://") }?.toHttpUrl()
                ?: throw IOException("No reader image returned by Nhentai.")
            if (image.encodedPath != "/" + manifest.first().getString("path"))
                throw IOException("Nhentai image and manifest do not match.")
            val media = gallery.getString("media_id")
            if (!Regex("\\d+").matches(media)) throw IOException("Invalid Nhentai media identity.")
            return manifest.map {
                val path = it.getString("path")
                val number = it.getInt("number")
                if (!Regex("galleries/$media/$number\\.[A-Za-z0-9.]+").matches(path))
                    throw IOException("Invalid Nhentai page path.")
                image.newBuilder().encodedPath("/$path").query(null).fragment(null).build().toString()
            }
        } catch (e: org.json.JSONException) {
            throw IOException("Invalid Nhentai page manifest.", e)
        }
    }

    private fun chapter(id: String, manga: String, title: String, number: String?): Chapter {
        val attributes =
            JSONObject()
                .put("title", title)
                .put("chapter", number)
                .put("translatedLanguage", "en")
                .put("pages", -1)
        val relationships = JSONArray().put(JSONObject().put("type", "manga").put("id", manga))
        return Chapter(
            id,
            manga,
            JSONObject()
                .put("id", id)
                .put("attributes", attributes)
                .put("relationships", relationships)
                .toString(),
        )
    }
}
