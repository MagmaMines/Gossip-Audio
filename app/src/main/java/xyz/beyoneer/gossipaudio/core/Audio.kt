package xyz.beyoneer.gossipaudio.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 16-bit PCM audio held in memory. GossipAudio keeps everything mono except
 * when a user drops in a stereo file, in which case channels are preserved
 * until [mono] is called.
 */
data class Pcm(
    val sampleRate: Int,
    val channels: Int,
    val samples: ShortArray
) {
    val frames: Int get() = if (channels <= 0) 0 else samples.size / channels
    val durationMs: Long get() = if (sampleRate <= 0) 0L else frames * 1000L / sampleRate

    fun mono(): Pcm {
        if (channels == 1) return this
        val out = ShortArray(frames)
        for (i in 0 until frames) {
            var acc = 0
            for (c in 0 until channels) acc += samples[i * channels + c].toInt()
            out[i] = (acc / channels).toShort()
        }
        return Pcm(sampleRate, 1, out)
    }

    fun peak(): Int {
        var p = 0
        for (s in samples) {
            val v = abs(s.toInt())
            if (v > p) p = v
        }
        return p
    }

    fun rms(): Double {
        if (samples.isEmpty()) return 0.0
        var acc = 0.0
        for (s in samples) acc += s.toDouble() * s.toDouble()
        return kotlin.math.sqrt(acc / samples.size)
    }
}

/** Pure-Kotlin WAV reader/writer (no android.* imports → unit-testable on the JVM). */
object Wav {

