package xyz.beyoneer.gossipaudio.ui.tabs

import android.app.TimePickerDialog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import xyz.beyoneer.gossipaudio.audio.PlaybackHub
import xyz.beyoneer.gossipaudio.core.TextKit
import xyz.beyoneer.gossipaudio.notify.ReminderScheduler
import xyz.beyoneer.gossipaudio.ui.Brand
import xyz.beyoneer.gossipaudio.ui.MainActivity
import xyz.beyoneer.gossipaudio.ui.Overlays
import xyz.beyoneer.gossipaudio.ui.Tab
import xyz.beyoneer.gossipaudio.ui.screens.ShellScreen
import xyz.beyoneer.gossipaudio.ui.theme.Ui
import xyz.beyoneer.gossipaudio.ui.widgets.IconView
import xyz.beyoneer.gossipaudio.ui.widgets.Icons
import xyz.beyoneer.gossipaudio.ui.widgets.NeonSlider
import xyz.beyoneer.gossipaudio.ui.widgets.Segmented
import xyz.beyoneer.gossipaudio.ui.widgets.ToggleSwitch
import xyz.beyoneer.gossipaudio.data.AudioFormat
import xyz.beyoneer.gossipaudio.speech.SpeechSynth

/** Every knob in one place, all of it real. */
class SettingsTab(private val shell: ShellScreen) : Tab {

    private val activity get() = shell.act
    private val palette get() = shell.colors
    private lateinit var scroll: ScrollView
    private var reminderRow: View? = null
    private var reminderTimeValue: TextView? = null

    override val root: View by lazy { build() }

    override fun onShow() {
        if (::scroll.isInitialized) Ui.fadeInUp(scroll, 0, 10f)
    }

    override fun onHide() = Unit

