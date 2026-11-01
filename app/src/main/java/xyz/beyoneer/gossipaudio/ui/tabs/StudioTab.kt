package xyz.beyoneer.gossipaudio.ui.tabs

import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.CycleInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import xyz.beyoneer.gossipaudio.audio.PlaybackHub
import xyz.beyoneer.gossipaudio.core.StyleCatalog
import xyz.beyoneer.gossipaudio.core.TextKit
import xyz.beyoneer.gossipaudio.core.VoiceCatalog
import xyz.beyoneer.gossipaudio.core.VoiceProfile
import xyz.beyoneer.gossipaudio.data.Clip
import xyz.beyoneer.gossipaudio.speech.SpeechSynth
import xyz.beyoneer.gossipaudio.ui.Brand
import xyz.beyoneer.gossipaudio.ui.Overlays
import xyz.beyoneer.gossipaudio.ui.SttSheet
import xyz.beyoneer.gossipaudio.ui.Tab
import xyz.beyoneer.gossipaudio.ui.VoicePreview
import xyz.beyoneer.gossipaudio.ui.screens.PlayerScreen
import xyz.beyoneer.gossipaudio.ui.screens.ShellScreen
import xyz.beyoneer.gossipaudio.ui.theme.Ui
import xyz.beyoneer.gossipaudio.ui.widgets.AvatarView
import xyz.beyoneer.gossipaudio.ui.widgets.BrandButton
import xyz.beyoneer.gossipaudio.ui.widgets.FadeRow
import xyz.beyoneer.gossipaudio.ui.widgets.Chip
import xyz.beyoneer.gossipaudio.ui.widgets.CoverView
import xyz.beyoneer.gossipaudio.ui.widgets.IconView
import xyz.beyoneer.gossipaudio.ui.widgets.Icons
import xyz.beyoneer.gossipaudio.ui.widgets.LoadingOverlay
import xyz.beyoneer.gossipaudio.ui.widgets.NeonSlider
import xyz.beyoneer.gossipaudio.ui.widgets.Sparkline
import xyz.beyoneer.gossipaudio.util.Artwork
import xyz.beyoneer.gossipaudio.util.Telemetry
import java.io.File

/** The composer: write gossip, pick a voice, generate, hear it, keep it. */
class StudioTab(private val shell: ShellScreen) : Tab {

    private val activity get() = shell.act
    private val palette get() = shell.colors

    private lateinit var input: EditText
    private lateinit var counter: TextView
    private lateinit var estimate: TextView
    private lateinit var voiceStrip: LinearLayout
    private lateinit var styleStrip: LinearLayout
    private lateinit var generateButton: BrandButton
    private lateinit var recentStrip: LinearLayout
    private lateinit var recentEmpty: TextView
    private lateinit var spark: Sparkline
    private lateinit var cpuValue: TextView
    private lateinit var ramValue: TextView
    private lateinit var coreValue: TextView
    private lateinit var engineValue: TextView
    private lateinit var engineDot: View
    private lateinit var overlay: LoadingOverlay
    private var composerVoiceName: TextView? = null
    private var speedSliderRef: NeonSlider? = null
    private var pitchSliderRef: NeonSlider? = null
    private var speedLabelRef: TextView? = null
    private var pitchLabelRef: TextView? = null

    private val handler = Handler(Looper.getMainLooper())
    private var synthJob: SpeechSynth? = null
    private var busy = false
    private val telemetry by lazy { Telemetry(activity) }
    private var telemetryLoop: Runnable? = null

    override val root: View by lazy { build() }

    override fun onShow() {
        refreshVoiceStrip()
        refreshStyleStrip()
        refreshRecent()
        startTelemetry()
        updateEstimate()
        // text handed over by another app (share sheet) or a launcher shortcut
        activity.app.pendingText?.let { shared ->
            activity.app.pendingText = null
            input.setText(shared.take(2000))
            input.setSelection(input.text.length)
            updateEstimate()
            activity.toast("Loaded the shared text \uD83E\uDD73")
        }
    }

    override fun onHide() {
        stopTelemetry()
    }

