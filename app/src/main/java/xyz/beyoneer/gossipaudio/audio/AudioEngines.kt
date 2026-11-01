package xyz.beyoneer.gossipaudio.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import xyz.beyoneer.gossipaudio.core.AudioFx
import xyz.beyoneer.gossipaudio.core.Pcm
import xyz.beyoneer.gossipaudio.core.Wav
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Wraps Android's hardware/software AAC encoder + MediaMuxer to produce a real
 * .m4a file. This is the "audio image" container: artwork and tags are added
 * afterwards by [xyz.beyoneer.gossipaudio.core.Mp4Tagger].
 */
object AacEncoder {

    private val SUPPORTED = intArrayOf(8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000)

    fun encodeToM4a(pcmIn: Pcm, outFile: File, bitrateKbps: Int = 128): Boolean {
        var pcm = pcmIn.mono()
        val rate = nearest(pcm.sampleRate)
        if (rate != pcm.sampleRate) pcm = AudioFx.resample(pcm, rate)
        return try {
            if (runEncode(pcm, outFile, bitrateKbps)) true else {
                outFile.delete()
                false
            }
        } catch (_: Throwable) {
            // Some encoders dislike 22.05 kHz; retry once at 44.1 kHz.
            try {
                val up = AudioFx.resample(pcm, 44100)
                runEncode(up, outFile, bitrateKbps)
            } catch (t: Throwable) {
                outFile.delete()
                false
            }
        }
    }

    private fun runEncode(pcm: Pcm, outFile: File, bitrateKbps: Int): Boolean {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, pcm.sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, (bitrateKbps * 1000).coerceIn(32000, 320000))
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65536)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        var muxer: MediaMuxer? = null
        var track = -1
        var muxerStarted = false
        var inputDone = false
        var outputDone = false
        var bytePos = 0
        var guard = 0
        val info = MediaCodec.BufferInfo()
        val pcmBytes = toBytes(pcm.samples)
        try {
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            while (!outputDone && guard++ < 200_000) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buf = codec.getInputBuffer(inIndex)
                        if (buf != null) {
                            buf.clear()
                            var chunk = minOf(buf.capacity(), pcmBytes.size - bytePos)
                            chunk -= chunk % 2
                            if (chunk > 0) {
                                buf.put(pcmBytes, bytePos, chunk)
                                codec.queueInputBuffer(inIndex, 0, chunk, timeUs(bytePos, pcm.sampleRate), 0)
                                bytePos += chunk
                            } else {
                                codec.queueInputBuffer(
                                    inIndex, 0, 0, timeUs(bytePos, pcm.sampleRate),
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            }
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (inputDone && muxerStarted) {
                            // keep spinning until EOS arrives
                        }
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIndex)
                        if (outBuf != null && info.size > 0 && muxerStarted) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, outBuf, info)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    }
                }
                if (inputDone && muxerStarted && info.size == 0 && outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // prevent a rare livelock on buggy encoders
                    if (guard > 4000) break
                }
            }
        } finally {
            try {
                codec.stop()
            } catch (_: Throwable) {
            }
            codec.release()
            if (muxer != null) {
                try {
                    if (muxerStarted) muxer.stop()
                } catch (_: Throwable) {
                }
                muxer.release()
            }
        }
        // A nonempty MP4 is not proof of a complete encode. Never report a
        // truncated file as a successful download if EOS was not observed.
        return outputDone && muxerStarted && outFile.exists() && outFile.length() > 1024
    }

    private fun nearest(rate: Int): Int {
        var best = SUPPORTED[0]
        var diff = Int.MAX_VALUE
        for (r in SUPPORTED) {
            val d = kotlin.math.abs(r - rate)
            if (d < diff) {
                diff = d
                best = r
            }
        }
        return best
    }

    private fun timeUs(bytePos: Int, sampleRate: Int): Long =
        (bytePos / 2).toLong() * 1_000_000L / sampleRate

    private fun toBytes(samples: ShortArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        var p = 0
        for (s in samples) {
            out[p++] = (s.toInt() and 0xFF).toByte()
            out[p++] = ((s.toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }
}

/** Reads the PCM out of a WAV file (used by the player, exporter and share sheet). */
object WavReader {
    fun read(file: File): Pcm? = try {
        Wav.read(file.readBytes())
    } catch (_: Throwable) {
        null
    }

    fun readBytes(bytes: ByteArray): Pcm? = Wav.read(bytes)

    fun writePcm(pcm: Pcm): ByteArray = Wav.write(pcm)

    fun silent(ms: Int, sampleRate: Int = 22050): Pcm =
        Pcm(sampleRate, 1, ShortArray(ms * sampleRate / 1000))

    fun decodeToString(pcm: Pcm): ByteArray = Wav.write(pcm)

    @Suppress("unused")
    private fun out(): ByteArrayOutputStream = ByteArrayOutputStream()
}
