package app.komori.domain

/** Stable persisted keys; direction changes navigation, never chapter ordering. */
enum class ReadingMode(val key: String, val label: String) {
    LTR("ltr", "Left to right"),
    RTL("rtl", "Right to left"),
    WEBTOON("webtoon", "Webtoon");

    fun tapDelta(fraction: Float): Int? =
        when {
            fraction < 0.3f -> if (this == RTL) 1 else -1
            fraction > 0.7f -> if (this == RTL) -1 else 1
            else -> null
        }

    fun swipeDelta(distance: Float): Int =
        if (distance == 0f) 0 else if ((distance > 0) == (this == RTL)) 1 else -1

    companion object {
        fun resolve(titleMode: String?, webtoonDefault: Boolean, rtlDefault: Boolean): ReadingMode =
            entries.find { it.key == titleMode }
                ?: if (webtoonDefault) WEBTOON else if (rtlDefault) RTL else LTR
    }
}