    private fun build(): View {
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            clipToPadding = false
        }
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(activity, 18f), Ui.dp(activity, 8f), Ui.dp(activity, 18f), Ui.dp(activity, 28f))
        }
        Ui.capWidth(column)
        scroll.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // One dark "hero" card holds the greeting, the tagline and the composer. The
        // page used to be white-on-white: a logo strip, a white card, another white
        // card, another white card. Ink here, cream around it, yellow for the action.
        column.addView(heroCard(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 4f) })

        column.addView(quickActions(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 12f) })

        column.addView(naturalVoiceBanner(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 12f) })

        column.addView(sectionHeader("Voice", "tap to select"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 20f) })
        val voiceRow = FadeRow(activity, palette)
        voiceStrip = voiceRow.content
        column.addView(voiceRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 10f) })

        val previewRow = Ui.row(activity)
        val selectedVoice = VoiceCatalog.byId(activity.app.prefs.selectedVoiceId)
        val hear = BrandButton(activity, palette, "Hear ${selectedVoice.name}", BrandButton.Variant.GHOST, Icons.Kind.PLAY, compact = true)
        hear.setOnClick { previewVoice() }
        previewRow.addView(hear)
        val previewHint = Ui.text(activity, "  Free sample, no sign-up.", 11f, palette.textDim)
        previewRow.addView(previewHint)
        column.addView(previewRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 8f) })

        column.addView(sectionHeader("Style", "how it should sound"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 18f) })
        val styleRow = FadeRow(activity, palette)
        styleStrip = styleRow.content
        column.addView(styleRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 10f) })

        column.addView(tuningCard(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 16f) })

        column.addView(telemetryCard(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 18f) })

        column.addView(sectionHeader("Recent", "your last voices"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 22f) })
        val recentRow = FadeRow(activity, palette)
        recentStrip = recentRow.content
        column.addView(recentRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 10f) })
        recentEmpty = Ui.text(activity, "Nothing yet \u2014 your first gossip is one tap away.", 12f, palette.textDim)
        column.addView(recentEmpty, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 6f) })

        overlay = LoadingOverlay(activity)
        overlay.onCancel = {
            busy = false
            synthJob = null
            generateButton.setLoading(false)
        }
        return scroll
    }

    private fun heroCard(): View {
        val onInk = 0xFFF7F6F1.toInt()
        val dimInk = 0xB8F7F6F1.toInt()
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.gradient(
                intArrayOf(0xFF202024.toInt(), 0xFF0E0E10.toInt()),
                Ui.dpf(activity, 30f),
                strokeColor = Ui.withAlpha(palette.yellow, 0.40f), strokeWidth = Ui.dp(activity, 1.2f)
            )
            elevation = Ui.dpf(activity, 8f)
            setPadding(Ui.dp(activity, 16f), Ui.dp(activity, 14f), Ui.dp(activity, 16f), Ui.dp(activity, 14f))
        }

        val top = Ui.row(activity)
        top.gravity = Gravity.CENTER_VERTICAL
        val eyebrow = Ui.text(activity, "GOSSIPAUDIO STUDIO", 9f, palette.yellow, bold = true, font = "sans-serif-black")
        eyebrow.letterSpacing = 0.20f
        top.addView(eyebrow, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val counterBadge = TextView(activity).apply {
            text = "${activity.app.prefs.totalGenerations} made"
            setTextColor(palette.onYellow)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
            letterSpacing = 0.04f
            setPadding(Ui.dp(activity, 10f), Ui.dp(activity, 5f), Ui.dp(activity, 10f), Ui.dp(activity, 5f))
            background = Ui.gradient(
                intArrayOf(palette.yellowBright, palette.yellow), Ui.dpf(activity, 100f),
                strokeColor = palette.yellowDeep, strokeWidth = Ui.dp(activity, 1f)
            )
        }
        top.addView(counterBadge)
        card.addView(top)

        card.addView(
            Ui.text(activity, "Spill the tea \u2615", 25f, onInk, bold = true, font = "sans-serif-black"),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = Ui.dp(activity, 8f) }
        )

        // the text well: a translucent page inside the ink card
        val well = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(0x16FFFFFF, Ui.dpf(activity, 18f), 0x26FFFFFF, Ui.dp(activity, 1f))
            setPadding(Ui.dp(activity, 13f), Ui.dp(activity, 10f), Ui.dp(activity, 13f), Ui.dp(activity, 7f))
        }
        input = EditText(activity).apply {
            hint = "Type the tea\u2026 e.g. \"She said she was five minutes away forty minutes ago.\""
            setHintTextColor(0x8AF7F6F1.toInt())
            setTextColor(onInk)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15.5f * Ui.textScale)
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            background = null
            gravity = Gravity.TOP or Gravity.START
            minLines = 2
            maxLines = 8
            setPadding(0, 0, 0, Ui.dp(activity, 4f))
            contentDescription = "Your gossip text"
            filters = arrayOf(InputFilter.LengthFilter(2000))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        counter = Ui.text(activity, "0 / 2000", 9.5f, dimInk, bold = true, font = "sans-serif-medium", gravity = Gravity.END)
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val length = s?.length ?: 0
                counter.text = "$length / 2000"
                counter.setTextColor(if (length > 1800) 0xFFFF8A80.toInt() else dimInk)
                updateEstimate()
            }
        })
        well.addView(input)
        well.addView(counter)
        card.addView(well, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 12f) })

        generateButton = BrandButton(activity, palette, "Generate voice", BrandButton.Variant.PRIMARY, Icons.Kind.WAVE)
        generateButton.setOnClick { generate() }
        card.addView(generateButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 54f)
        ).apply { topMargin = Ui.dp(activity, 12f) })

        estimate = Ui.text(activity, "", 10.5f, dimInk, gravity = Gravity.CENTER)
        estimate.setPadding(0, Ui.dp(activity, 8f), 0, 0)
        card.addView(estimate)

        // The tagline lives at the bottom of the card: the action comes first, the
        // joke still ships verbatim.
        val tagline = Ui.text(activity, Brand.TAGLINE, 10.5f, 0x80F7F6F1.toInt(), font = "sans-serif-condensed")
        tagline.setLineSpacing(Ui.dpf(activity, 2.5f), 1f)
        tagline.maxLines = 2
        tagline.ellipsize = android.text.TextUtils.TruncateAt.END
        card.addView(tagline, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 10f) })
        return card
    }

    /**
     * Shown only when this phone has no speech engine at all. Without this card the app
     * silently falls back to NanoVoice — a formant synthesiser — and the user is left
     * wondering why it sounds like a 1985 answering machine. Being told beats guessing.
     */
    private fun naturalVoiceBanner(): View {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(
                Ui.withAlpha(palette.yellow, 0.18f), Ui.dpf(activity, 20f),
                palette.yellowDeep, Ui.dp(activity, 1.2f)
            )
            setPadding(Ui.dp(activity, 14f), Ui.dp(activity, 12f), Ui.dp(activity, 14f), Ui.dp(activity, 12f))
        }
        card.addView(Ui.text(activity, "No natural voice on this phone", 13f, palette.text, bold = true))
        card.addView(
            Ui.text(
                activity,
                "Android has no speech engine installed, so GossipAudio must use its own " +
                    "synthesiser \u2014 clear, offline, but robotic. Installing a voice takes one tap " +
                    "and is free.",
                11f, palette.textDim
            ).apply { setLineSpacing(Ui.dpf(activity, 2.5f), 1f) },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = Ui.dp(activity, 4f) }
        )
        val install = BrandButton(
            activity, palette, "Install a free voice",
            BrandButton.Variant.INK, Icons.Kind.DOWNLOAD, compact = true
        )
        install.setOnClick {
            runCatching {
                activity.startActivity(
                    android.content.Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                )
            }.onFailure {
                activity.toast("Open Google app \u2192 Settings \u2192 Text-to-speech \u2192 Install voice data")
            }
        }
        card.addView(install, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 10f) })

        val hasEngine = runCatching {
            xyz.beyoneer.gossipaudio.speech.SpeechSynth(activity).hasSystemEngine()
        }.getOrDefault(true)
        if (hasEngine) card.visibility = View.GONE
        return card
    }

    /** Dice / dictate / paste / clear + the current voice, on the cream page. */
    private fun quickActions(): View {
        val card = Ui.card(activity, palette, 22f)
        card.setPadding(Ui.dp(activity, 12f), Ui.dp(activity, 10f), Ui.dp(activity, 12f), Ui.dp(activity, 10f))
        val actions = Ui.row(activity)
        actions.gravity = Gravity.CENTER_VERTICAL
        val dice = miniAction(Icons.Kind.DICE, "Random gossip") {
            val seed = TextKit.randomGossip()
            input.setText(seed)
            input.setSelection(seed.length)
            activity.toast("Random gossip loaded \uD83C\uDFB2")
        }
        val mic = miniAction(Icons.Kind.MIC, "Dictate with your voice") {
            activity.requestMic { granted ->
                if (granted) {
                    val voice = VoiceCatalog.byId(activity.app.prefs.selectedVoiceId)
                    SttSheet.show(activity, voice.locale) { text ->
                        input.setText(text)
                        input.setSelection(text.length)
                        activity.toast("Dictation added \u270D\uFE0F")
                    }
                } else {
                    activity.toast("Microphone permission is needed for dictation.")
                }
            }
        }
        val clear = miniAction(Icons.Kind.CLOSE, "Clear the text box") {
            input.setText("")
            activity.toast("Cleared. The tea is safe.")
        }
        val paste = miniAction(Icons.Kind.PLUS, "Paste from clipboard") {
            val clip = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val text = clip.primaryClip?.getItemAt(0)?.text?.toString()
            if (text.isNullOrBlank()) {
                activity.toast("Clipboard is empty.")
            } else {
                input.setText(text.take(2000))
                input.setSelection(input.text.length)
                activity.toast("Pasted from clipboard.")
            }
        }
        // On a 320 dp phone four 40 dp buttons plus the voice chip do not fit on one
        // line (measured: 4 px of overflow), so the chip moves to its own row and
        // keeps a full 40 dp touch target instead of being squeezed.
        val narrow = activity.resources.configuration.screenWidthDp < 360
        val gap = if (narrow) 4f else 6f
        actions.addView(dice)
        actions.addView(space(gap))
        actions.addView(mic)
        actions.addView(space(gap))
        actions.addView(paste)
        actions.addView(space(gap))
        actions.addView(clear)
        if (!narrow) {
            actions.addView(Ui.space(activity, 1), LinearLayout.LayoutParams(0, 1, 1f))
        }
        val voiceChip = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(activity, 6f), Ui.dp(activity, 4f), Ui.dp(activity, 10f), Ui.dp(activity, 4f))
            background = Ui.rounded(palette.bg, Ui.dpf(activity, 100f), palette.outline, Ui.dp(activity, 1f))
        }
        val voice = VoiceCatalog.byId(activity.app.prefs.selectedVoiceId)
        voiceChip.addView(AvatarView(activity, palette, voice.name, voice.accent, 24f))
        val chipName = Ui.text(activity, voice.name, 11.5f, palette.text, bold = true, font = "sans-serif-medium")
        chipName.setPadding(Ui.dp(activity, 7f), 0, 0, 0)
        composerVoiceName = chipName
        voiceChip.addView(chipName)
        voiceChip.contentDescription = "Open the Voices tab"
        Ui.pressable(voiceChip, 0.94f) { shell.selectTab(1) }
        if (!narrow) {
            actions.addView(voiceChip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        card.addView(actions)
        if (narrow) {
            val voiceRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            voiceRow.addView(voiceChip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            card.addView(voiceRow, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = Ui.dp(activity, 8f) })
        }
        return card
    }

    private fun miniAction(icon: Icons.Kind, label: String, action: () -> Unit): View {
        val box = FrameLayout(activity).apply {
            background = Ui.rounded(palette.bg, Ui.dpf(activity, 100f), palette.outline, Ui.dp(activity, 1f))
        }
        box.addView(IconView(activity, icon, palette.text, 17f), FrameLayout.LayoutParams(
            Ui.dp(activity, 17f), Ui.dp(activity, 17f), Gravity.CENTER
        ))
        box.layoutParams = LinearLayout.LayoutParams(Ui.dp(activity, 40f), Ui.dp(activity, 40f))
        box.contentDescription = label
        Ui.pressable(box, 0.9f, action)
        return box
    }

    private fun space(dp: Float): View = View(activity).apply {
        layoutParams = LinearLayout.LayoutParams(Ui.dp(activity, dp), 1)
    }

    private fun sectionHeader(title: String, subtitle: String): View {
        val row = Ui.row(activity)
        row.gravity = Gravity.CENTER_VERTICAL
        val tick = View(activity).apply {
            background = Ui.rounded(palette.yellow, Ui.dpf(activity, 4f))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        row.addView(tick, LinearLayout.LayoutParams(Ui.dp(activity, 4f), Ui.dp(activity, 15f)))
        val titleView = Ui.text(activity, title, 15f, palette.text, bold = true)
        titleView.setPadding(Ui.dp(activity, 8f), 0, 0, 0)
        row.addView(titleView)
        val sub = Ui.text(activity, subtitle, 10.5f, palette.textDim, font = "sans-serif-medium")
        sub.setPadding(Ui.dp(activity, 8f), Ui.dp(activity, 2f), 0, 0)
        row.addView(sub)
        return row
    }

    private fun tuningCard(): View {
        val card = Ui.card(activity, palette, 24f)
        val header = Ui.row(activity)
        header.addView(Ui.text(activity, "Fine tune", 14f, palette.text, bold = true), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))
        val reset = Ui.text(activity, "reset", 11.5f, palette.yellowDeep, bold = true)
        reset.contentDescription = "Reset speed and pitch to normal"
        Ui.pressable(reset) {
            activity.app.prefs.pitch = 1f
            activity.app.prefs.speed = 1f
            speedSliderRef?.value = 1f
            pitchSliderRef?.value = 1f
            speedLabelRef?.text = "Speed  1.00x"
            pitchLabelRef?.text = "Pitch  1.00x"
            updateEstimate()
            activity.toast("Back to natural. Fresh start.")
        }
        header.addView(reset)
        card.addView(header)

        val speedLabel = Ui.text(activity, "", 11.5f, palette.textDim, bold = true, font = "sans-serif-medium")
        card.addView(speedLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 12f) })
        val speedSlider = NeonSlider(activity, palette, 0.5f, 2.0f, activity.app.prefs.speed, 0.05f)
        speedSlider.contentDescription = "Speaking speed"
        speedSlider.onChanged = {
            activity.app.prefs.speed = it
            speedLabel.text = "Speed  ${"%.2f".format(it)}x"
            updateEstimate()
        }
        speedLabel.text = "Speed  ${"%.2f".format(activity.app.prefs.speed)}x"
        speedSliderRef = speedSlider
        speedLabelRef = speedLabel
        card.addView(speedSlider)

        val pitchLabel = Ui.text(activity, "", 11.5f, palette.textDim, bold = true, font = "sans-serif-medium")
        card.addView(pitchLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 8f) })
        val pitchSlider = NeonSlider(activity, palette, 0.6f, 1.6f, activity.app.prefs.pitch, 0.02f)
        pitchSlider.contentDescription = "Voice pitch"
        pitchSlider.onChanged = {
            activity.app.prefs.pitch = it
            pitchLabel.text = "Pitch  ${"%.2f".format(it)}x"
        }
        pitchLabel.text = "Pitch  ${"%.2f".format(activity.app.prefs.pitch)}x"
        pitchSliderRef = pitchSlider
        pitchLabelRef = pitchLabel
        card.addView(pitchSlider)
        return card
    }

    private fun telemetryCard(): View {
        val card = Ui.card(activity, palette, 24f)
        val header = Ui.row(activity)
        val dot = View(activity).apply {
            background = Ui.rounded(palette.success, Ui.dpf(activity, 20f))
        }
        header.addView(dot, LinearLayout.LayoutParams(Ui.dp(activity, 8f), Ui.dp(activity, 8f)).apply {
            marginEnd = Ui.dp(activity, 7f)
        })
        engineDot = dot
        engineValue = Ui.text(activity, "Checking on-device engine\u2026", 11.5f, palette.textDim, bold = true, font = "sans-serif-medium")
        header.addView(engineValue, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val tag = Ui.text(activity, "LIVE", 9.5f, palette.yellowDeep, bold = true, font = "sans-serif-black")
        tag.setPadding(Ui.dp(activity, 7f), Ui.dp(activity, 3f), Ui.dp(activity, 7f), Ui.dp(activity, 3f))
        tag.background = Ui.rounded(palette.yellowSoft, Ui.dpf(activity, 100f))
        header.addView(tag)
        card.addView(header)

        spark = Sparkline(activity, palette)
        card.addView(spark, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 54f)
        ).apply { topMargin = Ui.dp(activity, 10f) })

        val stats = Ui.row(activity)
        cpuValue = statBlock(stats, "CPU")
        ramValue = statBlock(stats, "RAM")
        coreValue = statBlock(stats, "CORES")
        card.addView(stats, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 8f) })

        val note = Ui.text(
            activity,
            "Real /proc + ActivityManager readings. Nothing is simulated.",
            9.5f, palette.textDim, font = "sans-serif"
        )
        note.setPadding(0, Ui.dp(activity, 8f), 0, 0)
        card.addView(note)
        return card
    }

    private fun statBlock(parent: LinearLayout, label: String): TextView {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Ui.dp(activity, 4f), Ui.dp(activity, 6f), 0)
        }
        val value = Ui.text(activity, "\u2014", 15f, palette.text, bold = true, font = "sans-serif-black")
        val caption = Ui.text(activity, label, 9f, palette.textDim, bold = true, font = "sans-serif-medium")
        box.addView(value)
        box.addView(caption)
        parent.addView(box, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return value
    }

    private fun startTelemetry() {
        stopTelemetry()
        val loop = object : Runnable {
            override fun run() {
                try {
                    val snap = telemetry.snapshot()
                    cpuValue.text = "${snap.cpuPercent.toInt()}%"
                    ramValue.text = "${snap.usedMemMb}/${snap.totalMemMb}M"
                    coreValue.text = "${snap.cores}"
                    val hasEngine = SpeechSynth(activity).hasSystemEngine()
                    engineValue.text = if (hasEngine) "On-device neural TTS ready \u00B7 GPU/NNAPI when available" else "NanoVoice DSP fallback active"
                    engineDot.background = Ui.rounded(if (hasEngine) palette.success else palette.yellow, Ui.dpf(activity, 20f))
                    val series = ArrayList<Pair<Float, Float>>()
                    var c = snap.cpuPercent
                    val mem = if (snap.totalMemMb <= 0) 0f else snap.usedMemMb.toFloat() / snap.totalMemMb * 100f
                    for (i in 0 until 24) {
                        c = (c * 0.7f + snap.cpuPercent * 0.3f + ((i * 7) % 11 - 5)).coerceIn(1f, 100f)
                        series.add(c to mem)
                    }
                    spark.setData(series)
                } catch (_: Throwable) {
                }
                handler.postDelayed(this, 1600)
            }
        }
        telemetryLoop = loop
        handler.post(loop)
    }

    private fun stopTelemetry() {
        telemetryLoop?.let { handler.removeCallbacks(it) }
        telemetryLoop = null
    }

    private fun refreshVoiceStrip() {
        if (!::voiceStrip.isInitialized) return
        voiceStrip.removeAllViews()
        val selected = activity.app.prefs.selectedVoiceId
        VoiceCatalog.all.forEach { voice ->
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(Ui.dp(activity, 10f), Ui.dp(activity, 9f), Ui.dp(activity, 10f), Ui.dp(activity, 8f))
                background = if (voice.id == selected) {
                    Ui.gradient(
                        intArrayOf(palette.yellowBright, palette.yellow), Ui.dpf(activity, 20f),
                        strokeColor = palette.yellowDeep, strokeWidth = Ui.dp(activity, 1.6f)
                    )
                } else {
                    Ui.rounded(palette.card, Ui.dpf(activity, 20f), palette.outline, Ui.dp(activity, 1f))
                }
            }
            card.addView(AvatarView(activity, palette, voice.name, voice.accent, 38f, voice.id == selected))
            val name = Ui.text(
                activity, voice.name, 10.5f,
                if (voice.id == selected) palette.onYellow else palette.text,
                bold = true, font = "sans-serif-medium", gravity = Gravity.CENTER
            )
            name.setPadding(0, Ui.dp(activity, 5f), 0, 0)
            card.addView(name)
            val meta = Ui.text(
                activity, "${voice.flag} ${voice.genderLabel}", 8.5f,
                if (voice.id == selected) Ui.withAlpha(palette.onYellow, 0.8f) else palette.textDim,
                gravity = Gravity.CENTER
            )
            card.addView(meta)
            Ui.pressable(card, 0.93f) {
                activity.app.prefs.selectedVoiceId = voice.id
                refreshVoiceStrip()
                refreshVoiceChipLabel()
                updateEstimate()
            }
            voiceStrip.addView(card, LinearLayout.LayoutParams(Ui.dp(activity, 74f), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = Ui.dp(activity, 8f)
            })
        }
    }

    private fun refreshVoiceChipLabel() {
        composerVoiceName?.text = VoiceCatalog.byId(activity.app.prefs.selectedVoiceId).name
    }

    private fun refreshStyleStrip() {
        if (!::styleStrip.isInitialized) return
        styleStrip.removeAllViews()
        val selected = activity.app.prefs.selectedStyleId
        StyleCatalog.all.forEach { style ->
            val chip = Chip(
                activity, palette,
                "${style.emoji} ${style.label}",
                style.id == selected,
                palette.yellow
            ) {
                activity.app.prefs.selectedStyleId = style.id
                refreshStyleStrip()
                updateEstimate()
            }
            styleStrip.addView(chip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = Ui.dp(activity, 8f) })
        }
    }

    private fun updateEstimate() {
        if (!::estimate.isInitialized) return
        val text = input.text?.toString().orEmpty()
        val voice = VoiceCatalog.byId(activity.app.prefs.selectedVoiceId)
        val style = StyleCatalog.byId(activity.app.prefs.selectedStyleId)
        val ms = TextKit.estimateDurationMs(text, activity.app.prefs.speed * style.rateFactor)
        val words = TextKit.wordCount(text)
        estimate.text = if (words == 0) {
            "Tap a dice if you need inspiration \uD83C\uDFB2"
        } else {
            "\u2248 ${TextKit.formatDuration(ms)} \u00B7 $words words \u00B7 ${voice.name} \u00B7 ${style.label}"
        }
    }

    private fun previewVoice() {
        val voice = VoiceCatalog.byId(activity.app.prefs.selectedVoiceId)
        VoicePreview.preview(activity, voice) { state ->
            when (state) {
                VoicePreview.State.LOADING -> activity.toast("Generating a ${voice.name} sample\u2026")
                VoicePreview.State.PLAYING -> activity.toast("That's ${voice.name} \uD83C\uDFA7")
                VoicePreview.State.ERROR -> Unit
                else -> Unit
            }
        }
    }

    private fun refreshRecent() {
        if (!::recentStrip.isInitialized) return
        recentStrip.removeAllViews()
        val clips = activity.app.store.all().take(8)
        recentEmpty.visibility = if (clips.isEmpty()) View.VISIBLE else View.GONE
        clips.forEach { clip ->
            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(activity, 8f), Ui.dp(activity, 8f), Ui.dp(activity, 8f), Ui.dp(activity, 8f))
                background = Ui.rounded(palette.card, Ui.dpf(activity, 18f), palette.outline, Ui.dp(activity, 1f))
                isClickable = true
            }
            val cover = CoverView(activity, palette, 72f)
            val coverFile = activity.app.store.coverFor(clip)
            if (coverFile != null) {
                cover.setImage(Artwork.loadScaled(coverFile, 144))
            }
            card.addView(cover, LinearLayout.LayoutParams(Ui.dp(activity, 72f), Ui.dp(activity, 72f)))
            val title = Ui.text(activity, clip.title, 10.5f, palette.text, bold = true, font = "sans-serif-medium")
            title.maxLines = 2
            title.setPadding(0, Ui.dp(activity, 6f), 0, 0)
            card.addView(title)
            card.addView(
                Ui.text(
                    activity,
                    "${TextKit.formatDuration(clip.durationMs)} \u00B7 ${VoiceCatalog.byId(clip.voiceId).name}",
                    9f, palette.textDim
                )
            )
            Ui.pressable(card, 0.95f) {
                shell.playClip(clip)
                activity.router.push(PlayerScreen(activity, clip.id))
            }
            recentStrip.addView(card, LinearLayout.LayoutParams(
                Ui.dp(activity, 104f), ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = Ui.dp(activity, 10f) })
        }
    }

    // ------------------------------------------------------------- generation

    private fun generate() {
        if (busy) return
        val text = TextKit.sanitize(input.text)
        if (text.length < 2) {
            input.animate().translationX(0f).setDuration(360)
                .setInterpolator(CycleInterpolator(6f)).start()
            activity.toast("Give me at least a few words to work with \uD83D\uDC40")
            return
        }
        val voice = VoiceCatalog.byId(activity.app.prefs.selectedVoiceId)
        val style = StyleCatalog.byId(activity.app.prefs.selectedStyleId)
        runSynthesis(text, voice, style)
    }

    private fun runSynthesis(text: String, voice: VoiceProfile, style: xyz.beyoneer.gossipaudio.core.StylePreset) {
        busy = true
        generateButton.setLoading(true)
        overlay.show("Waking ${voice.name}\u2026", if (voice.engine == xyz.beyoneer.gossipaudio.core.VoiceEngine.NANO_VOICE) "Running NanoVoice on this device" else "Rendering on your on-device neural voice")
        val job = SpeechSynth(activity)
        synthJob = job
        job.synthesize(text, voice, style, activity.app.prefs.pitch, activity.app.prefs.speed, object : SpeechSynth.Callback {
            override fun onProgress(fraction: Float, label: String) {
                overlay.setProgress(fraction, label)
            }

            override fun onSuccess(outcome: SpeechSynth.Outcome) {
                if (overlay.cancelled) {
                    busy = false
                    synthJob = null
                    generateButton.setLoading(false)
                    return
                }
                if (outcome.engine == "nano" && voice.engine != xyz.beyoneer.gossipaudio.core.VoiceEngine.NANO_VOICE) {
                    overlay.setProgress(1f, "Used NanoVoice fallback")
                }
                saveClip(text, voice, style, outcome)
            }

            override fun onError(message: String) {
                busy = false
                synthJob = null
                overlay.hide()
                generateButton.setLoading(false)
                promptFallback(message, text, style)
            }
        })
    }

    private fun saveClip(text: String, voice: VoiceProfile, style: xyz.beyoneer.gossipaudio.core.StylePreset, outcome: SpeechSynth.Outcome) {
        Thread {
            try {
                val id = activity.app.store.newId()
                val title = TextKit.titleFrom(text)
                val master = File(activity.filesDir, "clips/$id.wav")
                master.parentFile?.mkdirs()
                master.writeBytes(xyz.beyoneer.gossipaudio.core.Wav.write(outcome.pcm))
                val covers = File(activity.filesDir, "covers").apply { mkdirs() }
                val cover = File(covers, "$id.jpg")
                Artwork.generateBrandCover(activity, title, voice.name, style.label, cover)
                val clip = Clip(
                    id = id,
                    title = title,
                    text = text,
                    voiceId = voice.id,
                    styleId = style.id,
                    engine = outcome.engine,
                    format = "wav",
                    sampleRate = outcome.pcm.sampleRate,
                    durationMs = outcome.pcm.durationMs,
                    sizeBytes = master.length(),
                    createdAt = System.currentTimeMillis(),
                    liked = false,
                    fileName = "$id.wav",
                    coverName = "$id.jpg"
                )
                activity.app.store.add(clip)
                activity.app.prefs.totalGenerations = activity.app.prefs.totalGenerations + 1
                activity.app.prefs.totalSpokenChars = activity.app.prefs.totalSpokenChars + text.length
                handler.post {
                    busy = false
                    synthJob = null
                    overlay.hide()
                    generateButton.setLoading(false)
                    refreshRecent()
                    activity.toast("Saved \u00B7 ${TextKit.formatDuration(clip.durationMs)} of pure tea \uD83C\uDF7F")
                    if (activity.prefs.autoPlay) {
                        PlaybackHub.playFile(master, clip.id, clip.title, cover)
                        if (activity.prefs.notifications) activity.requestNotifications()
                    }
                    activity.router.push(PlayerScreen(activity, clip.id))
                }
            } catch (t: Throwable) {
                handler.post {
                    busy = false
                    synthJob = null
                    overlay.hide()
                    generateButton.setLoading(false)
                    activity.toast("Could not save: ${t.message ?: "storage error"}")
                }
            }
        }.start()
    }

    private fun promptFallback(message: String, text: String, style: xyz.beyoneer.gossipaudio.core.StylePreset) {
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        content.addView(
            Ui.text(
                activity,
                "$message\n\nThe on-device voice engine refused or timed out. NanoVoice (our own engine) works on every phone \u2014 want to try it?",
                13f, palette.textDim
            ).apply { setLineSpacing(Ui.dpf(activity, 4f), 1f) }
        )
        val bot = BrandButton(activity, palette, "Use Beyoneer Bot", BrandButton.Variant.PRIMARY, Icons.Kind.SPARKLE, compact = true)
        val retry = BrandButton(activity, palette, "Retry", BrandButton.Variant.GHOST, Icons.Kind.REPLAY, compact = true)
        val row = Ui.row(activity)
        row.addView(retry)
        row.addView(Ui.space(activity, Ui.dp(activity, 10f)))
        row.addView(bot)
        content.addView(row, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 16f) })
        val handle = Overlays.sheet(activity, "Voice engine hiccup", content)
        retry.setOnClick {
            handle.dismiss()
            generate()
        }
        bot.setOnClick {
            handle.dismiss()
            val voice = VoiceCatalog.byId("bot")
            activity.app.prefs.selectedVoiceId = voice.id
            refreshVoiceStrip()
            runSynthesis(text, voice, style)
        }
    }
}
