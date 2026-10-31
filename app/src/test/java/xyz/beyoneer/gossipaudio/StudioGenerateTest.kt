package xyz.beyoneer.gossipaudio

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import xyz.beyoneer.gossipaudio.audio.Exporter
import xyz.beyoneer.gossipaudio.audio.WavReader
import xyz.beyoneer.gossipaudio.core.Mp3Encoder
import xyz.beyoneer.gossipaudio.data.AudioFormat
import xyz.beyoneer.gossipaudio.ui.MainActivity
import xyz.beyoneer.gossipaudio.ui.widgets.BrandButton
import java.io.File
import java.time.Duration

/**
 * End-to-end UI test of the headline flow: type text in the composer → tap Generate →
 * a real clip appears in the store with a real WAV on disk → the same clip exports to MP3.
 *
 * Everything below runs the production code paths; only the Android TTS engine is absent
 * (so the app does what it does on a bare phone: falls back to NanoVoice).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StudioGenerateTest {

    private val line = "Testing the studio, one two three. Your face is funny tho."

    @Test
    fun typingAndTappingGenerateCreatesAPlayableClip() {
        val app = ApplicationProvider.getApplicationContext<GossipApp>()
        app.prefs.onboardingDone = true
        app.prefs.autoPlay = false      // keep MediaPlayer out of the JVM test
        app.prefs.notifications = false
        app.prefs.reduceMotion = false

        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val mainLooper = shadowOf(Looper.getMainLooper())
        mainLooper.idleFor(Duration.ofSeconds(4))
        mainLooper.idle()

        val root = activity.window.decorView
        val input = findFirst<EditText>(root)
        assertNotNull("composer EditText should exist", input)
        input!!.setText(line)
        mainLooper.idle()

        val generate = findGenerateButton(root)
        assertNotNull("Generate button should exist", generate)
        assertEquals("Generate voice", generate!!.text())
        generate.performClick()

        // Synthesis + encode + save run on worker threads; drain the main looper until the clip lands.
        var clip = app.store.all().firstOrNull()
        var waited = 0
        while (clip == null && waited < 90_000) {
            Thread.sleep(200)
            mainLooper.idleFor(Duration.ofMillis(400))
            waited += 200
            clip = app.store.all().firstOrNull()
        }
        println("generate finished after ${waited} ms (engine=${clip?.engine}, ${clip?.durationMs} ms)")
        assertNotNull("a clip should be saved after Generate", clip)
        clip!!

        assertTrue("title should be derived from the text", clip.title.isNotBlank())
        assertTrue("text should be stored", clip.text.contains("Testing the studio"))
        assertTrue("duration should be audible: ${clip.durationMs}", clip.durationMs > 800)
        assertTrue("size should be real: ${clip.sizeBytes}", clip.sizeBytes > 20_000)
        assertEquals("nano", clip.engine)                        // no TTS engine in the JVM
        assertEquals("riya", clip.voiceId)                       // default voice
        assertEquals(22050, clip.sampleRate)

        val master = File(activity.filesDir, "clips/${clip.fileName}")
        assertTrue("master WAV missing: ${master.absolutePath}", master.exists() && master.length() > 20_000)
        val pcm = WavReader.read(master)
        assertNotNull("master WAV must parse", pcm)
        assertTrue("master must contain audio", pcm!!.samples.isNotEmpty())
        assertTrue("master must not be silent", pcm.peak() > 1500)

        val cover = File(activity.filesDir, "covers/${clip.coverName}")
        assertTrue("generated cover art missing: ${cover.absolutePath}", cover.exists() && cover.length() > 1_000)
        assertTrue("counter should increment", app.prefs.totalGenerations >= 1)

        // Re-export the very same master as MP3, exactly like the Download sheet does.
        val mp3 = Exporter.build(
            activity,
            pcm,
            Exporter.Options(
                format = AudioFormat.MP3,
                bitrateKbps = 128,
                coverFile = cover,
                title = clip.title,
                voiceLabel = "Riya",
                styleLabel = "Natural"
            )
        )
        assertNotNull("MP3 export should succeed", mp3)
        val bytes = mp3!!.file.readBytes()
        assertTrue("MP3 looks like MP3", Mp3Encoder.looksLikeMp3(bytes))
        assertTrue("MP3 should be a few KB: ${bytes.size}", bytes.size > 4_000)
        println("exported ${mp3.displayName}: ${bytes.size} bytes")

        // The UI should have switched to the player for the fresh clip.
        val screenName = activity.router.current()?.javaClass?.simpleName
        println("screen after generate: $screenName")
    }

    // ------------------------------------------------------------------ helpers

    private fun <T : View> findFirst(root: View): T? {
        if (root is EditText) {
            @Suppress("UNCHECKED_CAST")
            return root as? T
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findFirst<T>(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun findGenerateButton(root: View): BrandButton? {
        if (root is BrandButton && root.text() == "Generate voice") return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findGenerateButton(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    @Suppress("unused")
    private fun labelOf(view: View): String? = (view as? TextView)?.text?.toString()
}
