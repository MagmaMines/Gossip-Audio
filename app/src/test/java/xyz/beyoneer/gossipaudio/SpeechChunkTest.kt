package xyz.beyoneer.gossipaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.beyoneer.gossipaudio.speech.TextChunker

class SpeechChunkTest {
    @Test fun longUnbrokenTextStaysWithinLimit() {
        val input = "x".repeat(1501)
        val chunks = TextChunker.split(input, 700)
        assertTrue(chunks.all { it.length <= 700 })
        assertEquals(input, chunks.joinToString(""))
    }

    @Test fun emojiIsNotSplitAtBoundary() {
        val input = "x".repeat(699) + "🦋" + "y".repeat(700)
        val chunks = TextChunker.split(input, 700)
        assertEquals(input, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 700 })
        assertFalse(chunks.any { Character.isHighSurrogate(it.last()) || Character.isLowSurrogate(it.first()) })
    }
}
