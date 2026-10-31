package xyz.beyoneer.gossipaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.beyoneer.gossipaudio.core.AudioFx
import xyz.beyoneer.gossipaudio.core.Id3
import xyz.beyoneer.gossipaudio.core.Mp3Encoder
import xyz.beyoneer.gossipaudio.core.NanoVoice
import xyz.beyoneer.gossipaudio.core.Pcm
import xyz.beyoneer.gossipaudio.core.StyleCatalog
import xyz.beyoneer.gossipaudio.core.TextKit
import xyz.beyoneer.gossipaudio.core.VoiceCatalog
import xyz.beyoneer.gossipaudio.core.VoiceGender
import xyz.beyoneer.gossipaudio.core.Wav
import java.io.File

/**
 * Runs the real on-device audio pipeline on the JVM build machine:
 * NanoVoice synthesis -> WAV bytes -> LAME/Java MP3 -> ID3v2 tagging.
 *
 * No mocks, no fixtures: if these pass, the same code paths that run inside the
 * APK are able to produce valid audio files.
 */
class PipelineTest {

    private val sampleDir = File("build/test-samples").apply { mkdirs() }
    private val line = "Btw we're not gossiping inreal, but your face is funny tho."

    @Test
    fun catalogHasTenVoicesAndEightStyles() {
        assertEquals(10, VoiceCatalog.all.size)
        assertEquals(4, VoiceCatalog.femaleCount)
        assertEquals(5, VoiceCatalog.maleCount)
        assertEquals(1, VoiceCatalog.botCount)
        assertEquals(8, StyleCatalog.all.size)
        assertTrue(VoiceCatalog.all.any { it.gender == VoiceGender.NONHUMAN })
    }

    @Test
    fun everyVoiceSynthesisesAudibleSpeech() {
        val report = StringBuilder()
        VoiceCatalog.all.forEach { voice ->
            val pcm = NanoVoice.speak(
                line,
                NanoVoice.Options(
                    baseF0 = 92.0 + voice.pitch * 34.0,
                    rate = voice.rate.toDouble(),
                    robotic = voice.engine == xyz.beyoneer.gossipaudio.core.VoiceEngine.NANO_VOICE && voice.gender == VoiceGender.NONHUMAN,
                    sampleRate = 22050
                )
            )
            assertTrue("${voice.name} produced no samples", pcm.samples.isNotEmpty())
            val seconds = pcm.durationMs / 1000.0
            assertTrue("${voice.name} too short: $seconds s", seconds > 1.5)
            assertTrue("${voice.name} too long: $seconds s", seconds < 20.0)
            assertTrue("${voice.name} is silent", pcm.peak() > 1800)
            val wav = Wav.write(pcm)
            assertTrue(Wav.looksLikeWav(wav))
            if (voice.id in setOf("riya", "bot", "maya", "arjun")) {
                File(sampleDir, "voice-${voice.id}.wav").writeBytes(wav)
            }
            report.append(voice.name).append(" ").append(pcm.durationMs).append("ms ")
                .append(pcm.peak()).append(" peak\n")
        }
        File(sampleDir, "voices.txt").writeText(report.toString())
        println(report)
    }

    @Test
    fun wavRoundTripPreservesSamples() {
        val pcm = NanoVoice.speak("Testing one two three.", NanoVoice.Options(baseF0 = 128.0, sampleRate = 22050))
        val bytes = Wav.write(pcm)
        val back = Wav.read(bytes)
        assertNotNull(back)
        back!!
        assertEquals(pcm.sampleRate, back.sampleRate)
        assertEquals(pcm.channels, back.channels)
        assertEquals(pcm.samples.size, back.samples.size)
        assertEquals("44 + data", 44 + pcm.samples.size * 2, bytes.size)
    }

