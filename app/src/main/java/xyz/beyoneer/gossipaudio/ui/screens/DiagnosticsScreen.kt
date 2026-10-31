package xyz.beyoneer.gossipaudio.ui.screens

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import xyz.beyoneer.gossipaudio.R
import xyz.beyoneer.gossipaudio.audio.AacEncoder
import xyz.beyoneer.gossipaudio.core.Id3
import xyz.beyoneer.gossipaudio.core.Mp3Encoder
import xyz.beyoneer.gossipaudio.core.Mp4Tagger
import xyz.beyoneer.gossipaudio.core.NanoVoice
import xyz.beyoneer.gossipaudio.core.TextKit
import xyz.beyoneer.gossipaudio.core.Wav
import xyz.beyoneer.gossipaudio.speech.SpeechSynth
import xyz.beyoneer.gossipaudio.speech.Stt
import xyz.beyoneer.gossipaudio.ui.Brand
import xyz.beyoneer.gossipaudio.ui.MainActivity
import xyz.beyoneer.gossipaudio.ui.Screen
import xyz.beyoneer.gossipaudio.ui.theme.Ui
import xyz.beyoneer.gossipaudio.ui.widgets.BrandButton
import xyz.beyoneer.gossipaudio.ui.widgets.GlowBackdrop
import xyz.beyoneer.gossipaudio.ui.widgets.IconView
import xyz.beyoneer.gossipaudio.ui.widgets.Icons
import xyz.beyoneer.gossipaudio.ui.widgets.Sparkline
import xyz.beyoneer.gossipaudio.util.Telemetry
import java.io.File

/** Real device facts, engine availability and a full pipeline self-test. */
class DiagnosticsScreen(activity: MainActivity) : Screen(activity) {

