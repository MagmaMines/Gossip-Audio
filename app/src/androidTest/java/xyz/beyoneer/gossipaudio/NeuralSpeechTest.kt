package xyz.beyoneer.gossipaudio

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import xyz.beyoneer.gossipaudio.speech.NeuralEngine

@RunWith(AndroidJUnit4::class)
class NeuralSpeechTest {
    @Test fun bundledModelGeneratesNonSilentAudioForFirstAndLastVoice() {
        val context = ApplicationProvider.getApplicationContext<GossipApp>()
        assertTrue("Model assets missing", NeuralEngine.available(context))
        for (speaker in listOf(0, 9)) {
            val pcm = NeuralEngine.render(context, listOf("Hello, welcome to Gossip Audio."), speaker, 1f) { _, _ -> }
            assertTrue("Speaker $speaker output is too short", pcm.durationMs > 500)
            assertTrue("Speaker $speaker output is silent", pcm.peak() > 200)
        }
    }
}
