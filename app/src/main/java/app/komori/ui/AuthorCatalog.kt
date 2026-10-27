package app.komori.ui

import app.komori.data.*
import java.util.Locale
import org.json.JSONArray
import kotlinx.coroutines.*

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
    private val filter: AuthorWorkFilter = AuthorWorkFilter(),
    private val enrich: (suspend (Manga) -> Manga)? = null,
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
    private val incomplete = linkedMapOf<String, Manga>()
    private val resolved = mutableSetOf<String>()
    private val waitingOwners = mutableMapOf<String, MutableSet<Creator>>()
    val incompleteMetadataCount get() = incomplete.size

    private suspend fun metadata(batch: List<Manga>): Map<String, Manga?> = coroutineScope {
        val values = linkedMapOf<String, Manga?>()
        for (chunk in batch.distinctBy { it.id }.chunked(2)) {
            val results = chunk.map { title -> async {
                title.id to try {
                    val detailed = enrich?.invoke(title) ?: title
                    require(detailed.id == title.id) { "Details belong to a different title." }
                    detailed.metadata(); JSONArray(detailed.creatorsJson).objects()
                    detailed
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { null }
            } }.awaitAll()
            currentCoroutineContext().ensureActive()
            values.putAll(results)
        }
        values
    }

    suspend fun retryMetadata() {
        val result = metadata(incomplete.values.toList())
        currentCoroutineContext().ensureActive()
        result.forEach { (id, title) -> if (title != null) acceptMetadata(id, title) }
    }

    private fun acceptMetadata(id: String, title: Manga) {
        val pending = waitingOwners[id]
        if (pending != null) {
            val known = JSONArray(title.creatorsJson).objects().map { it.optString("id") }.filter { it.isNotBlank() }.toSet()
            pending.filter { it.id in known }.forEach { owners.getOrPut(id) { mutableSetOf() }.add(it) }
            if (known.isNotEmpty()) waitingOwners.remove(id)
        }
        if (owners[id].orEmpty().isNotEmpty()) { titles[id] = title; resolved += id }
        if (id !in waitingOwners) incomplete.remove(id)
    }

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
            return titles.values.filter { (!filter.needsMetadata || it.id in resolved) && filter.matches(it, owners[it.id].orEmpty()) }.sortedWith(ordering)
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
                val details = if (filter.needsMetadata) metadata(result.titles.filter { it.id !in resolved && it.id !in incomplete }) else emptyMap()
                currentCoroutineContext().ensureActive()
                val fresh = result.paginationIds.any { it !in cursor.paginationIds }
                cursor.paginationIds.addAll(result.paginationIds)
                result.orderingIds.distinct().forEach { id ->
                    if (cursor.seen.add(id)) ranks[id] = minOf(ranks[id] ?: Int.MAX_VALUE, cursor.rank++)
                }
                result.titles.forEach {
                    owners.getOrPut(it.id) { mutableSetOf() }.add(cursor.creator)
                    if (filter.needsMetadata) {
                        val detail = details[it.id]
                        if (detail != null) acceptMetadata(it.id, detail)
                        else if (it.id !in resolved) { titles[it.id] = it; incomplete.putIfAbsent(it.id, it) }
                    } else if (it.id !in resolved) titles[it.id] = it
                }
                result.incompleteCreatorTitles.forEach { candidate ->
                    waitingOwners.getOrPut(candidate.id) { mutableSetOf() }.add(cursor.creator)
                    incomplete.putIfAbsent(candidate.id, candidate)
                    if (candidate.id in resolved) acceptMetadata(candidate.id, titles.getValue(candidate.id))
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
    val filter: AuthorWorkFilter = AuthorWorkFilter(),
    val incompleteMetadata: Int = 0,
)