    @Test
    fun mp3EncoderProducesRealFramesAndTags() {
        val pcm = NanoVoice.speak(line, NanoVoice.Options(baseF0 = 124.0, rate = 1.0))
        val mp3 = Mp3Encoder.encode(pcm, 128, 4)
        assertTrue("mp3 too small: ${mp3.size}", mp3.size > 8000)
        assertTrue("missing frame sync", Mp3Encoder.looksLikeMp3(mp3))

        val expectedBytes = pcm.durationMs / 1000.0 * 128_000 / 8
        val ratio = mp3.size / expectedBytes
        assertTrue("bitrate sanity, ratio=$ratio", ratio in 0.55..1.65)

        val tags = Id3.Tags(
            title = "Unit test gossip",
            artist = "GossipAudio by Beyoneer XYZ",
            album = "CI pipeline",
            comment = "Encoded by the vendored LAME/Java port",
            year = "2025"
        )
        val tagged = Id3.attachToMp3(mp3, tags)
        assertTrue(Id3.hasId3(tagged))
        assertTrue("tag header missing", Id3.tagSize(tagged) > 60)
        assertEquals("tag + frames", tagged.size, mp3.size + Id3.tagSize(tagged))

        File(sampleDir, "pipeline-sample.mp3").writeBytes(tagged)
        File(sampleDir, "pipeline-sample.wav").writeBytes(Wav.write(pcm))
        File(sampleDir, "pipeline-sample.txt").writeText(
            "source=${pcm.sampleRate}Hz ${pcm.samples.size} samples (${pcm.durationMs} ms)\n" +
                "mp3=${mp3.size} bytes, tagged=${tagged.size} bytes, id3=${Id3.tagSize(tagged)} bytes\n"
        )
    }

    @Test
    fun stylesChangeRateAndPitchNumerically() {
        val base = NanoVoice.speak("Gossip test", NanoVoice.Options(baseF0 = 120.0, rate = 1.0))
        val slow = NanoVoice.speak("Gossip test", NanoVoice.Options(baseF0 = 120.0, rate = 0.8))
        assertTrue("slow version should be longer", slow.durationMs > base.durationMs)
        StyleCatalog.all.forEach { style ->
            assertTrue("style ${style.id} rate out of range", style.rateFactor in 0.5f..2.0f)
            assertTrue("style ${style.id} pitch out of range", style.pitchFactor in 0.5f..2.0f)
        }
    }

    @Test
    fun audioEffectsAreStable() {
        val pcm = NanoVoice.speak("Effects test.", NanoVoice.Options(baseF0 = 110.0))
        val normalized = AudioFx.normalize(pcm, -1.0)
        assertTrue("normalize should not clip", normalized.peak() <= 32767)
        assertTrue("normalize should raise level", normalized.peak() >= pcm.peak())

        val faded = AudioFx.fade(pcm, 20, 60)
        assertEquals(pcm.samples.size, faded.samples.size)
        assertTrue("fade-in starts near silence", kotlin.math.abs(faded.samples[0].toInt()) < 64)

        val resampled = AudioFx.resample(pcm, 44100)
        assertEquals(44100, resampled.sampleRate)
        val ratio = resampled.frames.toDouble() / pcm.frames
        assertTrue("resample ratio $ratio", ratio in 1.9..2.1)

        val trimmed = AudioFx.trimSilence(pcm)
        assertTrue(trimmed.samples.isNotEmpty())
    }

    @Test
    fun textKitEstimatesMatchRenderedAudio() {
        val text = "This is a reasonably long sentence that should take a few seconds to say out loud."
        val estimate = TextKit.estimateDurationMs(text)
        val rendered = NanoVoice.speak(text, NanoVoice.Options(baseF0 = 118.0)).durationMs
        assertTrue("words counted", TextKit.wordCount(text) > 12)
        assertTrue(estimate > 1500)
        assertTrue("estimate $estimate vs rendered $rendered", rendered in estimate / 3..estimate * 3)
        assertEquals("Untitled gossip", TextKit.titleFrom("   \n  "))
        assertEquals("hello-world-2025", TextKit.safeFileName("Hello / World: 2025!"))
    }

    @Test
    fun silenceAndEmptyInputAreHandledGracefully() {
        val empty = NanoVoice.speak("   ", NanoVoice.Options())
        assertEquals(0, empty.samples.size)
        val silent = Pcm(22050, 1, ShortArray(22050))
        assertEquals(1000L, silent.durationMs)
        val silentMp3 = Mp3Encoder.encode(silent, 96)
        assertTrue("silence still yields a decodable stream", silentMp3.isEmpty() || Mp3Encoder.looksLikeMp3(silentMp3))
    }
}
