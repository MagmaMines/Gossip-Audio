package xyz.beyoneer.gossipaudio.core

import java.io.ByteArrayOutputStream
import ru.wowa.jump3r.mp3.BitStream
import ru.wowa.jump3r.mp3.GainAnalysis
import ru.wowa.jump3r.mp3.ID3Tag
import ru.wowa.jump3r.mp3.Lame
import ru.wowa.jump3r.mp3.MPEGMode
import ru.wowa.jump3r.mp3.Presets
import ru.wowa.jump3r.mp3.Quantize
import ru.wowa.jump3r.mp3.QuantizePVT
import ru.wowa.jump3r.mp3.VBRTag
import ru.wowa.jump3r.mp3.Version
import ru.wowa.jump3r.mpg.MPGLib

/**
 * MP3 (MPEG-1/2 Layer III) encoding using the vendored pure-Java LAME port.
 * No NDK, no ffmpeg, no native .so — it works on every device the app supports.
 */
object Mp3Encoder {

    private val SUPPORTED_RATES = intArrayOf(8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000)

    fun encode(pcm: Pcm, requestedBitrateKbps: Int = 128, quality: Int = 4): ByteArray {
        if (pcm.samples.isEmpty()) return ByteArray(0)
        val mono = pcm.mono()
        val sampleRate = nearestSupportedRate(mono.sampleRate)
        val source = if (sampleRate != mono.sampleRate) AudioFx.resample(mono, sampleRate) else mono
        val bitrate = clampBitrate(sampleRate, requestedBitrateKbps)

        val gfp = Lame.lame_init()
        gfp.num_channels = 1
        gfp.in_samplerate = sampleRate
        gfp.brate = bitrate
        gfp.mode = MPEGMode.MONO
        gfp.quality = quality.coerceIn(0, 9)
        gfp.bWriteVbrTag = false
        gfp.copyright = 0
        gfp.disable_reservoir = false

        val lame = Lame(
            GainAnalysis(),
            BitStream(),
            Presets(),
            QuantizePVT(),
            Quantize(),
            VBRTag(),
            Version(),
            ID3Tag(),
            MPGLib()
        )
        val rc = lame.lame_init_params(gfp)
        if (rc < 0) throw IllegalStateException("LAME init failed ($rc)")

        val sink = ByteArrayOutputStream(source.samples.size / 3 + 8192)
        val chunk = 1152
        val buffer = ByteArray(65536)
        val normal = IntArray(chunk)
        var i = 0
        while (i < source.samples.size) {
            val n = minOf(chunk, source.samples.size - i)
            for (k in 0 until n) normal[k] = source.samples[i + k].toInt()
            val written = lame.lame_encode_buffer_int(gfp, normal, normal, n, buffer, 0, buffer.size)
            if (written < 0) throw IllegalStateException("LAME encode error ($written)")
            if (written > 0) sink.write(buffer, 0, written)
            i += n
        }
        val flushed = lame.lame_encode_flush(gfp, buffer, 0, buffer.size)
        if (flushed > 0) sink.write(buffer, 0, flushed)
        Lame.lame_close(gfp)
        return sink.toByteArray()
    }

    fun nearestSupportedRate(rate: Int): Int {
        var best = SUPPORTED_RATES[0]
        var bestDiff = Int.MAX_VALUE
        for (r in SUPPORTED_RATES) {
            val d = kotlin.math.abs(r - rate)
            if (d < bestDiff) {
                bestDiff = d
                best = r
            }
        }
        return best
    }

    fun clampBitrate(sampleRate: Int, wanted: Int): Int {
        val max = when {
            sampleRate >= 32000 -> 320
            sampleRate >= 16000 -> 160
            else -> 64
        }
        val min = 8
        val steps = intArrayOf(8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
        val target = wanted.coerceIn(min, max)
        var best = steps.first()
        for (s in steps) {
            if (s <= target) best = s
        }
        return best
    }

    /** Quick validity probe used by tests and by the exporter's self-check. */
    fun looksLikeMp3(b: ByteArray): Boolean {
        if (b.size < 4) return false
        val start = if (Id3.hasId3(b)) Id3.tagSize(b) else 0
        if (start + 1 >= b.size) return false
        val b0 = b[start].toInt() and 0xFF
        val b1 = b[start + 1].toInt() and 0xFF
        return b0 == 0xFF && (b1 and 0xE0) == 0xE0
    }
}
