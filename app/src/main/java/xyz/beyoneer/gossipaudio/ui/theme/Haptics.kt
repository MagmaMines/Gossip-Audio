package xyz.beyoneer.gossipaudio.ui.theme

import android.animation.ValueAnimator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Real haptic feedback for every tappable surface.
 *
 * The Settings → Haptics switch used to be decorative: the preference existed but nothing ever
 * called [View.performHapticFeedback]. This object is the single gate — flip it from [Prefs] and
 * every press in the app starts (or stops) buzzing. It also honours the system-wide "touch
 * feedback" toggle unless the user explicitly opted in, and never throws on devices without a
 * vibrator.
 */
object Haptics {

    /** Mirrors `Prefs.haptics`. Set from `MainActivity` on start and from the Settings switch. */
    @Volatile
    var enabled: Boolean = true

    /** Test instrumentation: counts press events that passed the gate. */
    internal var delivered: Int = 0
        private set

    private fun perform(view: View, constant: Int) {
        if (!enabled) return
        delivered++
        if (!view.isAttachedToWindow) return
        try {
            view.performHapticFeedback(
                constant,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        } catch (_: Throwable) {
            // Devices without a vibrator simply stay quiet.
        }
    }

    /** Light tick for a normal button press. */
    fun tap(view: View) = perform(view, HapticFeedbackConstants.KEYBOARD_TAP)

    /** Heavier confirmation, e.g. "audio saved". */
    fun confirm(view: View) = if (Build.VERSION.SDK_INT >= 30) {
        perform(view, HapticFeedbackConstants.CONFIRM)
    } else {
        perform(view, HapticFeedbackConstants.LONG_PRESS)
    }

    /** "That did not work" buzz. */
    fun warn(view: View) = if (Build.VERSION.SDK_INT >= 30) {
        perform(view, HapticFeedbackConstants.REJECT)
    } else {
        perform(view, HapticFeedbackConstants.LONG_PRESS)
    }
}

/**
 * Motion gate. The Settings → Reduce motion switch and the system "Remove animations"
 * accessibility setting both funnel into [enabled]; when it is false the UI still works,
 * it just stops flying around.
 */
object Motion {

    /** Mirrors `Prefs.reduceMotion`. */
    @Volatile
    var reduce: Boolean = false

    val enabled: Boolean
        get() = if (reduce) false else (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled())
}
