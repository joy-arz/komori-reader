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
) {
    val label: String
        get() =
            listOfNotNull(volume?.let { "Vol. $it" }, number?.let { "Ch. $it" })
                .joinToString(" · ")
                .ifEmpty { title.ifEmpty { "Unnumbered chapter" } }

    // Unknown numbering stays separate: collapsing it could hide real content.
    val key: String
        get() =
            "$mangaId|$language|${canonical(volume)}|${number?.let(::canonical) ?: "release:$id"}"
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
)

object ChapterPolicy {
    fun select(
        releases: List<Release>,
        prefs: ChapterPreferences,
        pinnedRelease: String? = null,
    ): List<Release> {
        val eligible =
            releases.filter {
                it.language == prefs.language &&
                    (!prefs.strict ||
                        prefs.preferredGroup.isEmpty() ||
                        prefs.preferredGroup in it.groups)
            }
        val chosen =
            if (!prefs.smart) eligible
            else
                eligible
                    .groupBy { it.key }
                    .values
                    .map { alternatives ->
                        alternatives.minWith(
                            compareBy<Release>(
                                { if (it.id == pinnedRelease) 0 else 1 },
                                {
                                    if (
                                        prefs.preferredGroup.isNotEmpty() &&
                                            prefs.preferredGroup in it.groups
                                    )
                                        0
                                    else 1
                                },
                                { if (it.externalUrl == null && it.pages > 0) 0 else 1 },
                                { it.id },
                            )
                        )
                    }
        return chosen.sortedWith { a, b ->
            compareValues(a.volume?.toBigDecimalOrNull(), b.volume?.toBigDecimalOrNull()).takeIf {
                it != 0
            }
                ?: compareValues(a.number?.toBigDecimalOrNull(), b.number?.toBigDecimalOrNull())
                    .takeIf { it != 0 }
                ?: a.id.compareTo(b.id)
        }
    }

    fun start(
        releases: List<Release>,
        currentId: String?,
        completedKeys: Set<String>,
        reread: Boolean,
    ): Release? {
        val current = releases.find { it.id == currentId }
        if (current != null && current.key !in completedKeys) return current
        if (current != null) {
            releases
                .drop(releases.indexOf(current) + 1)
                .firstOrNull { it.key !in completedKeys }
                ?.let {
                    return it
                }
        }
        return releases.firstOrNull { it.key !in completedKeys }
            ?: releases.firstOrNull().takeIf { reread }
    }
}
