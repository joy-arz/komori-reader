package app.komori.data

import java.util.Locale
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class CoverLanguage(val code: String, val name: String, val slug: String)

private val coverLanguages =
    listOf(
            Triple("en", "English", listOf("en", "eng", "gb", "us")),
            Triple("jp", "Japanese", listOf("jp", "ja", "jpn")),
            Triple("sp", "Spanish", listOf("sp", "es", "spa", "spanish", "spanyol", "español")),
            Triple("fr", "French", listOf("fr", "fra", "fre")),
            Triple("kr", "Korean", listOf("kr", "ko", "kor")),
            Triple("ch", "Chinese", listOf("ch", "cn", "zh", "zho", "chi", "zh-hans", "zh-hant")),
            Triple("de", "German", listOf("de", "deu", "ger")),
            Triple("ru", "Russian", listOf("ru", "rus")),
            Triple("it", "Italian", listOf("it", "ita")),
            Triple("pt", "Portuguese", listOf("pt", "por", "pt-br", "brazilian")),
            Triple("id", "Indonesian", listOf("id", "ind")),
            Triple("th", "Thai", listOf("th", "tha")),
            Triple("vi", "Vietnamese", listOf("vi", "vie")),
            Triple("pl", "Polish", listOf("pl", "pol")),
            Triple("tr", "Turkish", listOf("tr", "tur")),
            Triple("ar", "Arabic", listOf("ar", "ara")),
            Triple("nl", "Dutch", listOf("nl", "nld", "dut")),
            Triple("uk", "Ukrainian", listOf("uk", "ukr")),
        )
        .flatMap { (code, name, aliases) ->
            val language = CoverLanguage(code, name, name.lowercase(Locale.ROOT))
            (aliases + language.slug).map { it to language }
        }
        .toMap()

fun coverLanguage(value: String): CoverLanguage? =
    coverLanguages[value.trim().lowercase(Locale.ROOT)]

/** Only explicit language metadata is eligible; title text and original-language hints are not. */
fun Manga.coverLanguages(): List<CoverLanguage> =
    try {
        JSONArray(tagsJson)
            .objects()
            .filter { it.optString("type") == "language" }
            .mapNotNull { tag ->
                coverLanguage(tag.optString("id").trim('/').substringAfterLast('/'))
                    ?: coverLanguage(tag.optString("name"))
            }
            .distinctBy { it.code }
    } catch (_: JSONException) {
        emptyList()
    }

internal fun coverLanguageTags(languages: List<CoverLanguage>): String =
    JSONArray()
        .apply {
            languages
                .distinctBy { it.code }
                .forEach {
                    put(
                        JSONObject()
                            .put("id", "/language/${it.slug}/")
                            .put("name", it.name)
                            .put("type", "language")
                    )
                }
        }
        .toString()
