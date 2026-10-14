package app.komori.domain

import java.math.BigDecimal

data class Release(
    val id: String,
    val mangaId: String,
    val number: String?,
    val volume: String?,
    val title: String,
    val language: String,
    val groups: List<String>,
    val groupNames: String,
    val pages: Int,
    val externalUrl: String? = null,
    val unavailable: Boolean = false,
    val local: Boolean = false,
) {
    val readable: Boolean
        get() = local || (!unavailable && externalUrl == null && pages > 0)

    val label: String
        get() =
            listOfNotNull(volume?.let { "Vol. $it" }, number?.let { "Ch. $it" })
                .joinToString(" · ")
                .ifEmpty { title.ifEmpty { "Unnumbered chapter" } }

    // Unknown numbering stays separate: collapsing it could hide real content.
    val key: String
        get() =
            "$mangaId|$language|${canonical(volume)}|${number?.takeIf { it.toBigDecimalOrNull() != null }?.let(::canonical) ?: "release:$id"}"
}

fun canonical(value: String?): String =
    value?.let {
        runCatching { BigDecimal(it).stripTrailingZeros().toPlainString() }.getOrDefault(it.trim())
    } ?: "?"

data class ChapterPreferences(
    val language: String = "en",
    val smart: Boolean = true,
    val preferredGroup: String = "",
    val strict: Boolean = false,
    val showUnavailable: Boolean = true,
)

object ChapterPolicy {
    fun select(
        releases: List<Release>,
        prefs: ChapterPreferences,
        pinnedRelease: String? = null,
    ): List<Release> {
        val eligible =
            releases.filter {
                (prefs.language == "*" || it.language == prefs.language) &&
                    (prefs.showUnavailable || !it.unavailable || it.local) &&
                    (!prefs.strict ||
                        prefs.preferredGroup.isEmpty() ||
                        prefs.preferredGroup in it.groups)
            }
        val currentGroup = releases.find { it.id == pinnedRelease }?.groups.orEmpty()
        val chosen =
            if (!prefs.smart) eligible
            else
                eligible
                    .groupBy { it.key }
                    .values
                    .map { alternatives ->
                        alternatives.minWith(
                            compareBy<Release>(
                                {
                                    if (it.readable) 0
                                    else if (!it.unavailable && it.externalUrl != null) 1 else 2
                                },
                                { if (it.id == pinnedRelease) 0 else 1 },
                                {
                                    if (
                                        prefs.preferredGroup.isNotEmpty() &&
                                            prefs.preferredGroup in it.groups
                                    )
                                        0
                                    else 1
                                },
                                { if (it.groups.any { group -> group in currentGroup }) 0 else 1 },
                                { it.id },
                            )
                        )
                    }
        return readingOrder(chosen)
    }

    // Chapter number is primary: newer unassigned-volume releases must not jump
    // ahead of chapter 1. Unknown labels stay visible after numbered chapters.
    fun readingOrder(releases: List<Release>): List<Release> =
        releases.sortedWith(
            compareBy<Release> { it.number?.toBigDecimalOrNull() == null }
                .thenBy { it.number?.toBigDecimalOrNull() }
                .thenBy { it.volume?.toBigDecimalOrNull() == null }
                .thenBy { it.volume?.toBigDecimalOrNull() }
                .thenBy { it.id }
        )

    fun start(
        releases: List<Release>,
        currentId: String?,
        completedKeys: Set<String>,
        reread: Boolean,
    ): Release? {
        val ordered = readingOrder(releases).filter { !it.unavailable || it.local }
        val current = ordered.find { it.id == currentId }
        if (current != null && current.key !in completedKeys) return current
        if (current != null) {
            ordered
                .drop(ordered.indexOf(current) + 1)
                .firstOrNull { it.key !in completedKeys }
                ?.let {
                    return it
                }
        }
        return ordered.firstOrNull { it.key !in completedKeys }
            ?: ordered.firstOrNull().takeIf { reread }
    }
}

/** A successful source refresh must not orphan completed downloads. */
fun retainDownloadedChapters(
    feed: List<Release>,
    cached: List<Release>,
    downloads: Map<String, Int>,
): List<Release> =
    (feed + cached.filter { it.id in downloads })
        .distinctBy { it.id }
        .map { chapter ->
            downloads[chapter.id]?.let { chapter.copy(local = true, pages = it) } ?: chapter
        }
