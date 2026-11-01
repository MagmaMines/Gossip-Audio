package xyz.beyoneer.gossipaudio.ui.theme

import android.content.Context
import android.content.res.Configuration
import xyz.beyoneer.gossipaudio.GossipApp

/**
 * Brand tokens. Yellow + black + white, exactly as the logo demands.
 * Light mode is warm paper-white with ink text; dark mode is system black with
 * the same yellow so the brand never changes identity.
 */
data class Palette(
    val dark: Boolean,
    val bg: Int,
    val bgAlt: Int,
    val card: Int,
    val cardAlt: Int,
    val text: Int,
    val textDim: Int,
    val outline: Int,
    val yellow: Int,
    val yellowBright: Int,
    val yellowDeep: Int,
    val yellowSoft: Int,
    val onYellow: Int,
    val danger: Int,
    val success: Int,
    val shadow: Int
) {
    companion object {
        val YELLOW = 0xFFF5C518.toInt()
        val YELLOW_BRIGHT = 0xFFFFD84D.toInt()
        val YELLOW_DEEP = 0xFF8A6A00.toInt()
        val INK = 0xFF0B0B0C.toInt()

        val LIGHT = Palette(
            dark = false,
            bg = 0xFFFFFDF5.toInt(),
            bgAlt = 0xFFFFF8E1.toInt(),
            card = 0xFFFFFFFF.toInt(),
            cardAlt = 0xFFFFF7D6.toInt(),
            text = 0xFF0B0B0C.toInt(),
            textDim = 0xFF6E6A5E.toInt(),
            outline = 0xFFE4D9B4.toInt(),
            yellow = YELLOW,
            yellowBright = YELLOW_BRIGHT,
            yellowDeep = YELLOW_DEEP,
            yellowSoft = 0xFFFFF2BF.toInt(),
            onYellow = 0xFF0B0B0C.toInt(),
            danger = 0xFFC62828.toInt(),
            success = 0xFF2E7D32.toInt(),
            shadow = 0x14000000
        )

        /**
         * The house look: true black canvas, surfaces one step above it, one loud yellow.
         *
         * The old dark palette was a warm grey (#17171A) on near-black with a dull olive
         * outline, which read as "unfinished". Deep neutral surfaces make the yellow the
         * only colour in the room, which is the whole brand.
         */
        val DARK = Palette(
            dark = true,
            bg = 0xFF070708.toInt(),
            bgAlt = 0xFF0E0E10.toInt(),
            card = 0xFF141416.toInt(),
            cardAlt = 0xFF1B1B1E.toInt(),
            text = 0xFFF7F7F4.toInt(),
            textDim = 0xFF9D9D96.toInt(),
            outline = 0xFF26262A.toInt(),
            yellow = YELLOW,
            yellowBright = YELLOW_BRIGHT,
            yellowDeep = 0xFFB08A00.toInt(),
            yellowSoft = 0xFF2A2410.toInt(),
            onYellow = 0xFF0B0B0C.toInt(),
            danger = 0xFFFF6B6B.toInt(),
            success = 0xFF9BE15D.toInt(),
            shadow = 0x99000000
        )
    }
}

object ThemeController {

    fun isDark(context: Context): Boolean {
        val app = context.applicationContext as? GossipApp
        // Dark is the house style: black canvas, yellow buttons, white text. "system"
        // is still respected for anyone who had it before 2.0.
        return when (app?.prefs?.themeMode ?: "dark") {
            "light" -> false
            "dark" -> true
            else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
    }

    fun palette(context: Context): Palette = if (isDark(context)) Palette.DARK else Palette.LIGHT

    fun toggle(context: Context) {
        val app = context.applicationContext as? GossipApp ?: return
        val dark = isDark(context)
        app.prefs.themeMode = if (dark) "light" else "dark"
    }
}
