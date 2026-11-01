package xyz.beyoneer.gossipaudio

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import xyz.beyoneer.gossipaudio.core.StyleCatalog
import xyz.beyoneer.gossipaudio.core.VoiceCatalog
import xyz.beyoneer.gossipaudio.speech.SpeechSynth
import xyz.beyoneer.gossipaudio.speech.VoiceDownloads
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ExtraVoicesTest {
    @Test fun downloadAndSynthesizeAllExtraVoices() {
        val context = ApplicationProvider.getApplicationContext<GossipApp>()
        for ((id, text) in listOf("cori" to "Hello, welcome to Gossip Audio.",
                                  "ljspeech" to "Hello, welcome to Gossip Audio.",
                                  "padmavathi" to "నమస్కారం! మీ మాటలకు స్వాగతం.",
                                  "hi_super" to "नमस्ते! आपका स्वागत है।",
                                  "ja_super" to "こんにちは。今日はいい天気です。",
                                  "fr_super" to "Bonjour et bienvenue à tous.",
                                  "zh_kokoro" to "你好，欢迎来到语音工作室。")) {
            VoiceDownloads.download(context, id)
            assertTrue("Voice download not installed: $id", VoiceDownloads.installed(context, id))
            if (id == "hi_super") assertTrue("Shared pack missing", VoiceDownloads.installed(context, "ja_super") && VoiceDownloads.installed(context, "fr_super"))
            val result = AtomicReference<SpeechSynth.Outcome>()
            val failure = AtomicReference<String>()
            val latch = CountDownLatch(1)
            SpeechSynth(context).synthesize(text, VoiceCatalog.byId(id), StyleCatalog.byId("natural"), 1f, 1f,
                object : SpeechSynth.Callback {
                    override fun onProgress(fraction: Float, label: String) = Unit
                    override fun onSuccess(outcome: SpeechSynth.Outcome) { result.set(outcome); latch.countDown() }
                    override fun onError(message: String) { failure.set(message); latch.countDown() }
                })
            assertTrue("$id timed out", latch.await(300, TimeUnit.SECONDS))
            assertTrue("$id failed: ${failure.get()}", result.get() != null)
            assertTrue("$id produced silence", result.get().pcm.peak() > 200)
            assertTrue("$id produced short audio", result.get().pcm.durationMs > 300)
        }
    }
}
