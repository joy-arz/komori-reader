package app.komori.data

import org.json.JSONArray
import org.json.JSONObject

/** Saved identifiers, never result lists, page manifests, images or session credentials. */
data class NavigationState(
    val tab: Int = 0,
    val source: String = "",
    val query: String = "",
    val filter: BrowseFilter = BrowseFilter(),
    val title: String = "",
    val reader: String = "",
    val page: Int = 0,
    val creator: Creator? = null,
) {
    fun encode(): String = JSONObject()
        .put("tab", tab.coerceIn(0, 4)).put("source", source.take(64)).put("query", query.take(512))
        .put("title", title.take(2048)).put("reader", reader.take(2048)).put("page", page.coerceAtLeast(0))
        .put("sort", filter.sort.take(128)).put("searchType", filter.searchType.take(32))
        .put("facet", filter.facetPath.take(2048)).put("language", filter.language.take(64))
        .put("category", filter.category.take(128)).put("tags", JSONArray(filter.tags.take(20)))
        .put("status", filter.status.take(32)).put("ratings", JSONArray(filter.ratings.sorted()))
        .apply { creator?.let { put("creator", JSONObject().put("id", it.id.take(2048)).put("name", it.name.take(512)).put("role", it.role.take(32))) } }
        .toString()

    companion object {
        fun decode(text: String?): NavigationState? {
            if (text == null || text.length > 32768) return null
            return try {
                val o = JSONObject(text)
                val creator = o.optJSONObject("creator")?.let { Creator(it.getString("id"), it.getString("name"), it.getString("role")) }
                val tags = o.optJSONArray("tags") ?: JSONArray()
                NavigationState(o.optInt("tab").coerceIn(0, 4), o.optString("source"), o.optString("query"),
                    BrowseFilter(o.optString("sort"), (0 until minOf(tags.length(), 20)).map { tags.getString(it) }.toSet(),
                        o.optString("searchType", "Title"), o.optString("facet"), o.optString("language"), o.optString("category"), o.optString("status"),
                        o.optJSONArray("ratings")?.let { r -> (0 until minOf(r.length(), 4)).map { r.getString(it) }.toSet() } ?: setOf("safe", "suggestive")),
                    o.optString("title"), o.optString("reader"), o.optInt("page").coerceAtLeast(0), creator)
            } catch (_: org.json.JSONException) { null }
        }
    }
}
