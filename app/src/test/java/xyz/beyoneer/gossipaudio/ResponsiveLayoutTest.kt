package xyz.beyoneer.gossipaudio

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xyz.beyoneer.gossipaudio.ui.MainActivity
import xyz.beyoneer.gossipaudio.ui.screens.ShellScreen
import org.robolectric.annotation.Config as RoboConfig
import java.io.File
import java.time.Duration

/**
 * "Does it actually fit?" — the test screenshots alone cannot answer that.
 *
 * It renders the real shell at three widths (a tiny 320 dp phone, a Pixel-class
 * 411 dp phone and an 800 dp tablet), then walks the measured view tree and fails
 * if any laid-out child sticks out of its parent. Horizontal scrollers are exempt
 * (their content is *supposed* to be wider than the screen).
 *
 * On tablets it also checks the responsive width cap: the reading column must stay
 * at or below ~680 dp instead of stretching a sentence across 800 dp.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResponsiveLayoutTest {

    @Test
    @RoboConfig(sdk = [34], qualifiers = "w320dp-h640dp-mdpi")
    fun tinyPhoneFitsEveryTab() = renderAllTabs(expectCapped = false)

    @Test
    @RoboConfig(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun pixelPhoneFitsEveryTab() = renderAllTabs(expectCapped = false)

    @Test
    @RoboConfig(sdk = [34], qualifiers = "w800dp-h1280dp-xhdpi")
    fun tabletFitsEveryTabAndCapsTheReadingColumn() = renderAllTabs(expectCapped = true)

    @Test
    @RoboConfig(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", fontScale = 1.3f)
    fun largeFontsStillFit() = renderAllTabs(expectCapped = false)

    private fun renderAllTabs(expectCapped: Boolean) {
        val app = ApplicationProvider.getApplicationContext<GossipApp>()
        app.prefs.onboardingDone = true
        app.prefs.reduceMotion = false

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val looper = shadowOf(Looper.getMainLooper())
        looper.idleFor(Duration.ofSeconds(4))
        looper.idle()

        val root = activity.window.decorView
        val metrics = activity.resources.displayMetrics
        layoutAt(root, metrics.widthPixels, metrics.heightPixels)

        val shell = activity.router.current() as? ShellScreen
        assertNotNull("shell did not boot", shell)

        val layoutReport = StringBuilder()
        var tabOverflow = false
        for (index in 0..3) {
            shell!!.selectTab(index, animate = false)
            looper.idle()
            layoutAt(root, metrics.widthPixels, metrics.heightPixels)
            val problems = ArrayList<String>()
            audit(root, problems)
            if (problems.isNotEmpty()) {
                tabOverflow = true
                val report = "TAB $index overflow @${metrics.widthPixels}px:\n  " +
                    problems.distinct().take(14).joinToString("\n  ")
                println(report)
                layoutReport.append(report).append('\n')
            }
            assertTrue("tab $index renders nothing", root.width > 100 && root.height > 100)
        }
        File("build/test-samples").mkdirs()
        File("build/test-samples/layout.txt").writeText(
            "device=${metrics.widthPixels}x${metrics.heightPixels}px density=${metrics.density}\n" +
                layoutReport + "\n"
        )
        assertTrue(
            "a control sticks out of its parent:\n" + layoutReport.toString().take(900),
            !tabOverflow
        )

        if (expectCapped) {
            val column = findReadingColumn(root)
            assertNotNull("no reading column found", column)
            val contentPx = column!!.width - column.paddingLeft - column.paddingRight
            val contentDp = contentPx / metrics.density
            println("tablet content width: ${contentDp}dp at density ${metrics.density}")
            assertTrue("reading column should be capped on tablets, was ${contentDp}dp", contentDp <= 700f)
            assertTrue("tablet column should still be reasonably wide, was ${contentDp}dp", contentDp >= 500f)
        }
    }

    private fun layoutAt(view: View, width: Int, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
    }

    private fun audit(parent: View, problems: MutableList<String>, path: String = "root") {
        if (parent !is ViewGroup) return
        // A horizontal scroller's child is allowed to be wider than the screen.
        if (parent is HorizontalScrollView) return
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.visibility == View.GONE) continue
            if (child.width > 0 && (child.right > parent.width + 2 || child.left < -2)) {
                problems.add(
                    "$path/${child.javaClass.simpleName}[$i] " +
                        "${child.left}..${child.right} in ${parent.width}px wide ${parent.javaClass.simpleName}"
                )
            }
            audit(child, problems, "$path/${parent.javaClass.simpleName}")
        }
    }

    private fun findReadingColumn(root: View): View? {
        if (root is ScrollView && root.childCount > 0) {
            val child = root.getChildAt(0)
            if (child is ViewGroup && child.paddingLeft > 0) return child
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val hit = findReadingColumn(root.getChildAt(i))
                if (hit != null) return hit
            }
        }
        return null
    }
}
