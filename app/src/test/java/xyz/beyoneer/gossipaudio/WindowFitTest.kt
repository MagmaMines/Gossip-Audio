package xyz.beyoneer.gossipaudio

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.beyoneer.gossipaudio.ui.WindowFit

/**
 * The navigation dock used to sit under the gesture bar on Android 15, which is why the
 * bottom icons could not be reached. The fix is arithmetic, so the arithmetic is tested.
 */
class WindowFitTest {

    @Test
    fun edgeToEdgePadsBothBars() {
        val pad = WindowFit.padding(statusTop = 96, navBottom = 132, imeBottom = 0)
        assertEquals(96, pad.top)
        assertEquals(132, pad.bottom)
    }

    @Test
    fun keyboardWinsOverTheNavigationBar() {
        val pad = WindowFit.padding(statusTop = 96, navBottom = 132, imeBottom = 880)
        assertEquals(880, pad.bottom)
    }

    @Test
    fun cutoutCanBeTallerThanTheStatusBar() {
        val pad = WindowFit.padding(statusTop = 80, navBottom = 48, imeBottom = 0, cutoutTop = 140)
        assertEquals(140, pad.top)
    }

    @Test
    fun nothingIsNegative() {
        val pad = WindowFit.padding(statusTop = -1, navBottom = -5, imeBottom = -2)
        assertEquals(0, pad.top)
        assertEquals(0, pad.bottom)
    }
}
