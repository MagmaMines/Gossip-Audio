package xyz.beyoneer.gossipaudio.ui.screens

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import xyz.beyoneer.gossipaudio.ui.Brand
import xyz.beyoneer.gossipaudio.ui.MainActivity
import xyz.beyoneer.gossipaudio.ui.Screen
import xyz.beyoneer.gossipaudio.ui.theme.Ui
import xyz.beyoneer.gossipaudio.ui.widgets.BrandButton
import xyz.beyoneer.gossipaudio.ui.widgets.GlowBackdrop
import xyz.beyoneer.gossipaudio.ui.widgets.IconView
import xyz.beyoneer.gossipaudio.ui.widgets.Icons

/** Three friendly pages; skippable; sets the tone before anyone gossips. */
class OnboardingScreen(activity: MainActivity) : Screen(activity) {

    private var index = 0
    private lateinit var artHolder: FrameLayout
    private lateinit var titleView: android.widget.TextView
    private lateinit var bodyView: android.widget.TextView
    private lateinit var dots: LinearLayout
    private lateinit var nextButton: BrandButton

    private data class Page(val title: String, val body: String, val icon: Icons.Kind, val accent: Int)

    private val pages = listOf(
        Page(
            "Type the tea.\nWe do the talking.",
            "Write anything \u2014 a rumour, a roast, your group chat at 2 a.m. \u2014 and GossipAudio turns it into a voice note you can actually send.",
            Icons.Kind.MIC,
            palette.yellow
        ),
        Page(
            "More voices.\nNeural models.",
            "Preview the bundled voice or download British English, American English and Telugu. Speech generation runs on-device, without phone TTS.",
            Icons.Kind.PERSON,
            palette.yellowBright
        ),
        Page(
            "Download it.\nIt is actually free.",
            "Export lossless WAV, proper MP3 with cover art, or M4A with an audio image. Save to Downloads or share. Speech generation runs locally; dictation uses your system recognizer.",
            Icons.Kind.DOWNLOAD,
            palette.yellow
        )
    )

    override fun onCreateView(): View {
        val root = FrameLayout(activity)
        root.addView(GlowBackdrop(activity, palette), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(activity, 24f), Ui.dp(activity, 28f), Ui.dp(activity, 24f), Ui.dp(activity, 28f))
        }

        val top = Ui.row(activity)
        val brandRow = Ui.row(activity)
        val logo = android.widget.ImageView(activity).apply {
            setImageResource(xyz.beyoneer.gossipaudio.R.drawable.gossipaudio_glyph)
        }
        brandRow.addView(logo, LinearLayout.LayoutParams(Ui.dp(activity, 30f), Ui.dp(activity, 30f)))
        val name = Ui.text(activity, Brand.NAME, 15f, palette.text, bold = true)
        name.setPadding(Ui.dp(activity, 8f), 0, 0, 0)
        brandRow.addView(name)
        top.addView(brandRow, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val skip = Ui.text(activity, "Skip", 13f, palette.textDim, bold = true)
        skip.setPadding(Ui.dp(activity, 12f), Ui.dp(activity, 6f), 0, Ui.dp(activity, 6f))
        Ui.pressable(skip) { finish() }
        top.addView(skip)
        column.addView(top)

        artHolder = FrameLayout(activity)
        column.addView(artHolder, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 250f)
        ).apply { topMargin = Ui.dp(activity, 10f) })

        titleView = Ui.text(activity, "", 30f, palette.text, bold = true, font = "sans-serif-black")
        titleView.setLineSpacing(Ui.dpf(activity, 2f), 1f)
        column.addView(titleView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 8f) })

        bodyView = Ui.text(activity, "", 14.5f, palette.textDim, font = "sans-serif")
        bodyView.setLineSpacing(Ui.dpf(activity, 5f), 1f)
        column.addView(bodyView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 14f) })

        column.addView(Ui.space(activity, 1), LinearLayout.LayoutParams(1, 0, 1f))

        dots = Ui.row(activity)
        dots.gravity = Gravity.CENTER
        column.addView(dots, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = Ui.dp(activity, 16f) })

        nextButton = BrandButton(activity, palette, "Next", BrandButton.Variant.PRIMARY, Icons.Kind.NEXT)
        nextButton.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        nextButton.setOnClick { advance() }
        column.addView(nextButton)

        // Scroll on short screens or with enlarged fonts instead of clipping controls.
        val scroll = android.widget.ScrollView(activity).apply { isFillViewport = true }
        scroll.addView(column, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        render(false)
        return root
    }

    private fun render(animated: Boolean) {
        val page = pages[index]
        artHolder.removeAllViews()
        val orb = FrameLayout(activity)
        val circle = View(activity).apply {
            background = Ui.gradient(
                intArrayOf(Ui.blend(page.accent, Color.WHITE, 0.35f), page.accent, palette.yellowDeep),
                Ui.dpf(activity, 200f),
                strokeColor = palette.yellowDeep, strokeWidth = Ui.dp(activity, 2f)
            )
            elevation = Ui.dpf(activity, 6f)
        }
        val size = Ui.dp(activity, 172f)
        orb.addView(circle, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        val icon = IconView(activity, page.icon, palette.onYellow, 64f, 0.11f)
        orb.addView(icon, FrameLayout.LayoutParams(Ui.dp(activity, 72f), Ui.dp(activity, 72f), Gravity.CENTER))
        artHolder.addView(orb, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // orbiting accents
        for (i in 0 until 4) {
            val dot = View(activity).apply {
                background = Ui.rounded(if (i % 2 == 0) palette.yellow else palette.textDim, Ui.dpf(activity, 20f))
                alpha = 0.7f
            }
            val s = Ui.dp(activity, if (i % 2 == 0) 9f else 6f)
            val lp = FrameLayout.LayoutParams(s, s, Gravity.CENTER)
            val angle = i * Math.PI / 2 + 0.6
            lp.leftMargin = (Math.cos(angle) * Ui.dp(activity, 108f)).toInt()
            lp.topMargin = (Math.sin(angle) * Ui.dp(activity, 108f)).toInt()
            orb.addView(dot, lp)
        }

        titleView.text = page.title
        bodyView.text = page.body
        dots.removeAllViews()
        for (i in pages.indices) {
            val dot = View(activity).apply {
                background = Ui.rounded(
                    if (i == index) palette.yellow else palette.outline,
                    Ui.dpf(activity, 20f),
                    if (i == index) palette.yellowDeep else null,
                    if (i == index) Ui.dp(activity, 1.5f) else 0
                )
            }
            val w = if (i == index) Ui.dp(activity, 26f) else Ui.dp(activity, 8f)
            dots.addView(dot, LinearLayout.LayoutParams(w, Ui.dp(activity, 8f)).apply {
                marginEnd = Ui.dp(activity, 7f)
            })
        }
        nextButton.setText(if (index == pages.size - 1) "Start gossiping" else "Next")
        if (animated && !activity.prefs.reduceMotion) {
            Ui.popIn(artHolder)
            Ui.fadeInUp(titleView, 40)
            Ui.fadeInUp(bodyView, 90)
            Ui.fadeInUp(dots, 130)
        }
    }

    private fun advance() {
        if (index < pages.size - 1) {
            index++
            render(true)
        } else {
            finish()
        }
    }

    private fun finish() {
        activity.prefs.onboardingDone = true
        activity.router.start(ShellScreen(activity))
    }
}
