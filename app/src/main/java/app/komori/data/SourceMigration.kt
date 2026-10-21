package app.komori.data

import app.komori.domain.Release
import app.komori.domain.canonical

/** Candidate mappings require matching chapter, volume and translation; titles are never keys. */
data class MigrationProgress(val position: Progress, val source: Release?, val target: Release?)

object SourceMigrationPolicy {
    private fun identity(release: Release): String? {
        if (release.number?.toBigDecimalOrNull() == null) return null
        return "${release.language}|${canonical(release.volume)}|${canonical(release.number)}"
    }

    fun propose(source: List<Release>, progress: List<Progress>, target: List<Release>): List<MigrationProgress> {
        val sourceById = source.associateBy { it.id }
        val targets = target.filter { it.readable }.mapNotNull { release -> identity(release)?.let { it to release } }.groupBy({ it.first }, { it.second })
        return progress.groupBy { it.chapterKey }.values.map { positions ->
            val position = positions.maxWith(compareBy<Progress> { it.completed }.thenBy { it.page })
            val chapter = sourceById[position.releaseId]
            val match = chapter?.let(::identity)?.let { targets[it]?.singleOrNull() }
            MigrationProgress(position, chapter, match)
        }.sortedWith(compareBy<MigrationProgress> { it.source?.number?.toBigDecimalOrNull() == null }.thenBy { it.source?.number?.toBigDecimalOrNull() }.thenBy { it.position.releaseId })
    }
}
