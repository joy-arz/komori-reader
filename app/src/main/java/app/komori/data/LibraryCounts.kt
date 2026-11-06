package app.komori.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Page/scroll changes never rebuild the chapter index or recalculate counts. */
data class LibraryChapterKey(val mangaId: String, val language: String, val chapterKey: String)

internal fun libraryLanguageCounts(
    chapters: Flow<List<LibraryChapterKey>>,
    completed: Flow<List<String>>,
): Flow<List<LibraryLanguageCount>> = combine(
    chapters.distinctUntilChanged().map { rows -> rows.groupBy { it.mangaId to it.language } },
    completed.distinctUntilChanged().map { it.toHashSet() },
) { groups, done ->
    groups.map { (owner, rows) ->
        LibraryLanguageCount(owner.first, owner.second, rows.size, rows.count { it.chapterKey !in done })
    }
}.distinctUntilChanged().flowOn(Dispatchers.Default)
