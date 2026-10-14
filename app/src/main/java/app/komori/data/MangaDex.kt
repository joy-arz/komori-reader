package app.komori.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

interface MangaSource {
    suspend fun search(query: String, offset: Int = 0, creator: Creator? = null): List<Manga>

    suspend fun chapters(mangaId: String, language: String): List<Chapter>

    suspend fun languages(mangaId: String): List<String>

    suspend fun pages(chapterId: String, saver: Boolean): List<String>
}

class MangaDex(val client: OkHttpClient, private val baseUrl: String = "https://api.mangadex.org") :
    MangaSource {
    private val throttle = Mutex()
    private var lastRequest = 0L

    private suspend fun json(
        path: String,
        params: List<Pair<String, String>> = emptyList(),
    ): JSONObject =
        withContext(Dispatchers.IO) {
            throttle.withLock {
                val wait = 300 - ((System.nanoTime() / 1_000_000) - lastRequest)
                if (wait > 0) delay(wait)
                lastRequest = (System.nanoTime() / 1_000_000)
                val url =
                    "$baseUrl/$path"
                        .toHttpUrl()
                        .newBuilder()
                        .apply { params.forEach { addQueryParameter(it.first, it.second) } }
                        .build()
                client
                    .newCall(
                        Request.Builder()
                            .url(url)
                            .header(
                                "User-Agent",
                                "Komori/${app.komori.BuildConfig.VERSION_NAME} (Android reader)",
                            )
                            .build()
                    )
                    .execute()
                    .use { response ->
                        if (response.code == 429)
                            throw IOException(
                                "MangaDex is rate limiting requests. Please wait before retrying."
                            )
                        if (!response.isSuccessful)
                            throw IOException(
                                "MangaDex returned ${response.code}. Try again later."
                            )
                        JSONObject(response.body?.string() ?: throw IOException("Empty response"))
                    }
            }
        }

    override suspend fun search(query: String, offset: Int, creator: Creator?): List<Manga> {
        val params =
            mutableListOf(
                "limit" to "30",
                "offset" to offset.toString(),
                "includes[]" to "cover_art",
                "includes[]" to "author",
                "includes[]" to "artist",
                "contentRating[]" to "safe",
                "contentRating[]" to "suggestive",
                "order[followedCount]" to "desc",
            )
        if (query.isNotBlank()) params += "title" to query
        creator?.let { params += (if (it.role == "artist") "artists[]" else "authors[]") to it.id }
        return json("manga", params).getJSONArray("data").objects().map { o ->
            val a = o.getJSONObject("attributes")
            val relationships = o.getJSONArray("relationships").objects()
            val cover =
                relationships
                    .firstOrNull { it.optString("type") == "cover_art" }
                    ?.optJSONObject("attributes")
                    ?.optString("fileName")
            val names = a.getJSONObject("title")
            val title =
                names.nullable("en")
                    ?: names.keys().asSequence().firstOrNull()?.let { names.getString(it) }
                    ?: "Untitled"
            val creators =
                org.json.JSONArray().apply {
                    relationships
                        .filter { it.optString("type") in listOf("author", "artist") }
                        .forEach {
                            put(
                                JSONObject()
                                    .put("id", it.getString("id"))
                                    .put(
                                        "name",
                                        it.optJSONObject("attributes")?.optString("name")
                                            ?: "Unknown creator",
                                    )
                                    .put("role", it.getString("type"))
                            )
                        }
                }
            Manga(
                o.getString("id"),
                title,
                a.optJSONObject("description")?.nullable("en")
                    ?: "No English description available.",
                cover?.let {
                    "https://uploads.mangadex.org/covers/${o.getString("id")}/$it.256.jpg"
                } ?: "",
                creators.toString(),
                a.optString("status"),
            )
        }
    }

    override suspend fun chapters(mangaId: String, language: String): List<Chapter> {
        val all = mutableListOf<Chapter>()
        var offset = 0
        do {
            val result =
                json(
                    "manga/$mangaId/feed",
                    listOf(
                        "limit" to "500",
                        "offset" to "$offset",
                        "includes[]" to "scanlation_group",
                        "order[chapter]" to "asc",
                        "includeFuturePublishAt" to "0",
                        // Omit includeExternalUrl: 1 means external-only, 0 hosted-only.
                        "includeUnavailable" to "1",
                    ) +
                        if (language == "*") emptyList()
                        else listOf("translatedLanguage[]" to language),
                )
            val batch = result.getJSONArray("data").objects()
            val total = result.getInt("total")
            if (result.getInt("offset") != offset || (batch.isEmpty() && offset < total))
                throw IOException("Incomplete chapter list from MangaDex. Please refresh.")
            val previousIds = all.map { it.id }.toHashSet()
            if (batch.isNotEmpty() && batch.all { it.getString("id") in previousIds })
                throw IOException("MangaDex repeated a chapter page. Please refresh.")
            all += batch.map { Chapter(it.getString("id"), mangaId, it.toString()) }
            offset += batch.size
            if (batch.isEmpty()) break
            if (offset >= 10000 && offset < result.optInt("total"))
                throw IOException("This title exceeds the source's chapter paging limit.")
        } while (offset < result.optInt("total"))
        return all.distinctBy { it.id }
    }

    override suspend fun languages(mangaId: String): List<String> {
        val values =
            json("manga/$mangaId")
                .getJSONObject("data")
                .getJSONObject("attributes")
                .optJSONArray("availableTranslatedLanguages") ?: return emptyList()
        return (0 until values.length())
            .mapNotNull {
                values.optString(it).takeIf { code -> code.isNotBlank() && code != "null" }
            }
            .sorted()
    }

    override suspend fun pages(chapterId: String, saver: Boolean): List<String> {
        val result = json("at-home/server/$chapterId")
        val chapter = result.getJSONObject("chapter")
        val field = if (saver) "dataSaver" else "data"
        val files = chapter.getJSONArray(field)
        val path = if (saver) "data-saver" else "data"
        return (0 until files.length()).map {
            "${result.getString("baseUrl")}/$path/${chapter.getString("hash")}/${files.getString(it)}"
        }
    }

    companion object {
        fun client() =
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(40, TimeUnit.SECONDS)
                .callTimeout(60, TimeUnit.SECONDS)
                .build()
    }
}
