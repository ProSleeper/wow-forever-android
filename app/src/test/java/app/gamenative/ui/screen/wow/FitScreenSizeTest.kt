package app.gamenative.ui.screen.wow

import org.junit.Assert.assertEquals
import org.junit.Test

class FitScreenSizeTest {

    @Test
    fun sixteenByNineWindow_keepsBaseSize() {
        assertEquals("1920x1080", fitScreenSize("1920x1080", 2400, 1350))
    }

    @Test
    fun foldInnerScreen_keepsPixelBudget() {
        assertEquals("1104x834", fitScreenSize("1280x720", 2448, 1848))
    }

    @Test
    fun portraitWindow_usesLandscapeAspect() {
        assertEquals("1104x834", fitScreenSize("1280x720", 1848, 2448))
    }

    @Test
    fun adreno740Base_onFoldInnerScreen() {
        assertEquals("1658x1252", fitScreenSize("1920x1080", 2448, 1848))
    }

    @Test
    fun widePhone_widensAndShortens() {
        assertEquals("2146x966", fitScreenSize("1920x1080", 2400, 1080))
    }

    @Test
    fun unknownWindow_keepsBaseSize() {
        assertEquals("1280x720", fitScreenSize("1280x720", 0, 0))
    }

    @Test
    fun unfoldedAspect_shapesTheGameForTheInnerScreenWhileFolded() {
        // A Fold 7's inner screen is 2184x1968; folded, the window is the 2520x1080 cover screen.
        val unfolded = UnfoldedAspect.aspectOf(2184, 1968)!!
        assertEquals(fitScreenSize("1280x720", 2184, 1968), fitScreenSize("1280x720", unfolded))
        assertEquals("1012x912", fitScreenSize("1280x720", unfolded))
    }

    @Test
    fun aspectOf_isLongSideOverShortSide() {
        assertEquals(2.3333333f, UnfoldedAspect.aspectOf(1080, 2520)!!, 0.0001f)
        assertEquals(null, UnfoldedAspect.aspectOf(0, 1080))
    }
}
