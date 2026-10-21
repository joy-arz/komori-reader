package app.komori.ui

import app.komori.data.*
import java.util.Locale
import kotlinx.coroutines.CancellationException

enum class AuthorSort(val label: String) {
    LATEST("Latest"),
    POPULAR("Popularity / views"),
    ALPHABETICAL("Alphabetical"),
    AUTHOR("Author");

    fun sourceSort(source: String): String =
        when (this) {
            POPULAR ->
                when (source) {
                    "mangadex" -> "Popularity"
                    "nhentai" -> "Popular all time"
                    else -> "Popular"
                }
            else -> if (source == "mangadex") "Latest updates" else "Latest"
        }
}

/** Round-robin paging keeps a prolific creator from hiding everyone else. */
class AuthorCatalog(
    creators: List<Creator>,
    sort: AuthorSort = AuthorSort.LATEST,
    private val fetch: suspend (Creator, Int) -> BrowsePage,
) {
    private data class Cursor(
        val creator: Creator,
        var page: Int = 1,
        var done: Boolean = false,
        val seen: MutableSet<String> = mutableSetOf(),
        var rank: Int = 0,
        val paginationIds: MutableSet<String> = mutableSetOf(),
    )

    var sort: AuthorSort = sort
        private set

    fun reorder(order: AuthorSort) {
        require((sort == AuthorSort.POPULAR) == (order == AuthorSort.POPULAR)) {
            "Changing source ranking requires a new catalog."
        }
        sort = order
    }

    private val cursors = creators.distinctBy { it.id }.map { Cursor(it) }
    private var next = 0
    private val titles = linkedMapOf<String, Manga>()
    private val ranks = mutableMapOf<String, Int>()
    private val owners = mutableMapOf<String, MutableSet<Creator>>()

    private fun owner(id: String) =
        owners[id].orEmpty().minOfOrNull { it.name.lowercase(Locale.ROOT) }.orEmpty()

    val items: List<Manga>
        get() {
            val alphabetical = compareBy<Manga> { it.title.lowercase(Locale.ROOT) }.thenBy { it.id }
            val ordering =
                when (sort) {
                    AuthorSort.ALPHABETICAL -> alphabetical
                    AuthorSort.AUTHOR -> compareBy<Manga> { owner(it.id) }.then(alphabetical)
                    else ->
                        compareBy<Manga> { ranks[it.id] ?: Int.MAX_VALUE }
                            .thenBy { owner(it.id) }
                            .then(alphabetical)
                }
            return titles.values.sortedWith(ordering)
        }

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
                val fresh = result.paginationIds.any { it !in cursor.paginationIds }
                cursor.paginationIds.addAll(result.paginationIds)
                result.titles.forEach {
                    if (cursor.seen.add(it.id)) {
                        ranks[it.id] = minOf(ranks[it.id] ?: Int.MAX_VALUE, cursor.rank++)
                    }
                    owners.getOrPut(it.id) { mutableSetOf() }.add(cursor.creator)
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
    val sort: AuthorSort = AuthorSort.LATEST,
)
