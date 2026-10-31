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
        // SpeechSynth derives F0 from gender x pitch: these ranges keep the four
        // female voices above the five male ones even after the multiplier.
        assertTrue(
            "female voices need a higher pitch factor than male ones",
            VoiceCatalog.all.filter { it.gender == VoiceGender.FEMALE }.all { it.pitch > 1.05f } &&
                VoiceCatalog.all.filter { it.gender == VoiceGender.MALE }.all { it.pitch < 0.96f }
        )
    }

    @Test
    fun everyVoiceSynthesisesAudibleSpeech() {
        val report = StringBuilder()
        VoiceCatalog.all.forEach { voice ->
            val pcm = NanoVoice.speak(
                line,
                NanoVoice.Options(
                    baseF0 = xyz.beyoneer.gossipaudio.core.VoiceTuning.baseF0(voice),
                    formantScale = xyz.beyoneer.gossipaudio.core.VoiceTuning.formantScale(voice),
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
            // trim like the app's own finish() does, so the published sample is what a
            // user actually gets (no 400 ms of dead air at the end)
            val wav = Wav.write(AudioFx.trimSilence(pcm, 120, 20))
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
        // NanoVoice v2 already lands near full scale, so test both directions of
        // normalize instead of assuming the old, very quiet output.
        val quiet = AudioFx.gain(pcm, 0.2)
        val raised = AudioFx.normalize(quiet, -1.0)
        assertTrue("normalize should raise a quiet signal", raised.peak() > quiet.peak())
        val tamed = AudioFx.normalize(pcm, -1.0)
        assertTrue("normalize should not clip", tamed.peak() in 1..32767)

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
    /**
     * The regression guard for the "only ticks" bug report. A voiced frame of
     * real speech puts most of its energy on harmonics of F0; v1 scored 0.31-0.52
     * here (noise-like), v2 scores 0.85+. This test fails the build if anyone
     * ever ships a buzz generator again.
     */
    @Test
    fun renderedAudioIsTonalSpeechNotNoise() {
        val text = "Btw we're not gossiping inreal, your face is funny tho."
        val pcm = NanoVoice.speak(text, NanoVoice.Options(baseF0 = 126.0))
        assertTrue("audio too short", pcm.durationMs > 2000)
        val fs = pcm.sampleRate.toDouble()
        val frame = 660
        val hop = 320
        val energies = ArrayList<Double>()
        var start = 0
        while (start + frame <= pcm.samples.size) {
            var e = 0.0
            for (j in 0 until frame) {
                val v = pcm.samples[start + j] / 32768.0
                e += v * v
            }
            energies.add(e)
            start += hop
        }
        val loud = energies.sorted()[((energies.size - 1) * 0.9).toInt()]
        val ratios = ArrayList<Double>()
        var frameStart = 0
        var index = 0
        while (frameStart + frame <= pcm.samples.size) {
            if (energies[index] > loud * 0.45) {
                val x = DoubleArray(frame) { j -> pcm.samples[frameStart + j] / 32768.0 }
                val mean = x.average()
                for (j in x.indices) x[j] -= mean
                var best = 0.0
                var bestLag = 0
                for (lag in (fs / 350).toInt()..(fs / 70).toInt()) {
                    var num = 0.0
                    var ea = 0.0
                    var eb = 0.0
                    for (j in 0 until frame - lag) {
                        num += x[j] * x[j + lag]
                        ea += x[j] * x[j]
                        eb += x[j + lag] * x[j + lag]
                    }
                    val r = if (ea <= 0.0 || eb <= 0.0) 0.0 else num / kotlin.math.sqrt(ea * eb)
                    if (r > best) {
                        best = r
                        bestLag = lag
                    }
                }
                if (bestLag > 0 && best > 0.25) {
                    val f0 = fs / bestLag
                    var harmonic = 0.0
                    var h = f0
                    while (h < 4500.0) {
                        var re = 0.0
                        var im = 0.0
                        for (j in x.indices) {
                            val w = 0.54 - 0.46 * kotlin.math.cos(2.0 * kotlin.math.PI * j / (frame - 1))
                            val ang = 2.0 * kotlin.math.PI * h * j / fs
                            re += x[j] * w * kotlin.math.cos(ang)
                            im += x[j] * w * kotlin.math.sin(ang)
                        }
                        harmonic += re * re + im * im
                        h += f0
                    }
                    var total = 0.0
                    var k = 40
                    while (k * fs / frame < 4500.0) {
                        var re = 0.0
                        var im = 0.0
                        val f = k * fs / frame
                        for (j in x.indices) {
                            val w = 0.54 - 0.46 * kotlin.math.cos(2.0 * kotlin.math.PI * j / (frame - 1))
                            val ang = 2.0 * kotlin.math.PI * f * j / fs
                            re += x[j] * w * kotlin.math.cos(ang)
                            im += x[j] * w * kotlin.math.sin(ang)
                        }
                        total += re * re + im * im
                        k += 4
                    }
                    if (total > 0.0) ratios.add((harmonic / total).coerceIn(0.0, 1.0))
                }
            }
            frameStart += hop
            index++
        }
        // Report before asserting: when a regression lands, the numbers have to survive
        // into dist/samples/quality.txt, otherwise the CI log only says "failed".
        val median = if (ratios.isEmpty()) 0.0 else ratios.sorted()[ratios.size / 2]
        val shares = bandShares(pcm.samples, pcm.sampleRate)
        val report = buildString {
            append("harmonic_ratio_median=").append(String.format("%.3f", median)).append('\n')
            append("voiced_frames=").append(ratios.size).append('\n')
            append("duration_ms=").append(pcm.durationMs).append('\n')
            append("peak=").append(pcm.peak()).append('\n')
            append("bands_percent=").append(formatShares(shares)).append('\n')
            append("estimate=").append("v4 cascade formant synthesiser").append('\n')
        }
        File(sampleDir, "quality.txt").writeText(report)
        File(sampleDir, "voice-quality.wav").writeBytes(Wav.write(AudioFx.trimSilence(pcm, 120, 20)))
        println(report)
        assertTrue("not enough voiced frames: ${ratios.size}", ratios.size > 20)
        assertTrue("harmonic ratio $median is noise-like (ticks!), expected > 0.65", median > 0.65)
        // A buzzer has ~all of its energy under 300 Hz. Speech puts real power in
        // F2/F3 (800-4000 Hz); if that band collapses the engine is a kazoo again.
        assertTrue(
            "upper formants are missing: ${formatShares(shares)}",
            shares[2] + shares[3] > 4.0
        )
        assertTrue(
            "all energy sits under 300 Hz: ${formatShares(shares)}",
            shares[0] < 48.0
        )
    }

    /** Energy share per band, so a regression to "one flat buzz" shows up in CI. */
    /**
     * Audio lab: renders the same sentence through every mixing strategy and writes
     * the band balance of each, so the choice is made from measurements instead of
     * taste. Not an assertion — it exists to feed CI's quality report.
     */
    @Test
    fun mixLab() {
        val text = "Btw we're not gossiping inreal, your face is funny tho."
        val report = StringBuilder()
        for (mix in 0..2) {
            val pcm = NanoVoice.speak(text, NanoVoice.Options(baseF0 = 126.0, mix = mix))
            val shares = bandShares(pcm.samples, pcm.sampleRate)
            File(sampleDir, "mix-$mix.wav").writeBytes(Wav.write(pcm))
            report.append("mix=").append(mix)
                .append(" rms=").append(String.format("%.0f", pcm.rms()))
                .append(" bands=").append(formatShares(shares))
                .append('\n')
        }
        File(sampleDir, "mix-lab.txt").writeText(report.toString())
        println(report)
    }

    private fun bandShares(samples: ShortArray, fs: Int): DoubleArray {
        val bands = listOf(0 to 300, 300 to 800, 800 to 2000, 2000 to 4000, 4000 to 8000)
        val frame = 1024
        val energy = DoubleArray(bands.size)
        var frames = 0
        var start = 0
        while (start + frame <= samples.size) {
            var rms = 0.0
            for (i in start until start + frame) {
                val v = samples[i] / 32768.0
                rms += v * v
            }
            if (kotlin.math.sqrt(rms / frame) > 0.02) {
                frames++
                val x = DoubleArray(frame) { samples[start + it] / 32768.0 }
                bands.forEachIndexed { bi, (lo, hi) ->
                    var k = maxOf(1, lo * frame / fs)
                    val kEnd = minOf(frame / 2 - 1, maxOf(k + 1, hi * frame / fs))
                    while (k <= kEnd) {
                        var re = 0.0
                        var im = 0.0
                        val f = k * fs.toDouble() / frame
                        for (j in 0 until frame) {
                            val w = 0.54 - 0.46 * kotlin.math.cos(2.0 * kotlin.math.PI * j / (frame - 1))
                            val ang = 2.0 * kotlin.math.PI * f * j / fs
                            re += x[j] * w * kotlin.math.cos(ang)
                            im += x[j] * w * kotlin.math.sin(ang)
                        }
                        energy[bi] += re * re + im * im
                        k += 6
                    }
                }
            }
            start += frame
        }
        val total = energy.sum().coerceAtLeast(1e-9)
        return DoubleArray(energy.size) { 100.0 * energy[it] / total }
    }

    private fun formatShares(shares: DoubleArray): String {
        val bands = listOf("0-300", "300-800", "800-2000", "2000-4000", "4000-8000")
        return bands.mapIndexed { i, b -> "$b=" + String.format("%.1f", shares[i]) }.joinToString(" ")
    }

    @Test
    fun hindiAndHinglishBothProduceSpeech() {
        val hindi = NanoVoice.speak("नमस्ते दोस्तों, आज की गॉसिप सुनो।", NanoVoice.Options(baseF0 = 168.0))
        assertTrue("hindi audio missing", hindi.durationMs > 1200)
        assertTrue("hindi too quiet", hindi.peak() > 2000)
        val hinglish = NanoVoice.speak("kaise ho bhai, sab badhiya hai?", NanoVoice.Options(baseF0 = 150.0))
        assertTrue("hinglish audio missing", hinglish.durationMs > 800)
        assertEquals("K EY S EY", NanoVoice.phonemize("kaise").joinToString(" "))
        assertTrue(NanoVoice.phonemize("नमस्ते").isNotEmpty())
        File(sampleDir, "voice-hindi.wav").writeBytes(Wav.write(AudioFx.trimSilence(hindi, 120, 20)))
        File(sampleDir, "voice-hinglish.wav").writeBytes(Wav.write(AudioFx.trimSilence(hinglish, 120, 20)))
    }
}
