package xyz.beyoneer.gossipaudio.ui.screens

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import xyz.beyoneer.gossipaudio.audio.PlaybackHub
import xyz.beyoneer.gossipaudio.audio.WavReader
import xyz.beyoneer.gossipaudio.core.AudioFx
import xyz.beyoneer.gossipaudio.core.StyleCatalog
import xyz.beyoneer.gossipaudio.core.TextKit
import xyz.beyoneer.gossipaudio.core.VoiceCatalog
import xyz.beyoneer.gossipaudio.data.Clip
import xyz.beyoneer.gossipaudio.ui.ClipActions
import xyz.beyoneer.gossipaudio.ui.ExportFlow
import xyz.beyoneer.gossipaudio.ui.MainActivity
import xyz.beyoneer.gossipaudio.ui.Overlays
import xyz.beyoneer.gossipaudio.ui.Screen
import xyz.beyoneer.gossipaudio.ui.theme.Ui
import xyz.beyoneer.gossipaudio.ui.widgets.AvatarView
import xyz.beyoneer.gossipaudio.ui.widgets.BrandButton
import xyz.beyoneer.gossipaudio.ui.widgets.Chip
import xyz.beyoneer.gossipaudio.ui.widgets.GlowBackdrop
import xyz.beyoneer.gossipaudio.ui.widgets.IconView
import xyz.beyoneer.gossipaudio.ui.widgets.Icons
import xyz.beyoneer.gossipaudio.ui.widgets.VinylView
import xyz.beyoneer.gossipaudio.ui.widgets.WaveformView
import xyz.beyoneer.gossipaudio.util.Artwork
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Full-screen player: spinning vinyl, live waveform, all the controls. */
class PlayerScreen(activity: MainActivity, private val clipId: String) : Screen(activity) {

    private var clip: Clip? = null
    private lateinit var vinyl: VinylView
    private lateinit var waveform: WaveformView
    private lateinit var playButton: FrameLayout
    private lateinit var playIcon: IconView
    private lateinit var elapsed: TextView
    private lateinit var total: TextView
    private lateinit var titleView: TextView
    private lateinit var metaView: TextView
    private var peaks: FloatArray = FloatArray(0)
    private var speedChips: MutableList<Pair<Float, Chip>> = ArrayList()

    private val hubListener: (PlaybackHub.State) -> Unit = { state -> onState(state) }

