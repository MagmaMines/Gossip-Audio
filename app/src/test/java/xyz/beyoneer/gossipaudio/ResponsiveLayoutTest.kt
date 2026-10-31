package xyz.beyoneer.gossipaudio

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
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
import java.time.Duration

/**
 * Lays the real UI out at three screen sizes and fails on anything that draws outside
 * its parent.
 *
 * The 320 dp case is the one that matters: it is the narrowest phone Android still
 * supports, the font is at default scale, and every extra chip in a row becomes a
 * genuine clipping bug. Horizontal carousels are the deliberate exception — they are
 * `HorizontalScrollView`s and are expected to scroll.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResponsiveLayoutTest {

    private val report = StringBuilder()

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun tinyPhoneFitsEveryTab() = checkEveryTab(320)

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun pixelPhoneFitsEveryTab() = checkEveryTab(411)

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun tabletFitsEveryTabAndCapsContent() = checkEveryTab(800)

    private fun checkEveryTab(widthDp: Int) {
        val app = ApplicationProvider.getApplicationContext<GossipApp>()
        app.prefs.onboardingDone = true
        app.prefs.reduceMotion = true

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val looper = shadowOf(Looper.getMainLooper())
        looper.idleFor(Duration.ofSeconds(4))
        looper.idle()

        val shell = activity.router.current() as? ShellScreen
        assertNotNull("expected the shell after the splash", shell)
        val root = activity.window.decorView
        val metrics = activity.resources.displayMetrics

        for (tab in 0 until 4) {
            shell!!.selectTab(tab, animate = false)
            looper.idle()
            root.measure(
                View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
            report.append("device=${metrics.widthPixels}x${metrics.heightPixels}px density=${metrics.density}\n")
            val problems = ArrayList<String>()
            audit(root, problems, "tab$tab", 0)
            report.append("tab $tab @${widthDp}dp: ").append(if (problems.isEmpty()) "ok" else problems.take(8)).append('\n')
            assertTrue("tab $tab overflows @${widthDp}dp: ${problems.take(8)}", problems.isEmpty())
        }

        if (widthDp >= 700) {
            val column = findCappedColumn(root)
            assertNotNull("tablet layout should have a width-capped column", column)
            val contentDp = (column!!.width - column.paddingLeft - column.paddingRight) / metrics.density
            report.append("tablet content width: ${contentDp}dp\n")
            assertTrue("content should stay readable on a tablet (capped ~680dp), was $contentDp dp", contentDp <= 690f)
        }

        java.io.File("build/test-samples").mkdirs()
        java.io.File("build/test-samples/layout.txt").writeText(report.toString())
        println(report)
        Unit
    }

    /**
     * Walks the tree looking for a child wider than the space its parent gives it, or one
     * that starts left of its parent. Scroll containers are skipped on the scroll axis.
     */
    private fun audit(view: View, problems: MutableList<String>, path: String, depth: Int) {
        if (depth > 40 || view.visibility != View.VISIBLE) return
        if (view is ViewGroup) {
            val skipX = view is HorizontalScrollView
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child.visibility != View.VISIBLE) continue
                val childPath = "$path/${child.javaClass.simpleName}[$i]"
                if (!skipX && view !is ScrollView) {
                    val available = view.width - view.paddingLeft - view.paddingRight
                    val childWidth = child.right - child.left
                    if (available > 0 && childWidth > available + 1) {
                        problems.add(
                            "$childPath ${childWidth}px in ${available}px (${view.javaClass.simpleName})"
                        )
                    }
                    if (child.left < view.paddingLeft - 1) {
                        problems.add("$childPath starts at ${child.left}px, parent padding ${view.paddingLeft}px")
                    }
                }
                audit(child, problems, childPath, depth + 1)
            }
        }
    }

    private fun findCappedColumn(view: View): LinearLayout? {
        if (view is LinearLayout && view.width > 0) {
            val contentDp = view.width / view.resources.displayMetrics.density
            if (contentDp > 600f && contentDp < 700f) return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findCappedColumn(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }
}
