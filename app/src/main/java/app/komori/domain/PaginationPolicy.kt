package app.komori.domain

object PaginationPolicy {
    /** Stop automatic traversal when a source repeats a page or returns no new entries. */
    fun hasNext(sourceHasMore: Boolean, previousCount: Int, mergedCount: Int): Boolean =
        sourceHasMore && mergedCount > previousCount
}
