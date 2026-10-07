package app.opensefer.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NavigatorTest {

    @Test
    fun goToAndBack_moveThroughTheStack() {
        val nav = Navigator()
        nav.goTo(Destination.Search)
        nav.goTo(Destination.Book("Genesis", "בראשית"))

        assertEquals(Destination.Book("Genesis", "בראשית"), nav.current.destination)
        assertTrue(nav.back())
        assertEquals(Destination.Search, nav.current.destination)
        assertTrue(nav.back())
        assertFalse(nav.back()) // never pops the root
        assertEquals(Destination.Library, nav.current.destination)
    }

    @Test
    fun everyEntry_hasItsOwnIdentity_evenForTheSameDestination() {
        val nav = Navigator()
        nav.goTo(Destination.Reader("Genesis", "בראשית"))
        val first = nav.current
        nav.back()
        nav.goTo(Destination.Reader("Genesis", "בראשית"))

        assertNotEquals(first.id, nav.current.id) // a fresh entry → a fresh ViewModel, fresh start position
        assertTrue(first.viewModelStore !== nav.current.viewModelStore)
    }

    @Test
    fun backToRoot_returnsToTheLibrary() {
        val nav = Navigator()
        nav.goTo(Destination.Search)
        nav.goTo(Destination.Book("Genesis", "בראשית"))
        nav.goTo(Destination.Reader("Genesis", "בראשית", "Genesis.3", 4))

        nav.backToRoot()

        assertEquals(listOf<Destination>(Destination.Library), nav.backStack.map { it.destination })
    }

    @Test
    fun theBackStack_survivesSaveAndRestore_withItsIds() {
        val nav = Navigator()
        nav.goTo(Destination.Search)
        nav.goTo(Destination.Book("Mishneh Torah, Repentance", "משנה תורה, הלכות תשובה"))
        nav.goTo(Destination.Reader("Mishneh Torah, Repentance", "משנה תורה, הלכות תשובה", null, 0))
        nav.goTo(Destination.AboutBook("Berakhot", "ברכות"))
        nav.goTo(Destination.Reader("Berakhot", "ברכות", "Berakhot.2a", 3))
        nav.goTo(Destination.About)

        val restored = Navigator.restore(nav.saveable())

        assertEquals(nav.backStack.map { it.destination }, restored.backStack.map { it.destination })
        assertEquals(nav.backStack.map { it.id }, restored.backStack.map { it.id })
        restored.goTo(Destination.Search)
        assertTrue(restored.backStack.map { it.id }.toSet().size == restored.backStack.size) // ids stay unique
    }

    @Test
    fun aCorruptSavedStack_fallsBackToTheLibrary() {
        assertEquals(Destination.Library, Navigator.restore(listOf("garbage")).current.destination)
    }
}
