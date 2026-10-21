package app.komori.data

import org.json.JSONArray
import org.json.JSONObject

data class SavedSearch(val id: String, val name: String, val navigation: NavigationState)

fun savedSearches(text: String): List<SavedSearch> = try {
    JSONArray(text).objects().take(100).mapNotNull { o ->
        val state = NavigationState.decode(o.optString("navigation")) ?: return@mapNotNull null
        if (state.source !in sourceNames) null else SavedSearch(o.getString("id"), o.getString("name"), state)
    }
} catch (_: org.json.JSONException) { emptyList() }

internal fun savedSearchJson(rows: List<SavedSearch>): String {
    require(rows.size <= 100) { "The saved-search limit is 100." }
    return JSONArray(rows.map { row ->
        require(row.name.isNotBlank() && row.name.length <= 100) { "Search names must contain 1–100 characters." }
        JSONObject().put("id", row.id).put("name", row.name).put("navigation", row.navigation.encode())
    }).toString().also { require(it.length <= 128 * 1024) { "Saved searches exceed the size limit." } }
}