    private fun build(): View {
        scroll = ScrollView(activity)
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(activity, 18f), Ui.dp(activity, 8f), Ui.dp(activity, 18f), Ui.dp(activity, 30f))
        }
        scroll.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        column.addView(Ui.text(activity, "Settings", 25f, palette.text, bold = true))
        column.addView(
            Ui.text(activity, "Tune the app, not your friends.", 12.5f, palette.textDim, font = "sans-serif-medium"),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(activity, 3f)
            }
        )

        // --- appearance
        column.addView(sectionLabel("Appearance"), topMargin())
        column.addView(
            card(
                segRow(
                    "Theme",
                    listOf("System", "Light", "Dark"),
                    when (activity.prefs.themeMode) {
                        "light" -> 1
                        "dark" -> 2
                        else -> 0
                    }
                ) { index ->
                    activity.setThemeMode(when (index) {
                        1 -> "light"
                        2 -> "dark"
                        else -> "system"
                    })
                },
                toggleRow(
                    Icons.Kind.SPARKLE, "Reduce motion",
                    "Calmer transitions, no butterfly flight",
                    activity.prefs.reduceMotion
                ) { activity.prefs.reduceMotion = it },
                toggleRow(
                    Icons.Kind.WAVE, "Haptics",
                    "Tiny taps when buttons are pressed",
                    activity.prefs.haptics
                ) { activity.prefs.haptics = it }
            )
        )

        // --- sound
        column.addView(sectionLabel("Sound & files"), topMargin())
        column.addView(
            card(
                segRow(
                    "Default download format",
                    listOf("WAV", "MP3", "M4A"),
                    when (AudioFormat.from(activity.prefs.defaultFormat)) {
                        AudioFormat.WAV -> 0
                        AudioFormat.MP3 -> 1
                        AudioFormat.M4A -> 2
                    }
                ) { index ->
                    activity.prefs.defaultFormat = when (index) {
                        0 -> "WAV"
                        2 -> "M4A"
                        else -> "MP3"
                    }
                },
                bitrateRow(),
                toggleRow(
                    Icons.Kind.FX, "Audio polish",
                    "Equalizer + bass boost during playback",
                    activity.prefs.effectsEnabled
                ) {
                    activity.prefs.effectsEnabled = it
                    PlaybackHub.setEffects(it)
                },
                toggleRow(
                    Icons.Kind.PLAY, "Auto-play after generating",
                    "Straight to the player when your voice is ready",
                    activity.prefs.autoPlay
                ) { activity.prefs.autoPlay = it }
            )
        )

        // --- notifications
        column.addView(sectionLabel("Notifications"), topMargin())
        val reminder = toggleRow(
            Icons.Kind.BELL, "Daily gossip nudge",
            "A friendly reminder at your chosen time",
            activity.prefs.dailyReminder
        ) { enabled ->
            activity.prefs.dailyReminder = enabled
            if (enabled) {
                activity.requestNotifications()
                ReminderScheduler.schedule(activity, activity.prefs.reminderHour, activity.prefs.reminderMinute)
                activity.toast("Daily gossip enabled \uD83D\uDD14")
            } else {
                ReminderScheduler.cancel(activity)
                activity.toast("Reminders off. Quiet mode.")
            }
        }
        reminderRow = reminder
        column.addView(
            card(
                toggleRow(
                    Icons.Kind.INFO, "System notifications",
                    "Playback controls + export confirmations",
                    activity.prefs.notifications
                ) { enabled ->
                    activity.prefs.notifications = enabled
                    if (enabled) activity.requestNotifications()
                },
                reminder,
                actionRowTitled(Icons.Kind.TIMER, "Reminder time", reminderTimeLabel()) { pickTime() }
            )
        )

        // --- dictation
        column.addView(sectionLabel("Dictation"), topMargin())
        column.addView(
            card(
                toggleRow(
                    Icons.Kind.MIC, "Prefer offline speech-to-text",
                    "Uses on-device recognition when installed",
                    activity.prefs.preferOfflineStt
                ) { activity.prefs.preferOfflineStt = it },
                actionRow(Icons.Kind.INFO, "Speech engine", "System recognizer") {
                    showEngineInfo()
                }
            )
        )

        // --- storage
        column.addView(sectionLabel("Storage & privacy"), topMargin())
        val usage = "${activity.app.store.all().size} clips \u00B7 ${TextKit.formatBytes(activity.app.store.totalBytes())}"
        column.addView(
            card(
                actionRow(Icons.Kind.LIST, "Library usage", usage) {
                    Overlays.info(
                        activity, "Storage",
                        listOf(
                            "Clips" to "${activity.app.store.all().size}",
                            "Audio files" to TextKit.formatBytes(activity.app.store.audioDirSize()),
                            "Covers" to TextKit.formatBytes(activity.app.store.coverDirSize()),
                            "Cache" to TextKit.formatBytes(activity.cacheDirSizeCompat()),
                            "Free space" to "${activity.app.store.freeMb()} MB"
                        )
                    )
                },
                actionRow(Icons.Kind.TRASH, "Clean orphan files", "Remove audio with no metadata") {
                    val removed = activity.app.store.sweepOrphans()
                    activity.toast(if (removed == 0) "Already tidy." else "Removed $removed file(s).")
                },
                actionRow(Icons.Kind.DOWNLOAD, "Open Downloads folder", "Where exports land") {
                    Overlays.info(
                        activity, "Downloads",
                        listOf(
                            "Folder" to "Downloads/GossipAudio",
                            "Formats" to "WAV \u00B7 MP3 \u00B7 M4A",
                            "Cover art" to "Embedded in MP3 + M4A"
                        )
                    )
                }
            )
        )

        // --- about
        column.addView(sectionLabel("About"), topMargin())
        column.addView(
            card(
                actionRow(Icons.Kind.HELP, "Help & FAQ", "24 answers, zero judgement") { shell.openHelp() },
                actionRow(Icons.Kind.INFO, "Diagnostics", "Device, engines, self-test") { shell.openDiagnostics() },
                actionRow(Icons.Kind.HEART, "About ${Brand.NAME}", "v${Brand.VERSION} by ${Brand.AUTHOR}") { shell.openAbout() },
                actionRow(Icons.Kind.SHARE, "Share the app", "Send the tagline to a friend") {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            android.content.Intent.EXTRA_TEXT,
                            "${Brand.NAME} by ${Brand.AUTHOR} \u2014 ${Brand.TAGLINE}"
                        )
                    }
                    activity.startActivity(android.content.Intent.createChooser(intent, "Share GossipAudio"))
                }
            )
        )

        val footer = Ui.text(
            activity,
            "${Brand.NAME} v${Brand.VERSION}\n${Brand.TAGLINE}\n\nMade with \u2764 and absolutely no cloud.",
            10.5f, palette.textDim, gravity = Gravity.CENTER, font = "sans-serif"
        )
        footer.setLineSpacing(Ui.dpf(activity, 3f), 1f)
        footer.setPadding(0, Ui.dp(activity, 22f), 0, 0)
        column.addView(footer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        return scroll
    }

    private fun topMargin(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(activity, 20f)
        }

    private fun sectionLabel(title: String): View {
        val tv = Ui.text(activity, title.uppercase(), 10.5f, palette.yellowDeep, bold = true)
        tv.letterSpacing = 0.12f
        tv.setPadding(Ui.dp(activity, 4f), 0, 0, Ui.dp(activity, 8f))
        return tv
    }

    private fun card(vararg children: View): View {
        val card = Ui.card(activity, palette, 24f)
        children.forEachIndexed { index, child ->
            card.addView(child, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { if (index > 0) topMargin = Ui.dp(activity, 4f) })
        }
        return card
    }

    private fun segRow(label: String, options: List<String>, selected: Int, onChange: (Int) -> Unit): View {
        val wrap = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(Ui.text(activity, label, 12.5f, palette.textDim, bold = true, font = "sans-serif-medium"))
        val segmented = Segmented(activity, palette, options, selected)
        segmented.onSelect = onChange
        wrap.addView(segmented, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 40f)
        ).apply { topMargin = Ui.dp(activity, 8f) })
        return wrap
    }

    private fun bitrateRow(): View {
        val wrap = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val label = Ui.text(activity, "Download quality  ${activity.prefs.defaultBitrate} kbps", 12.5f, palette.textDim, bold = true, font = "sans-serif-medium")
        wrap.addView(label)
        val slider = NeonSlider(activity, palette, 64f, 320f, activity.prefs.defaultBitrate.toFloat(), 8f)
        slider.onChanged = {
            activity.prefs.defaultBitrate = it.toInt()
            label.text = "Download quality  ${it.toInt()} kbps"
        }
        wrap.addView(slider)
        return wrap
    }

    private fun toggleRow(icon: Icons.Kind, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Ui.dp(activity, 8f), 0, Ui.dp(activity, 8f))
        }
        row.addView(IconView(activity, icon, palette.text, 18f), LinearLayout.LayoutParams(
            Ui.dp(activity, 20f), Ui.dp(activity, 20f)
        ))
        val texts = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(activity, 10f), 0, Ui.dp(activity, 10f), 0)
        }
        texts.addView(Ui.text(activity, title, 13.5f, palette.text, bold = true, font = "sans-serif-medium"))
        texts.addView(Ui.text(activity, subtitle, 10.5f, palette.textDim, font = "sans-serif"))
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val toggle = ToggleSwitch(activity, palette, checked)
        toggle.onChanged = onChange
        row.addView(toggle)
        return row
    }

    /** Same as [actionRow] but keeps a handle on the value label so it can be refreshed. */
    private fun actionRowTitled(icon: Icons.Kind, title: String, subtitle: String, action: () -> Unit): View {
        val row = actionRow(icon, title, subtitle, action)
        reminderTimeValue = findValueLabel(row)
        return row
    }

    private fun findValueLabel(view: View): TextView? {
        if (view is TextView && view.text?.toString() == reminderTimeLabel()) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findValueLabel(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun actionRow(icon: Icons.Kind, title: String, subtitle: String, action: () -> Unit): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, Ui.dp(activity, 8f), 0, Ui.dp(activity, 8f))
            isClickable = true
        }
        row.addView(IconView(activity, icon, palette.text, 18f), LinearLayout.LayoutParams(
            Ui.dp(activity, 20f), Ui.dp(activity, 20f)
        ))
        val texts = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(activity, 10f), 0, Ui.dp(activity, 6f), 0)
        }
        texts.addView(Ui.text(activity, title, 13.5f, palette.text, bold = true, font = "sans-serif-medium"))
        texts.addView(Ui.text(activity, subtitle, 10.5f, palette.textDim, font = "sans-serif"))
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(IconView(activity, Icons.Kind.NEXT, palette.textDim, 14f), LinearLayout.LayoutParams(
            Ui.dp(activity, 16f), Ui.dp(activity, 16f)
        ))
        Ui.pressable(row, 0.99f, action)
        return row
    }

    private fun reminderTimeLabel(): String {
        val h = activity.prefs.reminderHour
        val m = activity.prefs.reminderMinute
        val suffix = if (h < 12) "AM" else "PM"
        val hour12 = when {
            h % 12 == 0 -> 12
            else -> h % 12
        }
        return String.format("%d:%02d %s", hour12, m, suffix)
    }

    private fun pickTime() {
        TimePickerDialog(
            activity,
            { _, hour, minute ->
                activity.prefs.reminderHour = hour
                activity.prefs.reminderMinute = minute
                if (activity.prefs.dailyReminder) {
                    ReminderScheduler.schedule(activity, hour, minute)
                    activity.toast("Reminder moved to ${reminderTimeLabel()}")
                } else {
                    activity.toast("Saved ${reminderTimeLabel()} \u2014 enable the nudge to use it")
                }
                reminderTimeValue?.text = reminderTimeLabel()
            },
            activity.prefs.reminderHour,
            activity.prefs.reminderMinute,
            false
        ).show()
    }

    fun focusReminder() {
        // Building the root lazily is fine here: the tab is already the visible one.
        if (!::scroll.isInitialized) root
        val row = reminderRow ?: return
        scroll.post {
            scroll.smoothScrollTo(0, maxOf(0, row.top - Ui.dp(activity, 180f)))
            Ui.popIn(row)
        }
    }

    private fun showEngineInfo() {
        val synth = SpeechSynth(activity)
        Overlays.info(
            activity,
            "Speech engines",
            listOf(
                "System TTS" to if (synth.hasSystemEngine()) "available" else "missing",
                "NanoVoice DSP" to "always available",
                "Output formats" to "WAV / MP3 / M4A",
                "Cloud used" to "never (audio stays local)",
                "Speech-to-text" to if (activity.prefs.preferOfflineStt) "offline preferred" else "system default"
            )
        )
    }

}

/** Small helpers kept next to the screen that uses them. */
private fun xyz.beyoneer.gossipaudio.data.ClipStore.audioDirSize(): Long {
    var total = 0L
    audioDir.listFiles()?.forEach { total += it.length() }
    return total
}

private fun xyz.beyoneer.gossipaudio.data.ClipStore.coverDirSize(): Long {
    var total = 0L
    coverDir.listFiles()?.forEach { total += it.length() }
    return total
}

private fun xyz.beyoneer.gossipaudio.data.ClipStore.freeMb(): Long {
    val stat = android.os.StatFs(exportDir.absolutePath)
    return stat.availableBlocksLong * stat.blockSizeLong / (1024 * 1024)
}

private fun MainActivity.cacheDirSizeCompat(): Long {
    fun sizeOf(dir: java.io.File): Long {
        var total = 0L
        dir.listFiles()?.forEach { f ->
            total += if (f.isDirectory) sizeOf(f) else f.length()
        }
        return total
    }
    return try {
        sizeOf(cacheDir)
    } catch (_: Throwable) {
        0L
    }
}
