package app.komori.ui

import app.komori.data.*
import org.json.JSONObject

/** Catalog metadata and Library state are distinct; filtering never highlights saved titles. */
data class AuthorWorkFilter(
    val sources: Set<String> = emptySet(),
    val query: String = "",
    val language: String = "",
    val category: String = "",
    val tags: String = "",
    val library: String = "All works",
    val status: String = "",
) {
    val needsMetadata get() = language.isNotBlank() || category.isNotBlank() || tags.isNotBlank()
    val active get() = this != AuthorWorkFilter()
    val nativeLanguage get() = when (coverLanguage(language)?.code) { "jp" -> "ja"; "sp" -> "es"; "kr" -> "ko"; "ch" -> "zh"; else -> language }

    fun matches(title: Manga, owners: Collection<Creator>): Boolean {
        if (sources.isNotEmpty() && sourceKey(title.id) !in sources) return false
        if (query.isNotBlank() && !title.title.contains(query.trim(), true) && owners.none { it.name.contains(query.trim(), true) }) return false
        val metadata = title.metadata()
        if (language.isNotBlank()) {
            val code = coverLanguage(language)?.code ?: language
            if (sourceKey(title.id) != "mangadex" && title.coverLanguages().none { it.code == code }) return false
        }
        if (category.isNotBlank() && metadata.none { it.type == "category" && it.name.contains(category.trim(), true) }) return false
        val requested = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return requested.all { tag -> metadata.any { it.name.contains(tag, true) } }
    }

    fun matchesLibrary(shelf: Shelf?): Boolean = when (library) {
        "Saved" -> shelf?.saved == true
        "Not saved" -> shelf?.saved != true
        "Liked" -> shelf?.liked == true
        else -> true
    } && (status.isBlank() || shelf?.status == status)

    fun encode(): String = JSONObject().put("sources", sources.joinToString(",")).put("query", query).put("language", language)
        .put("category", category).put("tags", tags).put("library", library).put("status", status).toString()

    companion object {
        fun decode(value: String): AuthorWorkFilter = runCatching {
            val o = JSONObject(value)
            AuthorWorkFilter(o.optString("sources").split(',').filter { it in sourceNames }.toSet(), o.optString("query").take(256),
                o.optString("language").take(16), o.optString("category").take(128), o.optString("tags").take(512),
                o.optString("library", "All works"), o.optString("status"))
        }.getOrDefault(AuthorWorkFilter())
    }
}
