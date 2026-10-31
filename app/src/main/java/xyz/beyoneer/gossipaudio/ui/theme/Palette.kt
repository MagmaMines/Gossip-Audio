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

        val DARK = Palette(
            dark = true,
            bg = INK,
            bgAlt = 0xFF121214.toInt(),
            card = 0xFF17171A.toInt(),
            cardAlt = 0xFF1F1D14.toInt(),
            text = 0xFFFFFFFF.toInt(),
            textDim = 0xFF9A968A.toInt(),
            outline = 0xFF2C2A22.toInt(),
            yellow = YELLOW,
            yellowBright = YELLOW_BRIGHT,
            yellowDeep = YELLOW_DEEP,
            yellowSoft = 0xFF3A3416.toInt(),
            onYellow = 0xFF0B0B0C.toInt(),
            danger = 0xFFFF6B6B.toInt(),
            success = 0xFF9BE15D.toInt(),
            shadow = 0x66000000
        )
    }
}

object ThemeController {

    fun isDark(context: Context): Boolean {
        val app = context.applicationContext as? GossipApp
        return when (app?.prefs?.themeMode ?: "system") {
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
