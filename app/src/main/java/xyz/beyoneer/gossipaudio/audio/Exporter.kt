package xyz.beyoneer.gossipaudio.audio

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import xyz.beyoneer.gossipaudio.core.AudioFx
import xyz.beyoneer.gossipaudio.core.Id3
import xyz.beyoneer.gossipaudio.core.Mp3Encoder
import xyz.beyoneer.gossipaudio.core.Mp4Tagger
import xyz.beyoneer.gossipaudio.core.Pcm
import xyz.beyoneer.gossipaudio.core.Wav
import xyz.beyoneer.gossipaudio.data.AudioFormat
import xyz.beyoneer.gossipaudio.util.Artwork
import xyz.beyoneer.gossipaudio.core.TextKit
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Turns a generated clip into a downloadable file:
 *   WAV  — lossless PCM, zero dependencies
 *   MP3  — LAME (vendored pure Java) + ID3v2.3 tags + APIC cover
 *   M4A  — Android AAC encoder in an MP4 container + iTunes cover atom
 */
object Exporter {

    data class Options(
        val format: AudioFormat,
        val bitrateKbps: Int = 128,
        val coverFile: File? = null,
        val title: String,
        val artist: String = "GossipAudio \u00B7 Beyoneer XYZ",
        val album: String = "GossipAudio",
        val comment: String = "Made with GossipAudio. Btw we're not gossiping inreal.",
        val voiceLabel: String = "",
        val styleLabel: String = ""
    )

    data class Output(val file: File, val mime: String, val displayName: String)

    fun build(
        context: Context,
        pcm: Pcm,
        options: Options,
        progress: (Int) -> Unit = {}
    ): Output? {
        return try {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val base = TextKit.safeFileName(options.title, "gossip") + "-" + stamp
            val dir = File(context.filesDir, "exports").apply { mkdirs() }
            val cover = resolveCover(context, options, pcm)
            progress(5)
            val out = when (options.format) {
                AudioFormat.WAV -> {
                    val file = File(dir, "$base.wav")
                    file.writeBytes(Wav.write(pcm))
                    Output(file, AudioFormat.WAV.mime, "$base.wav")
                }
                AudioFormat.MP3 -> {
                    progress(15)
                    val mp3 = Mp3Encoder.encode(pcm, options.bitrateKbps)
                    progress(75)
                    val tagged = Id3.attachToMp3(
                        mp3,
                        Id3.Tags(
                            title = options.title,
                            artist = options.artist,
                            album = options.album,
                            comment = options.comment,
                            year = SimpleDateFormat("yyyy", Locale.US).format(Date()),
                            cover = cover?.readBytes(),
                            coverMime = "image/jpeg"
                        )
                    )
                    val file = File(dir, "$base.mp3")
                    file.writeBytes(tagged)
                    Output(file, AudioFormat.MP3.mime, "$base.mp3")
                }
                AudioFormat.M4A -> {
                    progress(15)
                    val file = File(dir, "$base.m4a")
                    val ok = AacEncoder.encodeToM4a(pcm, file, options.bitrateKbps)
                    if (!ok) {
                        file.delete()
                        return null
                    } else {
                        progress(80)
                        Mp4Tagger.tagFile(
                            file,
                            Mp4Tagger.Meta(
                                title = options.title,
                                artist = options.artist,
                                album = options.album,
                                comment = options.comment,
                                genre = "Speech",
                                year = SimpleDateFormat("yyyy", Locale.US).format(Date()),
                                coverJpeg = cover?.readBytes()
                            )
                        )
                        Output(file, AudioFormat.M4A.mime, "$base.m4a")
                    }
                }
            }
            progress(100)
            out
        } catch (_: Throwable) {
            null
        }
    }

    private fun resolveCover(context: Context, options: Options, pcm: Pcm): File? {
        val dir = File(context.filesDir, "covers").apply { mkdirs() }
        val target = File(dir, "cover-" + System.currentTimeMillis() + ".jpg")
        val user = options.coverFile
        if (user != null && user.exists()) {
            if (Artwork.copyAsCover(context, user, target)) return target
        }
        val generated = Artwork.generateBrandCover(
            context,
            options.title,
            options.voiceLabel,
            options.styleLabel,
            target
        )
        return if (generated) target else null
    }

    /** Publishes the file to the public Downloads folder and returns a shareable URI. */
    fun publish(context: Context, output: Output, folder: String = "GossipAudio"): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, output.displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, output.mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$folder")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = context.contentResolver.insert(collection, values) ?: return null
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    output.file.inputStream().use { it.copyTo(out) }
                }
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                context.contentResolver.update(uri, done, null, null)
                uri
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    folder
                )
                dir.mkdirs()
                val dest = File(dir, output.displayName)
                output.file.copyTo(dest, overwrite = true)
                MediaScannerConnection.scanFile(
                    context, arrayOf(dest.absolutePath), arrayOf(output.mime), null
                )
                Uri.fromFile(dest)
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** Writes the WAV master used by the library (always lossless, always playable). */
    fun writeMaster(context: Context, pcm: Pcm, id: String): File {
        val dir = File(context.filesDir, "clips").apply { mkdirs() }
        val file = File(dir, "$id.wav")
        file.writeBytes(Wav.write(AudioFx.fade(AudioFx.normalize(pcm, -1.5), 8, 40)))
        return file
    }

    fun formatStorageLabel(format: AudioFormat, bitrate: Int): String = when (format) {
        AudioFormat.WAV -> "WAV \u00B7 16-bit PCM"
        AudioFormat.MP3 -> "MP3 \u00B7 $bitrate kbps"
        AudioFormat.M4A -> "M4A \u00B7 $bitrate kbps"
    }
}
