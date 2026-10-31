package xyz.beyoneer.gossipaudio

import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import xyz.beyoneer.gossipaudio.ui.MainActivity
import xyz.beyoneer.gossipaudio.ui.screens.AboutScreen
import xyz.beyoneer.gossipaudio.ui.screens.ShellScreen
import xyz.beyoneer.gossipaudio.ui.theme.Haptics
import xyz.beyoneer.gossipaudio.ui.theme.Ui
import xyz.beyoneer.gossipaudio.ui.widgets.NeonSlider
import xyz.beyoneer.gossipaudio.ui.widgets.ToggleSwitch
import java.time.Duration

/**
 * The commercial-quality gate that screenshots cannot provide:
 *
 *  1. every tappable control either says something or has a spoken name (TalkBack),
 *  2. custom switches and sliders advertise the right accessibility role,
 *  3. the Haptics preference really gates the vibration calls,
 *  4. back pops the in-app stack instead of instantly killing the activity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AccessibilityTest {

    private fun boot(): MainActivity {
        val app = ApplicationProvider.getApplicationContext<GossipApp>()
        app.prefs.onboardingDone = true
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    @Test
    fun everyTappableControlHasAName() {
        val activity = boot()
        val shell = activity.router.current() as ShellScreen
        val problems = ArrayList<String>()

        for (index in 0..3) {
            shell.selectTab(index, animate = false)
            shadowOf(Looper.getMainLooper()).idle()
            audit(activity.window.decorView, "tab$index", problems)
        }

        assertTrue(
            "Unlabelled tappable controls (TalkBack would read nothing useful):\n" +
                problems.joinToString("\n"),
            problems.isEmpty()
        )
    }

    @Test
    fun switchesAndSlidersAdvertiseTheirAccessibilityRole() {
        val activity = boot()
        val shell = activity.router.current() as ShellScreen
        shell.selectTab(3, animate = false)
        shadowOf(Looper.getMainLooper()).idle()

        val switches = ArrayList<ToggleSwitch>()
        val sliders = ArrayList<NeonSlider>()
        collect(activity.window.decorView, switches, sliders)

        assertTrue("settings screen should contain switches", switches.size >= 5)
        assertTrue("settings screen should contain sliders", sliders.isNotEmpty())

        switches.forEach { toggle ->
            val info = AccessibilityNodeInfo.obtain()
            toggle.onInitializeAccessibilityNodeInfo(info)
            assertEquals("android.widget.Switch", info.className)
            assertTrue("switch must be checkable", info.isCheckable)
            assertTrue("switch needs a spoken name", !toggle.contentDescription.isNullOrBlank())
        }

        sliders.forEach { slider ->
            val info = AccessibilityNodeInfo.obtain()
            slider.onInitializeAccessibilityNodeInfo(info)
            assertEquals("android.widget.SeekBar", info.className)
            assertTrue("slider must expose a value range", info.rangeInfo != null)
        }

        // TalkBack must be able to change a slider without touching the screen.
        val slider = sliders.first()
        val before = slider.value
        val info = AccessibilityNodeInfo.obtain()
        slider.onInitializeAccessibilityNodeInfo(info)
        assertTrue(info.rangeInfo.max > before)
        slider.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null)
        assertTrue("scroll forward should increase the slider", slider.value > before)
    }

    @Test
    fun hapticsSwitchActuallyGatesVibration() {
        val activity = boot()
        val probe = View(activity)
        (activity.window.decorView as ViewGroup).addView(probe)
        Ui.pressable(probe)

        fun press() {
            val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 4f, 4f, 0)
            probe.dispatchTouchEvent(down)
            down.recycle()
        }

        Haptics.enabled = true
        val enabledCount = Haptics.delivered
        press()
        assertEquals("haptics on must deliver one tick", enabledCount + 1, Haptics.delivered)

        Haptics.enabled = false
        press()
        assertEquals("haptics off must stay silent", enabledCount + 1, Haptics.delivered)
        Haptics.enabled = true
        activity.finish()
    }

    @Test
    fun backPopsTheScreenStackBeforeLeavingTheApp() {
        val activity = boot()
        activity.router.push(AboutScreen(activity), animate = false)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.router.current() is AboutScreen)

        activity.onBackPressed()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("back should return to the shell", activity.router.current() is ShellScreen)
        assertFalse("app must stay open after popping a screen", activity.isFinishing)

        activity.onBackPressed()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("back from the root screen should finish the activity", activity.isFinishing)
    }

    // ---------------------------------------------------------------- helpers

    private fun audit(
        view: View,
        where: String,
        out: MutableList<String>,
        parentVisible: Boolean = true
    ) {
        val visible = parentVisible && view.visibility == View.VISIBLE
        val skip = view.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO ||
            view.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        if (!skip && visible && view.isClickable && !hasLabel(view)) {
            out.add("$where: ${describe(view)}")
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) audit(view.getChildAt(i), where, out, visible)
        }
    }

    private fun hasLabel(view: View): Boolean {
        if (!view.contentDescription.isNullOrBlank()) return true
        if (view is TextView && !view.text.isNullOrBlank()) return true
        if (view is ToggleSwitch && !view.contentDescription.isNullOrBlank()) return true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) if (hasLabel(view.getChildAt(i))) return true
        }
        return false
    }

    private fun describe(view: View): String {
        val text = (view as? TextView)?.text?.toString()?.take(28)
        return "${view.javaClass.simpleName} text=$text description=${view.contentDescription}"
    }

    private fun collect(
        view: View,
        switches: MutableList<ToggleSwitch>,
        sliders: MutableList<NeonSlider>
    ) {
        when (view) {
            is ToggleSwitch -> switches.add(view)
            is NeonSlider -> sliders.add(view)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) collect(view.getChildAt(i), switches, sliders)
        }
    }
}