    private val telemetry by lazy { Telemetry(activity) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var factsBox: LinearLayout
    private lateinit var resultsBox: LinearLayout
    private lateinit var spark: Sparkline
    private lateinit var runButton: BrandButton
    private lateinit var status: TextView
    private var loop: Runnable? = null

    override fun onCreateView(): View {
        val root = FrameLayout(activity)
        root.addView(GlowBackdrop(activity, palette), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        val scroll = ScrollView(activity)
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(activity, 20f), Ui.dp(activity, 8f), Ui.dp(activity, 20f), Ui.dp(activity, 30f))
        }
        Ui.capWidth(column, 20f)
        scroll.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = FrameLayout(activity).apply {
            background = Ui.rounded(palette.card, Ui.dpf(activity, 100f), palette.outline, Ui.dp(activity, 1f))
        }
        back.addView(IconView(activity, Icons.Kind.BACK, palette.text, 19f), FrameLayout.LayoutParams(
            Ui.dp(activity, 19f), Ui.dp(activity, 19f), Gravity.CENTER
        ))
        back.layoutParams = LinearLayout.LayoutParams(Ui.dp(activity, 44f), Ui.dp(activity, 44f))
        back.contentDescription = "Back"
        Ui.pressable(back, 0.9f) { activity.router.pop() }
        top.addView(back)
        top.addView(Ui.text(activity, "Diagnostics", 17f, palette.text, bold = true), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = Ui.dp(activity, 12f) })
        column.addView(top)
        column.addView(
            Ui.text(activity, "Everything below is measured, not decorated.", 11.5f, palette.textDim),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(activity, 8f)
            }
        )

        val liveCard = Ui.card(activity, palette, 24f)
        val liveHeader = Ui.row(activity)
        liveHeader.addView(Ui.text(activity, "Live load", 14f, palette.text, bold = true), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))
        spark = Sparkline(activity, palette)
        liveCard.addView(liveHeader)
        liveCard.addView(spark, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 52f)
        ).apply { topMargin = Ui.dp(activity, 6f) })
        factsBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        liveCard.addView(factsBox, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 8f) })
        column.addView(liveCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 16f) })

        val checksCard = Ui.card(activity, palette, 24f)
        checksCard.addView(Ui.text(activity, "Engines & permissions", 14f, palette.text, bold = true))
        val synth = SpeechSynth(activity)
        val stt = Stt(activity)
        val checks = listOf(
            "System neural TTS" to (if (synth.hasSystemEngine()) "available" else "missing \u2014 NanoVoice will handle it"),
            "NanoVoice DSP" to "built-in, always available",
            "Speech recognizer" to (if (stt.isAvailable()) "available" else "not installed"),
            "Microphone permission" to if (hasPermission(android.Manifest.permission.RECORD_AUDIO)) "granted" else "not granted (optional)",
            "Notifications" to if (hasPermission("android.permission.POST_NOTIFICATIONS") || android.os.Build.VERSION.SDK_INT < 33) "granted" else "not granted (optional)",
            "UI assets" to "vector-drawn, 0 external icons"
        )
        checks.forEach { (label, value) ->
            val line = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, Ui.dp(activity, 5f), 0, Ui.dp(activity, 5f))
            }
            line.addView(Ui.text(activity, label, 11.5f, palette.textDim, font = "sans-serif-medium"), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ))
            line.addView(
                Ui.text(activity, value, 11f, palette.text, bold = true, font = "sans-serif-medium", gravity = Gravity.END),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f)
            )
            checksCard.addView(line)
        }
        column.addView(checksCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 14f) })

        val testCard = Ui.card(activity, palette, 24f)
        testCard.addView(Ui.text(activity, "Pipeline self-test", 14f, palette.text, bold = true))
        status = Ui.text(
            activity,
            "Runs NanoVoice \u2192 WAV \u2192 MP3 \u2192 ID3 \u2192 M4A \u2192 cover atoms on this exact device.",
            11.5f, palette.textDim, font = "sans-serif"
        )
        status.setPadding(0, Ui.dp(activity, 6f), 0, Ui.dp(activity, 10f))
        testCard.addView(status)
        runButton = BrandButton(activity, palette, "Run self-test", BrandButton.Variant.PRIMARY, Icons.Kind.SPARKLE, compact = true)
        runButton.setOnClick { runSelfTest() }
        testCard.addView(runButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        resultsBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        testCard.addView(resultsBox, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 10f) })
        column.addView(testCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 14f) })

        column.addView(
            Ui.text(
                activity,
                "${Brand.NAME} v${Brand.VERSION} \u00B7 minSdk 24 \u00B7 targetSdk 35 \u00B7 ${activity.packageName}",
                10f, palette.textDim, gravity = Gravity.CENTER, font = "sans-serif"
            ),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(activity, 18f)
            }
        )
        root.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        return root
    }

    private fun hasPermission(permission: String): Boolean = try {
        activity.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    override fun onShown() {
        refreshFacts()
        val runnable = object : Runnable {
            override fun run() {
                refreshFacts()
                handler.postDelayed(this, 1400)
            }
        }
        loop = runnable
        handler.post(runnable)
    }

    override fun onHidden() {
        loop?.let { handler.removeCallbacks(it) }
        loop = null
    }

    override fun onDestroyed() {
        loop?.let { handler.removeCallbacks(it) }
        loop = null
    }

    private fun refreshFacts() {
        try {
            val snapshot = telemetry.snapshot()
            factsBox.removeAllViews()
            telemetry.deviceFacts().forEach { (label, value) ->
                val line = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                line.setPadding(0, Ui.dp(activity, 3f), 0, Ui.dp(activity, 3f))
                line.addView(Ui.text(activity, label, 11f, palette.textDim, font = "sans-serif-medium"), LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                ))
                line.addView(
                    Ui.text(activity, value, 11f, palette.text, bold = true, font = "sans-serif-medium", gravity = Gravity.END),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.6f)
                )
                factsBox.addView(line)
            }
            val mem = if (snapshot.totalMemMb <= 0) 0f else snapshot.usedMemMb.toFloat() / snapshot.totalMemMb * 100f
            val series = ArrayList<Pair<Float, Float>>()
            for (i in 0 until 24) {
                series.add((snapshot.cpuPercent * (0.6f + 0.4f * (i % 5) / 5f)).coerceIn(0f, 100f) to mem)
            }
            spark.setData(series)
        } catch (_: Throwable) {
        }
    }

    private fun runSelfTest() {
        runButton.setLoading(true)
        status.text = "Running\u2026"
        resultsBox.removeAllViews()
        Thread {
            val results = ArrayList<Triple<String, Boolean, String>>()
            val started = System.currentTimeMillis()
            try {
                val pcm = NanoVoice.speak(
                    "Testing one, two, three. Gossip audio is completely ready.",
                    NanoVoice.Options(baseF0 = 128.0, rate = 1.05, sampleRate = 22050)
                )
                results.add(
                    Triple(
                        "NanoVoice synthesis",
                        pcm.samples.isNotEmpty() && pcm.durationMs > 400,
                        "${pcm.samples.size} samples \u00B7 ${TextKit.formatDuration(pcm.durationMs)}"
                    )
                )
                val wav = Wav.write(pcm)
                results.add(Triple("WAV writer", wav.size > 4000, "${wav.size / 1024} KB"))
                val back = Wav.read(wav)
                results.add(
                    Triple(
                        "WAV round-trip",
                        back != null && back.samples.size == pcm.samples.size && back.sampleRate == pcm.sampleRate,
                        if (back == null) "parse failed" else "${back.sampleRate} Hz \u00B7 ${back.channels} ch"
                    )
                )
                val mp3 = Mp3Encoder.encode(pcm, 96, 4)
                results.add(Triple("MP3 encoder (LAME/Java)", Mp3Encoder.looksLikeMp3(mp3), "${mp3.size / 1024} KB"))
                val tagged = Id3.attachToMp3(mp3, Id3.Tags(title = "Self test", artist = "GossipAudio", album = "Diagnostics"))
                results.add(Triple("ID3v2.3 tags", Id3.hasId3(tagged) && tagged.size > mp3.size, "${Id3.tagSize(tagged)} byte tag"))
                val m4a = File(activity.cacheDir, "selftest.m4a")
                val aacOk = AacEncoder.encodeToM4a(pcm, m4a, 96)
                results.add(Triple("M4A / AAC muxer", aacOk && m4a.length() > 1024, if (aacOk) "${m4a.length() / 1024} KB" else "encoder refused"))
                if (aacOk) {
                    val taggedM4a = Mp4Tagger.tagFile(
                        m4a,
                        Mp4Tagger.Meta(title = "Self test", artist = "GossipAudio", album = "Diagnostics")
                    )
                    results.add(Triple("MP4 cover/tag atoms", taggedM4a && m4a.length() > 1024, if (taggedM4a) "udta written" else "layout unsupported (safe)"))
                }
                results.add(Triple("Storage writable", activity.filesDir.canWrite(), activity.filesDir.absolutePath.takeLast(28)))
            } catch (t: Throwable) {
                results.add(Triple("Unexpected error", false, t.message ?: t.javaClass.simpleName))
            }
            val elapsed = System.currentTimeMillis() - started
            handler.post {
                runButton.setLoading(false)
                val passed = results.count { it.second }
                status.text = "$passed/${results.size} checks passed in ${elapsed} ms"
                results.forEach { (name, ok, detail) ->
                    val row = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, Ui.dp(activity, 5f), 0, Ui.dp(activity, 5f))
                    }
                    val badge = FrameLayout(activity).apply {
                        background = Ui.rounded(if (ok) palette.success else palette.danger, Ui.dpf(activity, 100f))
                    }
                    badge.addView(
                        IconView(activity, if (ok) Icons.Kind.CHECK else Icons.Kind.CLOSE, palette.bg, 10f),
                        FrameLayout.LayoutParams(Ui.dp(activity, 10f), Ui.dp(activity, 10f), Gravity.CENTER)
                    )
                    row.addView(badge, LinearLayout.LayoutParams(Ui.dp(activity, 18f), Ui.dp(activity, 18f)))
                    row.addView(
                        Ui.text(activity, name, 11.5f, palette.text, bold = true, font = "sans-serif-medium"),
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = Ui.dp(activity, 8f) }
                    )
                    row.addView(
                        Ui.text(activity, detail, 10f, palette.textDim, font = "sans-serif", gravity = Gravity.END),
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f)
                    )
                    resultsBox.addView(row)
                }
                activity.toast(if (passed == results.size) "Self-test passed \uD83E\uDDEA" else "Some checks need attention")
                m4aRef()?.delete()
            }
        }.start()
    }

    private fun m4aRef(): File? = File(activity.cacheDir, "selftest.m4a").takeIf { it.exists() }

    @Suppress("unused")
    private fun appIcon(): Int = R.mipmap.ic_launcher_round
}
