package xyz.beyoneer.gossipaudio.ui

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import xyz.beyoneer.gossipaudio.audio.PlaybackHub
import xyz.beyoneer.gossipaudio.core.StyleCatalog
import xyz.beyoneer.gossipaudio.core.VoiceCatalog
import xyz.beyoneer.gossipaudio.core.VoiceEngine
import xyz.beyoneer.gossipaudio.core.Wav
import xyz.beyoneer.gossipaudio.speech.SpeechSynth
import xyz.beyoneer.gossipaudio.ui.theme.Ui
import xyz.beyoneer.gossipaudio.ui.widgets.BrandButton
import xyz.beyoneer.gossipaudio.ui.widgets.Icons
import java.io.File

/**
 * A/B, out loud.
 *
 * Every audio complaint so far has been argued with numbers. This is the thing that
 * settles it: render one sentence with the phone's own neural voice and with NanoVoice,
 * then play them back to back and say which one you want to keep. If the device voice
 * wins (it usually does), the app already prefers it and the fallback is only a safety
 * net — and if it loses, at least it lost in public.
 */
object VoiceCompare {

    private const val PROBE = "Btw we're not gossiping, in real life. Your face is funny though."

    fun open(activity: MainActivity) {
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val picked = VoiceCatalog.byId(activity.app.prefs.selectedVoiceId)
        content.addView(
            Ui.text(
                activity,
                "Both clips say the same sentence, recorded back to back. " +
                    "First the device voice, then NanoVoice. Headphones on.",
                12f, activity.palette.textDim
            ).apply { setLineSpacing(Ui.dpf(activity, 3f), 1f) }
        )

        val device = BrandButton(
            activity, activity.palette, "Play: device voice",
            BrandButton.Variant.PRIMARY, Icons.Kind.PLAY, compact = true
        )
        val nano = BrandButton(
            activity, activity.palette, "Play: NanoVoice",
            BrandButton.Variant.INK, Icons.Kind.WAVE, compact = true
        )
        val status = Ui.text(activity, "", 11f, activity.palette.textDim, gravity = Gravity.CENTER)

        val renderer: (VoiceEngine, BrandButton, BrandButton) -> Unit = { engine, busyButton, other ->
            busyButton.setLoading(true)
            other.isEnabled = false
            val voice = if (engine == VoiceEngine.NANO_VOICE) {
                picked.copy(engine = VoiceEngine.NANO_VOICE, name = picked.name + " (NanoVoice)")
            } else {
                picked.copy(engine = VoiceEngine.SYSTEM_TTS)
            }
            val synth = SpeechSynth(activity)
            synth.synthesize(PROBE, voice, StyleCatalog.all.first(), 1f, 1f, object : SpeechSynth.Callback {
                override fun onProgress(fraction: Float, label: String) {
                    status.text = label
                }

                override fun onSuccess(outcome: SpeechSynth.Outcome) {
                    val file = File(activity.cacheDir, "compare-${outcome.engine}.wav")
                    file.writeBytes(Wav.write(outcome.pcm))
                    busyButton.setLoading(false)
                    other.isEnabled = true
                    status.text = "${labelFor(outcome.engine)} \u00B7 ${outcome.tookMs} ms"
                    PlaybackHub.playFile(file, "compare-${outcome.engine}", labelFor(outcome.engine), null)
                    activity.toast("${labelFor(outcome.engine)} \uD83C\uDFA7")
                }

                override fun onError(message: String) {
                    busyButton.setLoading(false)
                    other.isEnabled = true
                    status.text = message
                    activity.toast(message)
                }
            })
        }
        device.setOnClick { renderer(VoiceEngine.SYSTEM_TTS, device, nano) }
        nano.setOnClick { renderer(VoiceEngine.NANO_VOICE, nano, device) }

        val both = BrandButton(
            activity, activity.palette, "Play both, A then B",
            BrandButton.Variant.GHOST, Icons.Kind.WAVE, compact = true
        )
        // Renders sequentially: the device voice first, NanoVoice 700 ms after it starts.
        both.setOnClick {
            both.setLoading(true)
            val handler = Handler(Looper.getMainLooper())
            renderBoth(activity, picked) { first, second, note ->
                both.setLoading(false)
                if (first == null && second == null) {
                    activity.toast(note ?: "Nothing rendered")
                    return@renderBoth
                }
                first?.let {
                    status.text = "A \u00B7 device voice"
                    PlaybackHub.playFile(it, "compare-a", "A \u00B7 device voice", null)
                }
                handler.postDelayed({
                    second?.let {
                        status.text = "B \u00B7 NanoVoice"
                        PlaybackHub.playFile(it, "compare-b", "B \u00B7 NanoVoice", null)
                    }
                }, (first?.let { pcmOf(it) } ?: 900L))
            }
        }

        listOf(device, nano, both).forEachIndexed { index, button ->
            content.addView(button, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = Ui.dp(activity, if (index == 0) 14f else 8f) })
        }
        content.addView(status, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 10f) })
        content.addView(
            Ui.text(
                activity,
                "If the device voice is missing a language, Android can download one for free: " +
                    "Settings \u2192 System \u2192 Languages \u2192 Text-to-speech \u2192 Install voice data.",
                10.5f, activity.palette.textDim
            ).apply { setLineSpacing(Ui.dpf(activity, 2.5f), 1f) },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = Ui.dp(activity, 14f) }
        )
        Overlays.sheetWithClose(activity, "Compare the engines", content)
    }

    private fun labelFor(engine: String): String = when (engine) {
        "system" -> "A \u00B7 device voice"
        "nano" -> "B \u00B7 NanoVoice"
        else -> engine
    }

    /** Duration of a rendered comparison file, so A and B never overlap. */
    private fun pcmOf(file: File): Long = try {
        val pcm = Wav.read(file.readBytes()) ?: return 1200L
        pcm.durationMs + 250L
    } catch (_: Throwable) {
        1200L
    }

    private fun renderBoth(
        activity: MainActivity,
        voice: xyz.beyoneer.gossipaudio.core.VoiceProfile,
        done: (File?, File?, String?) -> Unit
    ) {
        val out = arrayOfNulls<File>(2)
        var remaining = 2
        var note: String? = null
        val handler = Handler(Looper.getMainLooper())

        fun step(index: Int, engine: VoiceEngine) {
            val profile = if (engine == VoiceEngine.NANO_VOICE) {
                voice.copy(engine = VoiceEngine.NANO_VOICE)
            } else {
                voice.copy(engine = VoiceEngine.SYSTEM_TTS)
            }
            SpeechSynth(activity).synthesize(
                PROBE, profile, StyleCatalog.all.first(), 1f, 1f,
                object : SpeechSynth.Callback {
                    override fun onProgress(fraction: Float, label: String) = Unit
                    override fun onSuccess(outcome: SpeechSynth.Outcome) {
                        val file = File(activity.cacheDir, "compare-$index.wav")
                        file.writeBytes(Wav.write(outcome.pcm))
                        out[index] = file
                        finish()
                    }

                    override fun onError(message: String) {
                        note = message
                        finish()
                    }
                }
            )
        }

        fun finish() {
            remaining--
            if (remaining <= 0) {
                handler.post { done(out[0], out[1], note) }
            }
        }

        step(0, VoiceEngine.SYSTEM_TTS)
        // NanoVoice is CPU-only and fast; the device voice can take a second to spin up,
        // so they run in parallel instead of queueing behind each other.
        step(1, VoiceEngine.NANO_VOICE)
    }
}
