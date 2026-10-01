package app.roadstr.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeHomePresentationTest {
    @Test
    fun `idle map shows collapsed dashboard and bottom bar`() {
        val snapshot = NativeHomePresenter.present(4, NativeHomeInput())

        assertEquals(NativeHomeChromeStatus.Idle, snapshot.status)
        assertTrue(snapshot.visible)
        assertFalse(snapshot.expanded)
        assertNull(snapshot.unreadActivityLabel)
    }

    @Test
    fun `every competing workflow hides home chrome`() {
        val inputs = listOf(
            NativeHomeInput(navigating = true),
            NativeHomeInput(searchVisible = true),
            NativeHomeInput(placeVisible = true),
            NativeHomeInput(plannerVisible = true),
            NativeHomeInput(previewVisible = true),
            NativeHomeInput(alternativesVisible = true),
            NativeHomeInput(transitVisible = true),
            NativeHomeInput(calculating = true),
            NativeHomeInput(hasRoute = true),
        )

        inputs.forEachIndexed { index, input ->
            assertEquals(
                NativeHomeChromeStatus.Hidden,
                NativeHomePresenter.present(index.toLong(), input).status,
            )
        }
    }

    @Test
    fun `favorites are bounded cleaned and deduplicated by stable id`() {
        val snapshot = NativeHomePresenter.present(
            1,
            NativeHomeInput(
                favorites = listOf(
                    NativeHomeFavorite(" a ", " Home\u0000 "),
                    NativeHomeFavorite("a", "Duplicate"),
                    NativeHomeFavorite("", "Missing"),
                    NativeHomeFavorite("b", "Work"),
                    NativeHomeFavorite("c", "One"),
                    NativeHomeFavorite("d", "Two"),
                    NativeHomeFavorite("e", "Three"),
                    NativeHomeFavorite("f", "Not visible"),
                ),
            ),
        )

        assertEquals(5, snapshot.favorites.size)
        assertEquals(listOf("a", "b", "c", "d", "e"), snapshot.favorites.map { it.id })
        assertEquals("Home", snapshot.favorites.first().label)
        assertThrows(UnsupportedOperationException::class.java) {
            (snapshot.favorites as MutableList).clear()
        }
    }

    @Test
    fun `unread badge follows Flutter 99 plus presentation`() {
        assertNull(NativeHomePresenter.present(0, NativeHomeInput(unreadActivityCount = -1)).unreadActivityLabel)
        assertEquals("7", NativeHomePresenter.present(0, NativeHomeInput(unreadActivityCount = 7)).unreadActivityLabel)
        assertEquals("99", NativeHomePresenter.present(0, NativeHomeInput(unreadActivityCount = 99)).unreadActivityLabel)
        assertEquals("99+", NativeHomePresenter.present(0, NativeHomeInput(unreadActivityCount = 150)).unreadActivityLabel)
    }

    @Test
    fun `negative revisions fail closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeHomePresenter.present(-1, NativeHomeInput())
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeHomeSession().replace(-1, NativeHomeInput())
        }
    }

    @Test
    fun `session rejects stale replacement and resets expansion on new input`() {
        val session = NativeHomeSession()

        assertTrue(session.toggleExpanded(0))
        assertTrue(session.state.value.expanded)
        assertFalse(session.replace(0, NativeHomeInput(searchVisible = true)))
        assertTrue(session.replace(1, NativeHomeInput(searchVisible = true)))
        assertEquals(NativeHomeChromeStatus.Hidden, session.state.value.status)
        assertFalse(session.state.value.expanded)
    }

    @Test
    fun `hidden and stale sessions emit no action`() {
        val session = NativeHomeSession()

        assertEquals(NativeHomeAction.Navigate, session.action(0, NativeHomeAction.Navigate))
        assertNull(session.action(1, NativeHomeAction.Navigate))
        assertTrue(session.replace(1, NativeHomeInput(navigating = true)))
        assertNull(session.action(1, NativeHomeAction.Navigate))
        assertFalse(session.toggleExpanded(1))
    }

    @Test
    fun `favorite selection is revision fenced and limited to visible values`() {
        val session = NativeHomeSession(
            NativeHomeInput(favorites = listOf(NativeHomeFavorite("home", "Home"))),
        )

        assertEquals("Home", session.selectFavorite(0, "home")?.label)
        assertNull(session.selectFavorite(1, "home"))
        assertNull(session.selectFavorite(0, "missing"))
    }

    @Test
    fun `back-style collapse consumes only an expanded idle dashboard`() {
        val session = NativeHomeSession()

        assertFalse(session.collapse(0))
        assertTrue(session.toggleExpanded(0))
        assertTrue(session.collapse(0))
        assertFalse(session.collapse(0))
    }
}
