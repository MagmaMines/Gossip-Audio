package xyz.beyoneer.gossipaudio

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.beyoneer.gossipaudio.speech.SpeechSynth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpeechChunkTest {
    private val synth = SpeechSynth(ApplicationProvider.getApplicationContext<GossipApp>())

    @Test fun longUnbrokenTextStaysWithinLimit() {
        val input = "x".repeat(1501)
        val chunks = synth.chunkText(input, 700)
        assertTrue(chunks.all { it.length <= 700 })
        assertEquals(input, chunks.joinToString(""))
    }

    @Test fun emojiIsNotSplitAtBoundary() {
        val input = "x".repeat(699) + "🦋" + "y".repeat(700)
        val chunks = synth.chunkText(input, 700)
        assertEquals(input, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 700 })
        assertFalse(chunks.any { Character.isHighSurrogate(it.last()) || Character.isLowSurrogate(it.first()) })
    }
}
