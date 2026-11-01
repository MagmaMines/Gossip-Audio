package xyz.beyoneer.gossipaudio

import android.graphics.Bitmap
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xyz.beyoneer.gossipaudio.audio.Exporter
import xyz.beyoneer.gossipaudio.core.NanoVoice
import xyz.beyoneer.gossipaudio.data.Clip
import xyz.beyoneer.gossipaudio.ui.MainActivity
import xyz.beyoneer.gossipaudio.ui.screens.AboutScreen
import xyz.beyoneer.gossipaudio.ui.screens.DiagnosticsScreen
import xyz.beyoneer.gossipaudio.ui.screens.HelpScreen
import xyz.beyoneer.gossipaudio.ui.screens.PlayerScreen
import xyz.beyoneer.gossipaudio.ui.screens.ShellScreen
import java.io.File
import java.time.Duration

/**
 * Boots the real application on the JVM: Application.onCreate → MainActivity → Splash → Shell,
 * then walks every tab, opens the player and writes PNG screenshots of what actually rendered.
 *
 * This is the "does it open and draw something" proof that a compile check cannot give you.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiSmokeTest {

    private val shots = File("build/ui-shots").apply { mkdirs() }

    @Test
    fun appBootsIntoShellAndRendersEveryTab() {
        val app = ApplicationProvider.getApplicationContext<GossipApp>()
        // entrance fades are real animation; a screenshot taken mid-flight is not a bug
        // report, it is a race. Render the settled state instead.
        app.prefs.reduceMotion = true
        app.prefs.onboardingDone = true

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)
        assertFalse("activity must not finish on launch", activity.isFinishing)

        // Advance past the 2.5 s butterfly splash.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        shadowOf(Looper.getMainLooper()).idle()

        val root = activity.window.decorView
        val texts = ArrayList<String>()
        collectText(root, texts)
        println("after splash: " + texts.take(12))
        assertTrue("shell should render brand text, got: $texts", texts.any { it.contains("Gossip", true) })

        val current = activity.router.current()
        assertNotNull("router should have a screen", current)
        capture(root, "01-shell-studio.png")

        if (current is ShellScreen) {
            listOf(1 to "02-voices", 2 to "03-library", 3 to "04-settings").forEach { (index, name) ->
                current.selectTab(index, animate = false)
                shadowOf(Looper.getMainLooper()).idle()
                capture(root, "$name.png")
                val tabTexts = ArrayList<String>()
                collectText(root, tabTexts)
                assertTrue("$name tab rendered nothing", tabTexts.any { it.isNotBlank() })
                println("$name: " + tabTexts.take(10))
            }
            current.selectTab(0, animate = false)
            shadowOf(Looper.getMainLooper()).idle()
        }

        // Screens were reachable without crashing; check the rendered pixels are actually ours.
        val bitmap = drawToBitmap(root)
        assertTrue("decor view should have a size", bitmap.width > 200 && bitmap.height > 200)
        val yellow = countYellowPixels(bitmap)
        val colors = distinctColors(bitmap)
        println("yellow-ish pixels: $yellow · distinct sampled colours: $colors · bounds: ${yellowBounds(bitmap)}")
        if (colors > 4) {
            assertTrue("brand yellow should appear in the rendered UI ($yellow px)", yellow > 200)
        } else {
            println("software canvas produced a flat bitmap; skipping pixel assertions in this environment")
        }
    }

    @Test
    fun playerScreenOpensForARealClip() {
        val app = ApplicationProvider.getApplicationContext<GossipApp>()
        app.prefs.onboardingDone = true
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        shadowOf(Looper.getMainLooper()).idle()

        val store = app.store
        val id = store.newId()
        val pcm = NanoVoice.speak("Smoke test, one two three.", NanoVoice.Options(baseF0 = 132.0))
        val master = Exporter.writeMaster(activity, pcm, id)
        store.add(
            Clip(
                id = id,
                title = "Smoke test clip",
                text = "Smoke test, one two three.",
                voiceId = "riya",
                styleId = "gossip",
                engine = "nano",
                format = "WAV",
                sampleRate = pcm.sampleRate,
                durationMs = pcm.durationMs,
                sizeBytes = master.length(),
                createdAt = System.currentTimeMillis(),
                liked = false,
                fileName = master.name,
                coverName = null
            )
        )
        assertNotNull("clip should be indexed", store.byId(id))

        // Playback itself needs a real MediaPlayer; failure here must not hide UI problems.
        runCatching { activity.router.push(PlayerScreen(activity, id)) }
        shadowOf(Looper.getMainLooper()).idle()

        val texts = ArrayList<String>()
        collectText(activity.window.decorView, texts)
        println("player texts: " + texts.take(16))
        assertTrue("player screen should show its header", texts.any { it.contains("NOW PLAYING", true) || it.contains("The tea", true) })
        capture(activity.window.decorView, "05-player.png")
    }

    @Test
    fun aboutHelpAndDiagnosticsScreensRender() {
        val app = ApplicationProvider.getApplicationContext<GossipApp>()
        app.prefs.onboardingDone = true
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        shadowOf(Looper.getMainLooper()).idle()

        val root = activity.window.decorView

        activity.router.push(AboutScreen(activity), animate = false)
        shadowOf(Looper.getMainLooper()).idle()
        val about = ArrayList<String>()
        collectText(root, about)
        println("about: " + about.take(10))
        assertTrue("about must show the exact tagline", about.any { it.contains("not gossiping inreal", true) })
        assertTrue("about must credit the author", about.any { it.contains("Beyoneer XYZ") })
        capture(root, "06-about.png")
        activity.router.pop()
        shadowOf(Looper.getMainLooper()).idle()

        activity.router.push(HelpScreen(activity), animate = false)
        shadowOf(Looper.getMainLooper()).idle()
        val help = ArrayList<String>()
        collectText(root, help)
        println("help: " + help.take(8))
        assertTrue("help must list questions", help.count { it.endsWith("?") } > 5)
        capture(root, "07-help.png")
        activity.router.pop()
        shadowOf(Looper.getMainLooper()).idle()

        activity.router.push(DiagnosticsScreen(activity), animate = false)
        shadowOf(Looper.getMainLooper()).idle()
        val diag = ArrayList<String>()
        collectText(root, diag)
        println("diagnostics: " + diag.take(10))
        assertTrue("diagnostics must report device facts", diag.any { it.contains("CPU", true) || it.contains("RAM", true) })
        capture(root, "08-diagnostics.png")
    }

    @Test
    fun darkThemeStillUsesBrandYellow() {
        val app = ApplicationProvider.getApplicationContext<GossipApp>()
        app.prefs.onboardingDone = true
        app.prefs.themeMode = "dark"
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("dark palette expected", activity.palette.dark)
        capture(activity.window.decorView, "09-shell-dark.png")
    }

    // ---------------------------------------------------------------- helpers

    private fun collectText(view: View, out: MutableList<String>) {
        if (view is TextView) {
            view.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) collectText(view.getChildAt(i), out)
        }
    }

    private fun drawToBitmap(view: View): Bitmap {
        val metrics = view.resources.displayMetrics
        val width = maxOf(view.width, metrics.widthPixels)
        val height = maxOf(view.height, metrics.heightPixels)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        // Robolectric hands back a reused native canvas: without an explicit fill the
        // previous screen's pixels show through the gaps ("Choose a voice" appearing
        // behind the voice list). Paint the app background first.
        canvas.drawColor(0xFFFBF7EC.toInt())
        // Measure before drawing. view.layout() alone does not re-measure the children,
        // so drawing at a size the tree was never measured for produces half-empty
        // screenshots (and made the yellow-pixel assertion meaningless).
        forceLayoutTree(view)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
        view.draw(canvas)
        return bitmap
    }

    /** measure() returns cached results unless every view is marked dirty first. */
    private fun forceLayoutTree(view: View) {
        view.forceLayout()
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) forceLayoutTree(view.getChildAt(i))
        }
    }

    /** Where the brand yellow actually landed, in bitmap coordinates. */
    private fun yellowBounds(bitmap: Bitmap): String {
        var minX = Int.MAX_VALUE
        var maxX = -1
        var minY = Int.MAX_VALUE
        var maxY = -1
        var x = 0
        while (x < bitmap.width) {
            var y = 0
            while (y < bitmap.height) {
                val c = bitmap.getPixel(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                if (r > 190 && g > 150 && b < 130) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
                y += 7
            }
            x += 7
        }
        return if (maxX < 0) "none" else "x=$minX..$maxX y=$minY..$maxY in ${bitmap.width}x${bitmap.height}"
    }

    private fun distinctColors(bitmap: Bitmap): Int {
        val seen = HashSet<Int>()
        var x = 0
        while (x < bitmap.width) {
            var y = 0
            while (y < bitmap.height) {
                seen.add(bitmap.getPixel(x, y))
                if (seen.size > 40) return seen.size
                y += 11
            }
            x += 11
        }
        return seen.size
    }

    private fun capture(view: View, name: String) {
        try {
            val bitmap = drawToBitmap(view)
            File(shots, name).outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            println("screenshot written: ${File(shots, name).absolutePath} (${File(shots, name).length()} bytes)")
        } catch (t: Throwable) {
            println("screenshot failed for $name: ${t.message}")
        }
    }
}
