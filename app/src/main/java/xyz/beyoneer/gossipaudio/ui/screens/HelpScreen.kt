package xyz.beyoneer.gossipaudio.ui.screens

import android.animation.ObjectAnimator
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import xyz.beyoneer.gossipaudio.ui.Brand
import xyz.beyoneer.gossipaudio.ui.MainActivity
import xyz.beyoneer.gossipaudio.ui.Screen
import xyz.beyoneer.gossipaudio.ui.theme.Ui
import xyz.beyoneer.gossipaudio.ui.widgets.GlowBackdrop
import xyz.beyoneer.gossipaudio.ui.widgets.IconView
import xyz.beyoneer.gossipaudio.ui.widgets.Icons

/** Accordion FAQ — 24 answers, all honest. */
class HelpScreen(activity: MainActivity) : Screen(activity) {

    private val faq = listOf(
        "Is any of my text sent to a server?" to
            "Speech generation uses the bundled neural model. Your system recognition service may use the network under its own privacy policy.",
        "How do I download a clip?" to
            "Open the player, tap Download, pick WAV, MP3 or M4A, choose a quality, then Download. The file lands in Downloads/GossipAudio and the share sheet opens.",
        "Where do downloads go?" to
            "Downloads/GossipAudio on Android 10+. On Android 9 and below you choose the spot with the system picker.",
        "Does MP3 really work?" to
            "Yes. A full LAME encoder ported to pure Java runs inside the app, so 128 kbps MP3s are real MP3s, playable anywhere.",
        "What is the M4A option?" to
            "AAC audio inside an MP4 container, built with Android's MediaCodec + MediaMuxer, then tagged with title, artist and cover art.",
        "Can I add my own cover?" to
            "Yes. In the export sheet tap Pick image. It is embedded as an ID3 APIC frame in MP3 and a covr atom in M4A.",
        "Why do some voices sound robotic?" to
            "The app uses the full-precision Lessac neural model. Voice quality and pronunciation may still vary with text and playback devices.",
        "What is NanoVoice?" to
            "The old formant synthesizer is not used for speech generation. The new build uses a bundled English Piper VITS model.",
        "How many voices and styles?" to
            "One pretrained English neural voice, and eight speed styles. Other languages are not included.",
        "Can I change speed?" to
            "Drag the Speed slider in Studio (0.5x\u20132.0x) or use the speed chips in the player. Playback speed is separate and can be changed live.",
        "What does Pitch do?" to
            "Independent pitch control is not available with this neural model.",
        "What is the audio-image export?" to
            "M4A with embedded artwork. It behaves like a mini audio file with a picture attached, which is what social apps expect when you share.",
        "How does speech-to-text work?" to
            "Tap the mic in Studio. The system recogniser turns speech into text. Its provider may use a network even when offline recognition is preferred.",
        "Do I need the microphone permission?" to
            "Only for dictation. The app works perfectly without it; the mic button explains what it needs and asks once.",
        "Why no notification?" to
            "Android 13+ needs permission. Settings \u2192 Notifications, or tap the bell in the top bar.",
        "What does the daily reminder do?" to
            "Sends one friendly nudge a day at the time you pick, using the system alarm scheduler. No tracking, no streaks, no guilt.",
        "Can playback continue in the background?" to
            "Yes. A foreground service keeps audio alive with a media notification, lock-screen controls and a sleep timer.",
        "How do I like a clip?" to
            "Tap the heart on any card, in the player, or in the long-press menu. Favourites filter is in Library.",
        "Can I delete files?" to
            "Yes, individually or all at once. Exports you already downloaded are never touched, because those files belong to you.",
        "Where is the app data stored?" to
            "Private app storage: clips, covers and a small JSON index. Clear it any time from Android settings.",
        "Does it work offline?" to
            "Neural speech generation and encoding run on-device. Dictation depends on the system recognizer and its network behavior.",
        "Why is the app so yellow?" to
            "It is the brand. Yellow buttons, thin darker-yellow outlines, black text on light surfaces and white text on black. Very legible, very loud.",
        "How do I verify it actually works?" to
            "Open Settings \u2192 Diagnostics \u2192 Run self-test. It synthesises audio, writes WAV, encodes MP3, writes ID3, muxes M4A and reports each step.",
        "Can I use these audio files commercially?" to
            "Review your voice provider’s terms and applicable laws. The MP3 encoder is LGPL-2.1; see the Licenses entry in About."
    )

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
        top.addView(Ui.text(activity, "Help & FAQ", 17f, palette.text, bold = true), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = Ui.dp(activity, 12f) })
        column.addView(top)

        column.addView(
            Ui.text(activity, "${faq.size} questions, zero fine print.", 11.5f, palette.textDim),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(activity, 10f)
            }
        )

        faq.forEachIndexed { index, (question, answer) ->
            val card = Ui.card(activity, palette, 20f)
            card.elevation = 0f
            val header = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
            }
            val number = Ui.text(activity, "%02d".format(index + 1), 11f, palette.yellowDeep, bold = true, font = "sans-serif-medium")
            header.addView(number, LinearLayout.LayoutParams(Ui.dp(activity, 26f), ViewGroup.LayoutParams.WRAP_CONTENT))
            val questionView = Ui.text(activity, question, 12.5f, palette.text, bold = true, font = "sans-serif-medium")
            header.addView(questionView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val chevron = IconView(activity, Icons.Kind.NEXT, palette.textDim, 13f)
            chevron.rotation = 90f
            header.addView(chevron, LinearLayout.LayoutParams(Ui.dp(activity, 14f), Ui.dp(activity, 14f)))
            card.addView(header)

            val answerView = Ui.text(activity, answer, 11.5f, palette.textDim, font = "sans-serif")
            answerView.setLineSpacing(Ui.dpf(activity, 3f), 1f)
            answerView.visibility = if (index == 0) View.VISIBLE else View.GONE
            card.addView(answerView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = Ui.dp(activity, 7f) })

            header.setOnClickListener {
                val open = answerView.visibility == View.VISIBLE
                if (open) {
                    Ui.fadeOutShrink(answerView)
                    chevron.animate().rotation(90f).setDuration(180).start()
                } else {
                    answerView.visibility = View.VISIBLE
                    Ui.fadeInUp(answerView, 0, 4f)
                    chevron.animate().rotation(270f).setDuration(180).start()
                }
            }
            column.addView(card, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = Ui.dp(activity, 8f) })
        }

        column.addView(
            Ui.text(
                activity,
                "Still stuck? Ask a human: ${Brand.AUTHOR} ships this app as a one-person studio.",
                10.5f, palette.textDim, gravity = Gravity.CENTER, font = "sans-serif"
            ),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(activity, 14f)
            }
        )
        root.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        return root
    }

    @Suppress("unused")
    private fun unusedAnimation(): ObjectAnimator? = null

    @Suppress("unused")
    private fun unusedTv(): TextView? = null
}
