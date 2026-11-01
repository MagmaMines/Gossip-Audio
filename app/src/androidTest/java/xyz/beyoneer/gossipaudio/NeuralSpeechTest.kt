package xyz.beyoneer.gossipaudio

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import xyz.beyoneer.gossipaudio.core.Wav
import xyz.beyoneer.gossipaudio.core.Mp3Encoder
import xyz.beyoneer.gossipaudio.speech.NeuralEngine
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class NeuralSpeechTest {
    @Test fun fullPrecisionNeuralVoiceProducesUnclippedAudioAndExports() {
        val context = ApplicationProvider.getApplicationContext<GossipApp>()
        assertTrue("Neural model assets missing", NeuralEngine.available(context))
        val pcm = NeuralEngine.render(context, listOf("Hello! Welcome to Gossip Audio. Your words, their wings."), 0, 1f) { _, _ -> }
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
