package app.opensefer.ui

import app.opensefer.ui.about.formatSize
import app.opensefer.ui.icons.AppIcons
import app.opensefer.ui.library.splitTitle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiPiecesTest {

    @Test
    fun everyIcon_parses() {
        // A malformed path string would crash the first screen that draws the icon.
        AppIcons.all.forEach { icon -> assertTrue(icon.root.size > 0, "empty icon ${icon.name}") }
    }

    @Test
    fun coverTitles_splitIntoSeriesAndTitle() {
        assertEquals("משנה תורה" to "הלכות תשובה", splitTitle("משנה תורה, הלכות תשובה"))
        assertEquals(null to "בראשית", splitTitle("בראשית"))
        assertEquals(null to "שגוי,", splitTitle("שגוי,"))
    }

    @Test
    fun sizes_readNaturally() {
        assertEquals("512 B", formatSize(512))
        assertEquals("12 KB", formatSize(12 * 1024 + 100))
        assertEquals("3.5 MB", formatSize((3.5 * 1024 * 1024).toLong()))
    }
}
