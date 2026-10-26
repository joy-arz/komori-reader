package app.komori.domain

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A disk-only lookahead. Resolving and fetching never change reading progress. */
suspend fun prefetchReaderPages(
    chapter: Release,
    page: Int,
    limit: Int,
    nextChapter: (Release) -> Release?,
    resolve: suspend (Release) -> Release,
    fetch: suspend (Release, Int) -> Unit,
) {
    var target = chapter
    var nextPage = page + 1
    repeat(limit.coerceIn(0, 10)) {
        currentCoroutineContext().ensureActive()
        if (nextPage >= target.pages) {
            target = resolve(nextChapter(target) ?: return)
            nextPage = 0
        }
        if (target.pages <= 0) return
        fetch(target, nextPage++)
    }
}