    fun write(pcm: Pcm): ByteArray {
        val dataBytes = pcm.samples.size * 2
        val out = ByteArray(44 + dataBytes)
        putAscii(out, 0, "RIFF")
        putIntLE(out, 4, 36 + dataBytes)
        putAscii(out, 8, "WAVE")
        putAscii(out, 12, "fmt ")
        putIntLE(out, 16, 16)
        putShortLE(out, 20, 1)                       // PCM
        putShortLE(out, 22, pcm.channels)
        putIntLE(out, 24, pcm.sampleRate)
        val byteRate = pcm.sampleRate * pcm.channels * 2
        putIntLE(out, 28, byteRate)
        putShortLE(out, 32, pcm.channels * 2)        // block align
        putShortLE(out, 34, 16)                      // bits per sample
        putAscii(out, 36, "data")
        putIntLE(out, 40, dataBytes)
        var p = 44
        for (s in pcm.samples) {
            out[p++] = (s.toInt() and 0xFF).toByte()
            out[p++] = ((s.toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }

    /** True when the buffer starts with a RIFF/WAVE header. */
    fun looksLikeWav(b: ByteArray): Boolean =
        b.size >= 12 &&
            b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte() &&
            b[2] == 'F'.code.toByte() && b[3] == 'F'.code.toByte() &&
            b[8] == 'W'.code.toByte() && b[9] == 'A'.code.toByte() &&
            b[10] == 'V'.code.toByte() && b[11] == 'E'.code.toByte()

    /** True when the buffer starts with an Ogg / ID3 / MP3 / FLAC magic — used for engine sanity checks. */
    fun looksLikeCompressed(b: ByteArray): Boolean {
        if (b.size < 4) return false
        val isOgg = b[0] == 'O'.code.toByte() && b[1] == 'g'.code.toByte() && b[2] == 'g'.code.toByte() && b[3] == 'S'.code.toByte()
        val isId3 = b[0] == 'I'.code.toByte() && b[1] == 'D'.code.toByte() && b[2] == '3'.code.toByte()
        val isFlac = b[0] == 'f'.code.toByte() && b[1] == 'L'.code.toByte() && b[2] == 'a'.code.toByte() && b[3] == 'C'.code.toByte()
        val isMp3 = (b[0].toInt() and 0xFF) == 0xFF && (b[1].toInt() and 0xE0) == 0xE0
        return isOgg || isId3 || isFlac || isMp3
    }

    fun read(bytes: ByteArray): Pcm? {
        if (!looksLikeWav(bytes)) return null
        var pos = 12
        var channels = 1
        var sampleRate = 22050
        var bits = 16
        var format = 1
        var dataOffset = -1
        var dataSize = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = getIntLE(bytes, pos + 4)
            val body = pos + 8
            if (size < 0) break
            when (id) {
                "fmt " -> {
                    if (body + 16 <= bytes.size) {
                        format = getShortLE(bytes, body)
                        channels = getShortLE(bytes, body + 2).coerceAtLeast(1)
                        sampleRate = getIntLE(bytes, body + 4).coerceAtLeast(8000)
                        bits = getShortLE(bytes, body + 14).coerceAtLeast(8)
                        if (format == 0xFFFE && body + 26 <= bytes.size) {
                            format = getShortLE(bytes, body + 24)
                        }
                    }
                }
                "data" -> {
                    dataOffset = body
                    dataSize = minOf(size, bytes.size - body)
                }
            }
            pos = body + size + (size and 1)
            if (dataOffset >= 0 && id == "data") break
        }
        if (dataOffset < 0 || dataSize <= 0) return null
        val bytesPerSample = max(1, bits / 8)
        val frameBytes = bytesPerSample * channels
        val frames = dataSize / frameBytes
        if (frames <= 0) return null
        val out = ShortArray(frames * channels)
        var p = dataOffset
        var i = 0
        while (i < out.size) {
            val v: Int = when {
                format == 3 && bits == 32 -> {
                    val f = Float.fromBits(getIntLE(bytes, p))
                    (f.coerceIn(-1f, 1f) * 32767f).roundToInt()
                }
                bytesPerSample == 1 -> ((bytes[p].toInt() and 0xFF) - 128) shl 8
                bytesPerSample == 2 -> getShortLE(bytes, p)
                bytesPerSample == 3 -> {
                    val b0 = bytes[p].toInt() and 0xFF
                    val b1 = bytes[p + 1].toInt() and 0xFF
                    val b2 = bytes[p + 2].toByte().toInt()
                    ((b2 shl 16) or (b1 shl 8) or b0) shr 8
                }
                else -> getIntLE(bytes, p) shr 16
            }
            out[i] = v.coerceIn(-32768, 32767).toShort()
            p += bytesPerSample
            i++
        }
        return Pcm(sampleRate, channels, out)
    }

    /**
     * Some TTS engines hand back header-less PCM. Wrap it as 16-bit mono at the
     * advertised sample rate so playback/export still works.
     */
    fun wrapRawPcm(bytes: ByteArray, sampleRate: Int = 22050, channels: Int = 1): Pcm {
        val usable = bytes.size - (bytes.size % (2 * channels))
        val out = ShortArray(usable / 2)
        var p = 0
        for (i in out.indices) {
            out[i] = getShortLE(bytes, p).toShort()
            p += 2
        }
        return Pcm(sampleRate, channels, out)
    }

    private fun putAscii(out: ByteArray, off: Int, s: String) {
        for (i in s.indices) out[off + i] = s[i].code.toByte()
    }

    private fun putIntLE(out: ByteArray, off: Int, v: Int) {
        out[off] = (v and 0xFF).toByte()
        out[off + 1] = ((v shr 8) and 0xFF).toByte()
        out[off + 2] = ((v shr 16) and 0xFF).toByte()
        out[off + 3] = ((v shr 24) and 0xFF).toByte()
    }

    private fun putShortLE(out: ByteArray, off: Int, v: Int) {
        out[off] = (v and 0xFF).toByte()
        out[off + 1] = ((v shr 8) and 0xFF).toByte()
    }

    fun getIntLE(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)

    fun getShortLE(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
}

/** Small, dependency-free DSP helpers shared by the player, exporter and synths. */
object AudioFx {

    fun gain(pcm: Pcm, factor: Double): Pcm {
        val out = ShortArray(pcm.samples.size)
        for (i in out.indices) out[i] = (pcm.samples[i] * factor).roundToIntString().coerceIn(-32768, 32767).toShort()
        return pcm.copy(samples = out)
    }

    fun normalize(pcm: Pcm, targetDb: Double = -1.5): Pcm {
        val peak = pcm.peak()
        if (peak <= 0) return pcm
        val target = 32767.0 * Math.pow(10.0, targetDb / 20.0)
        val factor = (target / peak).coerceAtMost(8.0)
        return gain(pcm, factor)
    }

    fun fade(pcm: Pcm, fadeInMs: Int = 12, fadeOutMs: Int = 40): Pcm {
        val out = pcm.samples.copyOf()
        val sr = pcm.sampleRate.toDouble()
        val inN = (sr * fadeInMs / 1000.0).toInt().coerceAtLeast(0)
        val outN = (sr * fadeOutMs / 1000.0).toInt().coerceAtLeast(0)
        for (i in 0 until minOf(inN, out.size)) {
            out[i] = (out[i] * (i.toDouble() / inN)).roundToIntString().toShort()
        }
        for (i in 0 until minOf(outN, out.size)) {
            val idx = out.size - 1 - i
            out[idx] = (out[idx] * (i.toDouble() / outN)).roundToIntString().toShort()
        }
        return pcm.copy(samples = out)
    }

    fun trimSilence(pcm: Pcm, threshold: Int = 250, keepMs: Int = 40): Pcm {
        var start = 0
        var end = pcm.samples.size - 1
        while (start < pcm.samples.size && abs(pcm.samples[start].toInt()) < threshold) start++
        while (end > start && abs(pcm.samples[end].toInt()) < threshold) end--
        if (end <= start) return pcm
        val pad = (pcm.sampleRate * keepMs / 1000)
        start = max(0, start - pad)
        end = minOf(pcm.samples.size - 1, end + pad)
        return pcm.copy(samples = pcm.samples.copyOfRange(start, end + 1))
    }

    /** Simple linear-interpolation resampler — plenty for speech at 1x/1.5x. */
    fun resample(pcm: Pcm, newRate: Int): Pcm {
        if (newRate == pcm.sampleRate || pcm.frames == 0) return pcm
        val ratio = newRate.toDouble() / pcm.sampleRate
        val outN = (pcm.frames * ratio).roundToIntString().coerceAtLeast(1)
        val out = ShortArray(outN)
        for (i in 0 until outN) {
            val src = i / ratio
            val i0 = src.toInt().coerceIn(0, pcm.frames - 1)
            val i1 = (i0 + 1).coerceAtMost(pcm.frames - 1)
            val frac = src - i0
            val a = pcm.samples[i0].toDouble()
            val b = pcm.samples[i1].toDouble()
            out[i] = (a + (b - a) * frac).roundToIntString().coerceIn(-32768, 32767).toShort()
        }
        return Pcm(newRate, 1, out)
    }

    /** Down-sampled absolute envelope used by the waveform view and player glow. */
    fun envelope(pcm: Pcm, buckets: Int = 96): FloatArray {
        val n = buckets.coerceAtLeast(1)
        val out = FloatArray(n)
        if (pcm.samples.isEmpty()) return out
        val step = (pcm.samples.size / n).coerceAtLeast(1)
        var globalPeak = 1
        for (b in 0 until n) {
            var peak = 0
            val start = b * step
            val end = minOf(pcm.samples.size, start + step)
            for (i in start until end) {
                val v = abs(pcm.samples[i].toInt())
                if (v > peak) peak = v
            }
            out[b] = peak.toFloat()
            if (peak > globalPeak) globalPeak = peak
        }
        for (b in 0 until n) out[b] = (out[b] / globalPeak).coerceIn(0f, 1f)
        return out
    }
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun Double.roundToIntString(): Int = if (this >= 0) (this + 0.5).toInt() else (this - 0.5).toInt()
