package app.komori.domain

/** The middle third on both axes, measured against the viewport, not an image strip. */
object ReaderTapZones {
    fun isControlTap(x: Float, y: Float): Boolean =
        x in (1f / 3f)..(2f / 3f) && y in (1f / 3f)..(2f / 3f)
}