    override fun onCreateView(): View {
        clip = activity.app.store.byId(clipId)
        val root = FrameLayout(activity)
        root.addView(GlowBackdrop(activity, palette), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(activity, 18f), Ui.dp(activity, 6f), Ui.dp(activity, 18f), Ui.dp(activity, 30f))
        }
        scroll.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // header
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = FrameLayout(activity).apply {
            background = Ui.rounded(palette.card, Ui.dpf(activity, 100f), palette.outline, Ui.dp(activity, 1f))
        }
        back.addView(IconView(activity, Icons.Kind.BACK, palette.text, 20f), FrameLayout.LayoutParams(
            Ui.dp(activity, 20f), Ui.dp(activity, 20f), Gravity.CENTER
        ))
        back.layoutParams = LinearLayout.LayoutParams(Ui.dp(activity, 42f), Ui.dp(activity, 42f))
        Ui.pressable(back, 0.9f) { activity.router.pop() }
        header.addView(back)
        val headerText = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(activity, 12f), 0, 0, 0)
        }
        headerText.addView(Ui.text(activity, "NOW PLAYING", 9.5f, palette.yellowDeep, bold = true))
        headerText.addView(Ui.text(activity, "The tea, live", 13.5f, palette.text, bold = true, font = "sans-serif-medium"))
        header.addView(headerText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val more = FrameLayout(activity).apply {
            background = Ui.rounded(palette.card, Ui.dpf(activity, 100f), palette.outline, Ui.dp(activity, 1f))
        }
        more.addView(IconView(activity, Icons.Kind.LIST, palette.text, 18f), FrameLayout.LayoutParams(
            Ui.dp(activity, 18f), Ui.dp(activity, 18f), Gravity.CENTER
        ))
        more.layoutParams = LinearLayout.LayoutParams(Ui.dp(activity, 42f), Ui.dp(activity, 42f))
        Ui.pressable(more, 0.9f) {
            clip?.let { ClipActions.show(activity, it) { refreshFromStore() } }
        }
        header.addView(more)
        column.addView(header)

        // vinyl
        vinyl = VinylView(activity, palette, 250f).apply {
            reduceMotion = activity.prefs.reduceMotion
        }
        val vinylWrap = FrameLayout(activity)
        vinylWrap.addView(vinyl, FrameLayout.LayoutParams(Ui.dp(activity, 250f), Ui.dp(activity, 250f), Gravity.CENTER))
        column.addView(vinylWrap, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 260f)
        ).apply { topMargin = Ui.dp(activity, 6f) })

        titleView = Ui.text(activity, "", 21f, palette.text, bold = true, gravity = Gravity.CENTER)
        titleView.maxLines = 2
        column.addView(titleView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 6f) })

        metaView = Ui.text(activity, "", 11.5f, palette.textDim, gravity = Gravity.CENTER, font = "sans-serif-medium")
        column.addView(metaView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 4f) })

        waveform = WaveformView(activity, palette)
        column.addView(waveform, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 76f)
        ).apply { topMargin = Ui.dp(activity, 12f) })
        waveform.onSeek = { fraction ->
            val duration = PlaybackHub.state.durationMs
            if (duration > 0) PlaybackHub.seekTo((duration * fraction).toLong())
        }

        val times = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        elapsed = Ui.text(activity, "0:00", 10.5f, palette.textDim, bold = true, font = "sans-serif-medium")
        total = Ui.text(activity, "0:00", 10.5f, palette.textDim, bold = true, font = "sans-serif-medium", gravity = Gravity.END)
        times.addView(elapsed, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        times.addView(total, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        column.addView(times)

        // transport
        val transport = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        transport.addView(roundControl(Icons.Kind.PREV, 44f) { PlaybackHub.previous() }, LinearLayout.LayoutParams(
            Ui.dp(activity, 44f), Ui.dp(activity, 44f)
        ))
        transport.addView(Ui.space(activity, Ui.dp(activity, 6f)), LinearLayout.LayoutParams(0, 1, 0.35f))
        transport.addView(roundControl(Icons.Kind.REPLAY, 46f) { PlaybackHub.replay() }, LinearLayout.LayoutParams(
            Ui.dp(activity, 46f), Ui.dp(activity, 46f)
        ))
        transport.addView(Ui.space(activity, Ui.dp(activity, 4f)), LinearLayout.LayoutParams(0, 1, 0.2f))
        playButton = FrameLayout(activity).apply {
            background = Ui.gradient(
                intArrayOf(palette.yellowBright, palette.yellow), Ui.dpf(activity, 200f),
                strokeColor = palette.yellowDeep, strokeWidth = Ui.dp(activity, 2.5f)
            )
            elevation = Ui.dpf(activity, 8f)
        }
        playIcon = IconView(activity, Icons.Kind.PLAY, palette.onYellow, 32f)
        playButton.addView(playIcon, FrameLayout.LayoutParams(
            Ui.dp(activity, 36f), Ui.dp(activity, 36f), Gravity.CENTER
        ))
        transport.addView(playButton, LinearLayout.LayoutParams(Ui.dp(activity, 78f), Ui.dp(activity, 78f)))
        Ui.pressable(playButton, 0.92f) {
            if (PlaybackHub.state.clipId != clipId) {
                startPlayback()
            } else {
                PlaybackHub.toggle()
            }
        }
        transport.addView(Ui.space(activity, Ui.dp(activity, 4f)), LinearLayout.LayoutParams(0, 1, 0.2f))
        transport.addView(roundControl(Icons.Kind.STOP, 46f) { PlaybackHub.stop() }, LinearLayout.LayoutParams(
            Ui.dp(activity, 46f), Ui.dp(activity, 46f)
        ))
        transport.addView(Ui.space(activity, Ui.dp(activity, 6f)), LinearLayout.LayoutParams(0, 1, 0.35f))
        transport.addView(roundControl(Icons.Kind.NEXT, 44f) { PlaybackHub.next() }, LinearLayout.LayoutParams(
            Ui.dp(activity, 44f), Ui.dp(activity, 44f)
        ))
        column.addView(transport, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 8f) })

        // quick actions
        val quick = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        quick.addView(actionPill(Icons.Kind.HEART, "Like") {
            clip?.let {
                activity.app.store.toggleLike(it.id)
                refreshFromStore()
                activity.toast(if (it.liked) "Unliked" else "Liked \u2764\uFE0F")
            }
        })
        quick.addView(Ui.space(activity, Ui.dp(activity, 8f)))
        quick.addView(actionPill(Icons.Kind.DOWNLOAD, "Download") {
            clip?.let { ExportFlow.open(activity, it) }
        })
        quick.addView(Ui.space(activity, Ui.dp(activity, 8f)))
        quick.addView(actionPill(Icons.Kind.SHARE, "Share") {
            clip?.let { ExportFlow.quickShare(activity, it) }
        })
        quick.addView(Ui.space(activity, Ui.dp(activity, 8f)))
        quick.addView(actionPill(Icons.Kind.TIMER, "Sleep") { showSleepTimer() })
        column.addView(quick, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 18f) })

        // speed
        column.addView(
            Ui.text(activity, "Speed", 11f, palette.textDim, bold = true, font = "sans-serif-medium", gravity = Gravity.CENTER),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(activity, 16f)
            }
        )
        val speedRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
            val label = if (speed == 1f) "1x" else "${speed}x"
            val chip = Chip(activity, palette, label, speed == PlaybackHub.state.speed)
            chip.setOnClickListener {
                PlaybackHub.setSpeed(speed)
                speedChips.forEach { (value, c) -> c.setSelectedState(value == speed) }
            }
            speedChips.add(speed to chip)
            speedRow.addView(chip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = Ui.dp(activity, 6f) })
        }
        column.addView(speedRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 8f) })

        column.addView(infoCard(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 18f) })

        root.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        return root
    }

    private fun infoCard(): View {
        val card = Ui.card(activity, palette, 22f)
        val clip = clip ?: return card
        val voice = VoiceCatalog.byId(clip.voiceId)
        val style = StyleCatalog.byId(clip.styleId)
        val rows = listOf(
            "Voice" to "${voice.flag} ${voice.name} (${voice.genderLabel}, ${voice.region})",
            "Style" to "${style.emoji} ${style.label}",
            "Engine" to TextKit.engineLabel(clip.engine),
            "Format" to "WAV master \u00B7 ${clip.sampleRate} Hz",
            "Duration" to TextKit.formatDuration(clip.durationMs),
            "Size" to TextKit.formatBytes(clip.sizeBytes),
            "Created" to SimpleDateFormat("dd MMM yyyy \u00B7 HH:mm", Locale.getDefault()).format(Date(clip.createdAt))
        )
        rows.forEachIndexed { index, row ->
            val line = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, Ui.dp(activity, 5f), 0, Ui.dp(activity, 5f))
            }
            line.addView(Ui.text(activity, row.first, 11.5f, palette.textDim, font = "sans-serif-medium"), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ))
            line.addView(
                Ui.text(activity, row.second, 11.5f, palette.text, bold = true, font = "sans-serif-medium", gravity = Gravity.END),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.7f)
            )
            card.addView(line)
            if (index < rows.size - 1) {
                val divider = View(activity).apply { setBackgroundColor(Ui.withAlpha(palette.outline, 0.6f)) }
                card.addView(divider, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1
                ))
            }
        }
        return card
    }

    private fun roundControl(icon: Icons.Kind, sizeDp: Float, action: () -> Unit): View {
        val box = FrameLayout(activity).apply {
            background = Ui.rounded(palette.card, Ui.dpf(activity, 200f), palette.outline, Ui.dp(activity, 1.2f))
        }
        box.addView(IconView(activity, icon, palette.text, sizeDp * 0.42f), FrameLayout.LayoutParams(
            Ui.dp(activity, sizeDp * 0.45f), Ui.dp(activity, sizeDp * 0.45f), Gravity.CENTER
        ))
        Ui.pressable(box, 0.9f, action)
        return box
    }

    private fun actionPill(icon: Icons.Kind, label: String, action: () -> Unit): View {
        val pill = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(Ui.dp(activity, 12f), Ui.dp(activity, 8f), Ui.dp(activity, 12f), Ui.dp(activity, 8f))
            background = Ui.rounded(palette.card, Ui.dpf(activity, 18f), palette.outline, Ui.dp(activity, 1f))
        }
        pill.addView(IconView(activity, icon, palette.text, 18f), LinearLayout.LayoutParams(
            Ui.dp(activity, 18f), Ui.dp(activity, 18f)
        ))
        val tv = Ui.text(activity, label, 9.5f, palette.textDim, bold = true, font = "sans-serif-medium", gravity = Gravity.CENTER)
        tv.setPadding(0, Ui.dp(activity, 4f), 0, 0)
        pill.addView(tv)
        Ui.pressable(pill, 0.92f, action)
        return pill
    }

    private fun showSleepTimer() {
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val options = listOf(0 to "Off", 5 to "5 minutes", 15 to "15 minutes", 30 to "30 minutes", 60 to "1 hour")
        options.forEach { (minutes, label) ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(activity, 10f), Ui.dp(activity, 13f), Ui.dp(activity, 10f), Ui.dp(activity, 13f))
                background = Ui.rounded(Ui.withAlpha(palette.text, 0.04f), Ui.dpf(activity, 14f))
            }
            row.addView(IconView(activity, Icons.Kind.TIMER, palette.text, 17f), LinearLayout.LayoutParams(
                Ui.dp(activity, 30f), Ui.dp(activity, 18f)
            ))
            row.addView(Ui.text(activity, label, 14f, palette.text, bold = true, font = "sans-serif-medium"))
            Ui.pressable(row, 0.99f) {
                PlaybackHub.setSleepTimer(minutes)
                Overlays.dismissTop(activity)
                activity.toast(if (minutes == 0) "Sleep timer off" else "Sleeping in $minutes minutes \uD83D\uDE34")
            }
            content.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = Ui.dp(activity, 6f) })
        }
        Overlays.sheet(activity, "Sleep timer", content)
    }

    override fun onShown() {
        PlaybackHub.addListener(hubListener)
        val clip = clip ?: run {
            activity.toast("That clip no longer exists.")
            activity.router.pop()
            return
        }
        if (PlaybackHub.state.clipId != clip.id) {
            startPlayback()
        }
        loadArtwork()
        loadPeaks()
        onState(PlaybackHub.state)
    }

    override fun onHidden() {
        PlaybackHub.removeListener(hubListener)
    }

    override fun onDestroyed() {
        PlaybackHub.removeListener(hubListener)
    }

    private fun startPlayback() {
        val clip = clip ?: return
        val file = activity.app.store.fileFor(clip)
        if (!file.exists()) {
            activity.toast("Audio file missing for this clip.")
            return
        }
        PlaybackHub.playFile(file, clip.id, clip.title, activity.app.store.coverFor(clip))
        if (activity.prefs.notifications) activity.requestNotifications()
        loadPeaks()
    }

    private fun loadArtwork() {
        val coverFile = clip?.let { activity.app.store.coverFor(it) }
        val bitmap = if (coverFile != null) Artwork.loadScaled(coverFile, 400) else null
        vinyl.setArtwork(bitmap)
    }

    private fun loadPeaks() {
        val clip = clip ?: return
        Thread {
            val pcm = WavReader.read(activity.app.store.fileFor(clip))
            if (pcm != null) {
                val envelope = AudioFx.envelope(pcm, 64)
                activity.runOnUiThread {
                    peaks = envelope
                    waveform.setPeaks(envelope)
                }
            }
        }.start()
    }

    private fun refreshFromStore() {
        val updated = activity.app.store.byId(clipId) ?: return
        clip = updated
        titleView.text = updated.title
    }

    private fun onState(state: PlaybackHub.State) {
        if (!::titleView.isInitialized) return
        val clip = clip ?: return
        titleView.text = clip.title
        metaView.text = clip.let {
            val voice = VoiceCatalog.byId(it.voiceId)
            "${voice.flag} ${voice.name} \u00B7 ${TextKit.formatDuration(it.durationMs)} \u00B7 ${it.sampleRate / 1000} kHz"
        }
        val active = state.clipId == clip.id
        val playing = active && state.playing
        playIcon.kind = if (playing) Icons.Kind.PAUSE else Icons.Kind.PLAY
        playIcon.invalidate()
        vinyl.playing = playing
        waveform.playing = playing
        if (active) {
            val duration = if (state.durationMs > 0) state.durationMs else clip.durationMs
            total.text = TextKit.formatDuration(duration)
            elapsed.text = TextKit.formatDuration(state.positionMs)
            if (duration > 0) waveform.setProgressFraction(state.positionMs.toFloat() / duration)
            speedChips.forEach { (value, chip) -> chip.setSelectedState(value == state.speed) }
        } else {
            elapsed.text = "0:00"
            total.text = TextKit.formatDuration(clip.durationMs)
            waveform.setProgressFraction(0f)
        }
    }

    @Suppress("unused")
    private fun avatar(): View {
        val voice = VoiceCatalog.byId(clip?.voiceId ?: "riya")
        return AvatarView(activity, palette, voice.name, voice.accent, 30f)
    }

    @Suppress("unused")
    private fun unusedButton(): View = BrandButton(activity, palette, "unused", BrandButton.Variant.GHOST)
}
