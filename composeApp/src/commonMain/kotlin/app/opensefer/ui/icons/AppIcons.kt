package app.opensefer.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The app's handful of icons, as tiny in‑code vectors (path data from Material Icons, Apache‑2.0).
 * This replaces the multi‑megabyte `material-icons-extended` artifact for the dozen glyphs we use.
 * Icons that point along the reading direction ([Back], [Forward], [Contents]) are auto‑mirrored,
 * so they face the right way in the app's right‑to‑left layout.
 */
object AppIcons {
    val Back by lazy { icon("Back", mirror = true, "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z") }
    val Forward by lazy { icon("Forward", mirror = true, "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z") }
    val ExpandMore by lazy { icon("ExpandMore", mirror = false, "M16.59,8.59L12,13.17 7.41,8.59 6,10l6,6 6,-6z") }
    val Bookmark by lazy {
        icon("Bookmark", mirror = false, "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2z")
    }
    val BookmarkBorder by lazy {
        icon(
            "BookmarkBorder",
            mirror = false,
            "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2zM17,18l-5,-2.18L7,18V5h10v13z",
        )
    }
    val Search by lazy {
        icon(
            "Search",
            mirror = false,
            "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 " +
                "5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14" +
                "C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z",
        )
    }
    val Add by lazy { icon("Add", mirror = false, "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z") }
    val Check by lazy { icon("Check", mirror = false, "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z") }
    val Close by lazy {
        icon(
            "Close",
            mirror = false,
            "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z",
        )
    }
    val Copy by lazy {
        icon(
            "Copy",
            mirror = false,
            "M16,1L4,1c-1.1,0 -2,0.9 -2,2v14h2L4,3h12L16,1zM19,5L8,5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11" +
                "c1.1,0 2,-0.9 2,-2L21,7c0,-1.1 -0.9,-2 -2,-2zM19,21L8,21L8,7h11v14z",
        )
    }
    val Info by lazy {
        icon(
            "Info",
            mirror = false,
            "M11,7h2v2h-2zM11,11h2v6h-2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z" +
                "M12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z",
        )
    }
    val Download by lazy { icon("Download", mirror = false, "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z") }
    val OfflinePin by lazy {
        icon(
            "OfflinePin",
            mirror = false,
            "M12,2C6.5,2 2,6.5 2,12s4.5,10 10,10 10,-4.5 10,-10S17.5,2 12,2zM17,18H7v-2h10v2z" +
                "M10.3,14L7,10.7l1.4,-1.4 1.9,1.9 5.3,-5.3L17,7.3 10.3,14z",
        )
    }
    val LibraryAdd by lazy {
        icon(
            "LibraryAdd",
            mirror = false,
            "M4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6zM20,2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2" +
                "V4c0,-1.1 -0.9,-2 -2,-2zM19,11h-4v4h-2v-4H9V9h4V5h2v4h4v2z",
        )
    }
    val LibraryAdded by lazy {
        icon(
            "LibraryAdded",
            mirror = false,
            "M20,2H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2z" +
                "M12.47,14L9,10.5l1.4,-1.41 2.07,2.08L17.6,6 19,7.41 12.47,14zM4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6z",
        )
    }
    val Delete by lazy {
        icon(
            "Delete",
            mirror = false,
            "M16,9v10H8V9h8m-1.5,-6h-5l-1,1H5v2h14V4h-3.5l-1,-1zM18,7H6v12c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7z",
        )
    }
    val Contents by lazy {
        icon(
            "Contents",
            mirror = true,
            "M3,9h14V7H3v2zM3,13h14v-2H3v2zM3,17h14v-2H3v2zM19,17h2v-2h-2v2zM19,7v2h2V7h-2zM19,13h2v-2h-2v2z",
        )
    }
    val TextSize by lazy { icon("TextSize", mirror = false, "M2.5,4v3h5v12h3V7h5V4H2.5zM21.5,9h-9v3h3v7h3v-7h3V9z") }

    /** Every icon — lets a unit test prove all the path data parses. */
    internal val all: List<ImageVector>
        get() = listOf(
            Back, Forward, ExpandMore, Bookmark, BookmarkBorder, Search, Add, Check, Close, Copy, Info,
            Download, OfflinePin, LibraryAdd, LibraryAdded, Delete, Contents, TextSize,
        )

    private fun icon(name: String, mirror: Boolean, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = mirror,
        ).apply {
            paths.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
        }.build()
}
