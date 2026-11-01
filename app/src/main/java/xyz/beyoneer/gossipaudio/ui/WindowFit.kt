package xyz.beyoneer.gossipaudio.ui

/**
 * How much of the window the system bars eat.
 *
 * Android 15 (API 35) **forces** edge-to-edge for any app targeting 35: the window is
 * drawn behind the status bar and the navigation bar whether the app asks for it or not.
 * GossipAudio targeted 35 and never compensated, which is exactly the reported bug —
 * "the app is bigger than the screen, I can't see the bottom icons": the navigation dock
 * was sitting underneath the gesture bar.
 *
 * Kept as a pure function so a JVM test can prove the arithmetic without a device.
 */
object WindowFit {

    data class Padding(val top: Int, val bottom: Int)

    /**
     * @param statusTop height of the status bar (and any top cutout)
     * @param navBottom height of the navigation bar / gesture handle
     * @param imeBottom height of the on-screen keyboard, if it is showing
     */
    fun padding(statusTop: Int, navBottom: Int, imeBottom: Int, cutoutTop: Int = 0, cutoutBottom: Int = 0): Padding {
        val top = maxOf(statusTop, cutoutTop).coerceAtLeast(0)
        // The keyboard sits on top of the navigation bar, never next to it: taking the
        // larger value is what stops the composer being hidden behind the keyboard while
        // also stopping it from jumping up by the height of the nav bar.
        val bottom = maxOf(navBottom, imeBottom, cutoutBottom).coerceAtLeast(0)
        return Padding(top, bottom)
    }
}
