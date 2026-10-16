package app.komori.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// Only ship the small set of vectors used by the app, not an entire icon pack.
object KomoriIcons {
    private fun icon(name: String, path: String) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(
                PathParser().parsePathString(path).toNodes(),
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd,
            )
            .build()

    val ArrowBack = icon("Back", "M20,11H7.8l5.6,-5.6L12,4l-8,8 8,8 1.4,-1.4L7.8,13H20Z")
    val MenuBook = icon("Book", "M3,4h8v16H3ZM5,6v12h4V6ZM13,4h8v16h-8ZM15,6v12h4V6Z")
    val CollectionsBookmark =
        icon(
            "Library",
            "M3,4h4v16H3ZM9,4h4v16H9ZM15,5l4,-1 4,15 -4,1ZM17.5,6.5l2.8,11 -0.8,0.2 -2.8,-11Z",
        )
    val Explore =
        icon(
            "Discover",
            "M12,2a10,10 0,1 0,0 20a10,10 0,1 0,0 -20ZM12,4a8,8 0,1 1,0 16a8,8 0,1 1,0 -16ZM16,8l-3,5 -5,3 3,-5ZM12,11a1,1 0,1 0,0 2a1,1 0,1 0,0 -2Z",
        )
    val Download =
        icon("Download", "M11,3h2v10l3,-3 1.4,1.4L12,17l-5.4,-5.6L8,10l3,3ZM4,17h2v3h12v-3h2v5H4Z")
    val Tune = icon("Settings", "M3,6h8V4h2v2h8v2h-8v2h-2V8H3ZM3,16h4v-2h2v2h12v2H9v2H7v-2H3Z")
    val PersonOutline =
        icon(
            "Creator",
            "M12,3a4,4 0,1 0,0 8a4,4 0,1 0,0 -8ZM12,5a2,2 0,1 1,0 4a2,2 0,1 1,0 -4ZM4,21v-3c0,-7 16,-7 16,0v3ZM6,19h12v-1c0,-4 -12,-4 -12,0Z",
        )
    val More = icon("More", "M11,4h2v2h-2ZM11,11h2v2h-2ZM11,18h2v2h-2Z")
    val Search =
        icon(
            "Search",
            "M10,3a7,7 0,1 0,0 14c1.6,0 3,-0.5 4.2,-1.4L20,21l1,-1 -5.4,-5.8A7,7 0,0 0,10 3ZM10,5a5,5 0,1 1,0 10a5,5 0,1 1,0 -10Z",
        )
    val PlayArrow = icon("Read", "M8,5v14l11,-7Z")
    val BookmarkAdd = icon("Save", "M5,3h14v18l-7,-4 -7,4ZM7,5v12.5l5,-2.8 5,2.8V5Z")
    val BookmarkAdded = icon("Saved", "M5,3h14v18l-7,-4 -7,4ZM8,9l-1,1 4,4 6,-6 -1,-1 -5,5Z")
    val FavoriteBorder =
        icon(
            "Like",
            "M12,21L3,12C-3,5 6,-2 12,5C18,-2 27,5 21,12ZM12,18l7.5,-7.5C24,5 17,1 12,8C7,1 0,5 4.5,10.5Z",
        )
    val FilterList = icon("Filter", "M3,5h18v2H3ZM6,11h12v2H6ZM9,17h6v2H9Z")
    val CheckCircle =
        icon(
            "Read",
            "M12,2a10,10 0,1 0,0 20a10,10 0,1 0,0 -20ZM12,4a8,8 0,1 1,0 16a8,8 0,1 1,0 -16ZM6,12l1.4,-1.4 3,3 6,-6L18,9l-7.6,7.4Z",
        )
    val OfflinePin = CheckCircle
}
