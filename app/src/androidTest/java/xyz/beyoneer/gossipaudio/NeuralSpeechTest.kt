package xyz.beyoneer.gossipaudio

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import xyz.beyoneer.gossipaudio.core.Wav
import xyz.beyoneer.gossipaudio.core.Mp3Encoder
import xyz.beyoneer.gossipaudio.speech.NeuralEngine
import xyz.beyoneer.gossipaudio.speech.SpeechSynth
import xyz.beyoneer.gossipaudio.core.VoiceCatalog
import xyz.beyoneer.gossipaudio.core.StyleCatalog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class NeuralSpeechTest {
    @Test fun fullPrecisionNeuralVoiceProducesUnclippedAudioAndExports() {
        val context = ApplicationProvider.getApplicationContext<GossipApp>()
        assertTrue("Neural model assets missing", NeuralEngine.available(context))
        val latch = CountDownLatch(1)
        val outcome = AtomicReference<SpeechSynth.Outcome>()
        val failure = AtomicReference<String>()
        SpeechSynth(context).synthesize("Hello! Welcome to Gossip Audio. Your words, their wings.",
            VoiceCatalog.byId("riya"), StyleCatalog.byId("natural"), 1f, 1f,
            object : SpeechSynth.Callback {
                override fun onProgress(fraction: Float, label: String) = Unit
                override fun onSuccess(result: SpeechSynth.Outcome) { outcome.set(result); latch.countDown() }
                override fun onError(message: String) { failure.set(message); latch.countDown() }
            })
        assertTrue("Neural synthesis timed out", latch.await(90, TimeUnit.SECONDS))
        assertTrue("Neural synthesis failed: ${failure.get()}", outcome.get() != null)
        assertTrue("Unexpected synthesis engine", outcome.get().engine == "neural")
        val pcm = outcome.get().pcm
        assertTrue("Output is too short", pcm.durationMs > 1000)
        assertTrue("Output is silent", pcm.peak() > 200)
        assertTrue("Hard-clipped samples detected", pcm.samples.none { abs(it.toInt()) >= 32760 })
        val wav = Wav.write(pcm)
        assertTrue("WAV did not round trip", Wav.read(wav)?.samples?.size == pcm.samples.size)
        val mp3 = Mp3Encoder.encode(pcm, 192, 4)
        assertTrue("MP3 encoder did not produce frames", Mp3Encoder.looksLikeMp3(mp3))
        // MediaStore downloads survive Gradle uninstalling the test app.
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "gossipaudio-neural-sample.wav")
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to save sample to emulator Downloads")
        context.contentResolver.openOutputStream(uri)!!.use { it.write(wav) }
    }
}
