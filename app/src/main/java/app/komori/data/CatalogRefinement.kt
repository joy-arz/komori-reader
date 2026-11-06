package app.komori.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Unknown metadata is retained for manual retry, never presented as a matching title. */
internal suspend fun refineCatalog(
    page: BrowsePage,
    details: suspend (Manga) -> Manga,
    matches: (Manga) -> Boolean,
): BrowsePage = coroutineScope {
    val verified = mutableListOf<Manga>()
    val incomplete = mutableListOf<Manga>()
    for (chunk in page.titles.distinctBy { it.id }.chunked(2)) {
        val rows = chunk.map { title -> async {
            title to try {
                Result.success(details(title).also { require(it.id == title.id) { "Metadata belongs to another title." } }.takeIf(matches))
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { Result.failure<Manga?>(error) }
        } }.awaitAll()
        currentCoroutineContext().ensureActive()
        rows.forEach { (original, detailed) ->
            if (detailed.isFailure) incomplete += original
            else detailed.getOrNull()?.let { verified += it }
        }
    }
    page.copy(titles = verified, incompleteMetadata = incomplete)
}
