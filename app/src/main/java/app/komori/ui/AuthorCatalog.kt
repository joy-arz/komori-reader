package app.komori.ui

import app.komori.data.*
import kotlinx.coroutines.CancellationException

/** Round-robin paging keeps a prolific creator from hiding everyone else. */
class AuthorCatalog(
    creators: List<Creator>,
    private val fetch: suspend (Creator, Int) -> BrowsePage,
) {
    private data class Cursor(
        val creator: Creator,
        var page: Int = 1,
        var done: Boolean = false,
        val seen: MutableSet<String> = mutableSetOf(),
    )

    private val cursors = creators.distinctBy { it.id }.map { Cursor(it) }
    private var next = 0
    private val titles = linkedMapOf<String, Manga>()
    val items: List<Manga>
        get() = titles.values.toList()

    val hasMore: Boolean
        get() = cursors.any { !it.done }

    suspend fun loadNext(): List<String> {
        val failures = mutableListOf<String>()
        var attempted = 0
        var visited = 0
        while (attempted < 3 && visited < cursors.size) {
            val cursor = cursors[next % cursors.size]
            next = (next + 1) % cursors.size
            visited++
            if (cursor.done) continue
            attempted++
            try {
                val result = fetch(cursor.creator, cursor.page)
                val fresh = result.titles.any { it.id !in cursor.seen }
                result.titles.forEach {
                    cursor.seen.add(it.id)
                    titles[it.id] = it
                }
                cursor.page++
                cursor.done = !result.hasMore || !fresh
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures += "${cursor.creator.name}: ${connectionMessage(e)}"
            }
        }
        return failures
    }
}

data class AuthorFeed(
    val items: List<Manga> = emptyList(),
    val loading: Boolean = false,
    val hasMore: Boolean = false,
    val errors: List<String> = emptyList(),
    val context: List<String> = emptyList(),
)
