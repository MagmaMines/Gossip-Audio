package xyz.beyoneer.gossipaudio.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import xyz.beyoneer.gossipaudio.audio.Exporter
import xyz.beyoneer.gossipaudio.audio.WavReader
import xyz.beyoneer.gossipaudio.core.TextKit
import xyz.beyoneer.gossipaudio.core.VoiceCatalog
import xyz.beyoneer.gossipaudio.data.AudioFormat
import xyz.beyoneer.gossipaudio.data.Clip
import xyz.beyoneer.gossipaudio.notify.Notifier
import xyz.beyoneer.gossipaudio.ui.theme.Ui
import xyz.beyoneer.gossipaudio.ui.widgets.BrandButton
import xyz.beyoneer.gossipaudio.ui.widgets.Chip
import xyz.beyoneer.gossipaudio.ui.widgets.IconView
import xyz.beyoneer.gossipaudio.ui.widgets.Icons
import xyz.beyoneer.gossipaudio.ui.widgets.NeonSlider
import java.io.File

/**
 * Export sheet: WAV / MP3 / M4A, quality, optional cover art, download to
 * Downloads/GossipAudio, then share the finished file.
 */
object ExportFlow {

    fun open(activity: MainActivity, clip: Clip, startFormat: AudioFormat = AudioFormat.from(activity.prefs.defaultFormat)) {
        val palette = activity.palette
        val store = activity.app.store
        var format = startFormat
        var bitrate = activity.prefs.defaultBitrate
        var coverFile: File? = store.coverFor(clip)
        var customCover = false
        var busy = false

        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

        val preview = FrameLayout(activity)
        val coverImage = ImageView(activity).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        coverImage.setImageBitmap(xyz.beyoneer.gossipaudio.util.Artwork.loadScaled(
            File(activity.filesDir, "covers/${clip.coverName ?: ""}"), 220
        ))
        val rounded = Ui.rounded(palette.yellowDeep, Ui.dpf(activity, 20f), palette.yellowDeep, Ui.dp(activity, 2f))
        preview.background = rounded
        preview.addView(coverImage, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        content.addView(preview, LinearLayout.LayoutParams(Ui.dp(activity, 132f), Ui.dp(activity, 132f)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = Ui.dp(activity, 6f)
        })

        val titleView = Ui.text(activity, clip.title, 15f, palette.text, bold = true, gravity = Gravity.CENTER)
        titleView.maxLines = 2
        content.addView(titleView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        val meta = Ui.text(
            activity,
            "${VoiceCatalog.byId(clip.voiceId).name} \u00B7 ${TextKit.formatDuration(clip.durationMs)} \u00B7 ${TextKit.formatBytes(clip.sizeBytes)}",
            11.5f, palette.textDim, gravity = Gravity.CENTER, font = "sans-serif-medium"
        )
        content.addView(meta, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 4f) })

        val formatRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val chips = ArrayList<Pair<AudioFormat, Chip>>()
        AudioFormat.values().forEach { f ->
            val chip = Chip(activity, palette, f.label, f == format)
            chip.setOnClickListener {
                format = f
                chips.forEach { (fmt, c) -> c.setSelectedState(fmt == f) }
                activity.prefs.defaultFormat = f.name
            }
            chips.add(f to chip)
            formatRow.addView(chip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        content.addView(formatRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 14f) })

        val qualityLabel = Ui.text(activity, "Quality  $bitrate kbps", 11.5f, palette.textDim, bold = true, font = "sans-serif-medium")
        content.addView(qualityLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 10f) })
        val quality = NeonSlider(activity, palette, 64f, 320f, bitrate.toFloat(), 8f)
        quality.onChanged = {
            bitrate = it.toInt()
            qualityLabel.text = "Quality  $bitrate kbps"
            activity.prefs.defaultBitrate = bitrate
        }
        content.addView(quality)

        val coverRow = Ui.row(activity)
        val coverLabel = Ui.text(
            activity,
            if (customCover) "Custom cover attached" else "Auto brand cover",
            11.5f, palette.textDim, font = "sans-serif-medium"
        )
        coverRow.addView(coverLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val pick = BrandButton(activity, palette, "Pick image", BrandButton.Variant.GHOST, Icons.Kind.EDIT, compact = true)
        coverRow.addView(pick)
        content.addView(coverRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 8f) })

        val note = Ui.text(
            activity,
            "Saves to Downloads/GossipAudio. MP3 and M4A include your cover art as embedded metadata.",
            10.5f, palette.textDim, font = "sans-serif"
        )
        note.setLineSpacing(Ui.dpf(activity, 2f), 1f)
        content.addView(note, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 8f) })

        val download = BrandButton(activity, palette, "Download ${format.label}", BrandButton.Variant.PRIMARY, Icons.Kind.DOWNLOAD)
        content.addView(download, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 54f)
        ).apply { topMargin = Ui.dp(activity, 14f) })

        val handle = Overlays.sheet(activity, "Download \u00B7 ${clip.title}", content)
        val handler = Handler(Looper.getMainLooper())

        pick.setOnClick {
            activity.pickImage { uri ->
                if (uri == null) return@pickImage
                val file = activity.copyUriToCache(uri, "cover-${clip.id}.jpg")
                if (file != null) {
                    coverFile = file
                    customCover = true
                    coverLabel.text = "Custom cover attached"
                    val bmp: Bitmap? = xyz.beyoneer.gossipaudio.util.Artwork.loadScaled(file, 220)
                    if (bmp != null) {
                        coverImage.setImageBitmap(bmp)
                        Ui.popIn(preview)
                    }
                    activity.toast("Cover attached \uD83D\uDDBC\uFE0F")
                } else {
                    activity.toast("That image could not be read.")
                }
            }
        }

        download.setOnClick {
            if (busy) return@setOnClick
            busy = true
            download.setLoading(true)
            download.setText("Encoding\u2026")
            Thread {
                val master = store.fileFor(clip)
                val pcm = WavReader.read(master)
                if (pcm == null) {
                    handler.post {
                        busy = false
                        download.setLoading(false)
                        activity.toast("Master audio is missing or unreadable.")
                    }
                    return@Thread
                }
                val voice = VoiceCatalog.byId(clip.voiceId)
                val style = xyz.beyoneer.gossipaudio.core.StyleCatalog.byId(clip.styleId)
                val output = Exporter.build(
                    activity,
                    pcm,
                    Exporter.Options(
                        format = format,
                        bitrateKbps = bitrate,
                        coverFile = coverFile,
                        title = clip.title,
                        voiceLabel = voice.name,
                        styleLabel = style.label
                    )
                )
                handler.post {
                    busy = false
                    download.setLoading(false)
                    if (output == null) {
                        activity.toast("Export failed \u2014 try another format.")
                        return@post
                    }
                    activity.requestLegacyStorage { granted ->
                        if (!granted) {
                            activity.toast("Storage permission is required on Android 9 and below.")
                            return@requestLegacyStorage
                        }
                        Thread {
                            val uri = Exporter.publish(activity, output)
                            handler.post {
                                if (uri == null) {
                                    activity.toast("Could not write to Downloads. File kept in app storage.")
                                } else {
                                    clip.lastExportUri = uri.toString()
                                    store.update { if (it.id == clip.id) it.lastExportUri = uri.toString() }
                                    Notifier.exported(activity, output.displayName, uri.toString())
                                    activity.toast("Saved \u2022 Downloads/GossipAudio/${output.displayName}")
                                    shareSheet(activity, uri, output.displayName)
                                }
                                handle.dismiss()
                            }
                        }.start()
                    }
                }
            }.start()
        }
    }

    fun shareSheet(activity: MainActivity, uri: android.net.Uri, name: String) {
        try {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "GossipAudio \u00B7 $name")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Made with GossipAudio by Beyoneer XYZ. Btw we're not gossiping inreal\u2026"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(share, "Share your gossip"))
        } catch (t: Throwable) {
            activity.toast("No app available to share this file.")
        }
    }

    /** One-tap: export as MP3, publish, open the share sheet. */
    fun quickShare(activity: MainActivity, clip: Clip) {
        activity.toast("Encoding MP3 for sharing\u2026")
        val store = activity.app.store
        val voice = VoiceCatalog.byId(clip.voiceId)
        val style = xyz.beyoneer.gossipaudio.core.StyleCatalog.byId(clip.styleId)
        Thread {
            val pcm = WavReader.read(store.fileFor(clip))
            if (pcm == null) {
                activity.runOnUiThread { activity.toast("Could not read the master audio.") }
                return@Thread
            }
            val output = Exporter.build(
                activity,
                pcm,
                Exporter.Options(
                    format = AudioFormat.MP3,
                    bitrateKbps = activity.prefs.defaultBitrate,
                    coverFile = store.coverFor(clip),
                    title = clip.title,
                    voiceLabel = voice.name,
                    styleLabel = style.label
                )
            )
            if (output == null) {
                activity.runOnUiThread { activity.toast("Export failed.") }
                return@Thread
            }
            val uri = Exporter.publish(activity, output)
            activity.runOnUiThread {
                if (uri == null) {
                    activity.toast("Could not publish the file.")
                } else {
                    Notifier.exported(activity, output.displayName, uri.toString())
                    shareSheet(activity, uri, output.displayName)
                }
            }
        }.start()
    }
}
