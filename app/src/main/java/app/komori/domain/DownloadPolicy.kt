package app.komori.domain

enum class DownloadChoice(
    val label: String,
    val limit: Int = Int.MAX_VALUE,
    val unreadOnly: Boolean = true,
) {
    NEXT_1("Next unread chapter", 1),
    NEXT_5("Next 5 unread", 5),
    NEXT_10("Next 10 unread", 10),
    NEXT_25("Next 25 unread", 25),
    UNREAD("All unread chapters"),
    ALL("All readable chapters", unreadOnly = false),
}

object DownloadPolicy {
    val activeStates = setOf("Queued", "Downloading", "Retrying")

    fun select(
        releases: List<Release>,
        readKeys: Set<String>,
        existing: Map<String, String>,
        choice: DownloadChoice,
    ): List<Release> =
        ChapterPolicy.readingOrder(releases)
            .distinctBy { it.id }
            .filter {
                it.readable &&
                    existing[it.id] !in activeStates + "Complete" &&
                    (!choice.unreadOnly || it.key !in readKeys)
            }
            .take(choice.limit)
}
