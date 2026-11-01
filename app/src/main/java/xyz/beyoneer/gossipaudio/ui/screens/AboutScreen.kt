package xyz.beyoneer.gossipaudio.ui.screens

import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import xyz.beyoneer.gossipaudio.R
import xyz.beyoneer.gossipaudio.ui.Brand
import xyz.beyoneer.gossipaudio.ui.MainActivity
import xyz.beyoneer.gossipaudio.ui.Overlays
import xyz.beyoneer.gossipaudio.ui.Screen
import xyz.beyoneer.gossipaudio.ui.theme.Ui
import xyz.beyoneer.gossipaudio.ui.widgets.BrandButton
import xyz.beyoneer.gossipaudio.ui.widgets.GlowBackdrop
import xyz.beyoneer.gossipaudio.ui.widgets.IconView
import xyz.beyoneer.gossipaudio.ui.widgets.Icons

/** The "who, what, why" screen — including the full feature list. */
class AboutScreen(activity: MainActivity) : Screen(activity) {

    private val features = listOf(
        "\uD83C\uDF99 10 voice personalities",
        "\uD83D\uDC69 4 female \u00B7 5 male \u00B7 1 bot",
        "\uD83C\uDDEE\uD83C\uDDF3 Indian accents",
        "\uD83C\uDDFA\uD83C\uDDF8 American accents",
        "\uD83C\uDDEF\uD83C\uDDF5 Japanese accents",
        "\uD83C\uDDEC\uD83C\uDDE7 British accent",
        "\uD83C\uDFAD 8 emotion presets",
        "\u26A1 Speed 0.5x \u2013 2.0x",
        "\uD83C\uDF9A Pitch fine-tune",
        "\uD83C\uDFB2 Random gossip generator",
        "\uD83C\uDFA4 Speech-to-text dictation",
        "\uD83D\uDCE5 WAV export",
        "\uD83C\uDFB5 MP3 export",
        "\uD83C\uDFBC M4A / AAC export",
        "\uD83D\uDDBC Cover art embedding",
        "\uD83D\uDCF1 Save to Downloads",
        "\uD83D\uDCE4 One-tap sharing",
        "\u2764\uFE0F Favourites",
        "\uD83D\uDD0D Search your library",
        "\u270F\uFE0F Rename & delete",
        "\uD83C\uDFA7 Background playback",
        "\uD83D\uDD14 Playback notification",
        "\u23F0 Sleep timer",
        "\uD83C\uDF9A Clarity EQ (no bass boost)",
        "\uD83D\uDCCA Live CPU/RAM monitor",
        "\uD83E\uDDEA Built-in self-test",
        "\uD83C\uDF19 Light / dark / system theme",
        "\uD83E\uDD8B Butterfly animations"
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

        column.addView(topBar("About"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val logoWrap = FrameLayout(activity)
        val logo = ImageView(activity).apply { setImageResource(R.drawable.gossipaudio_glyph) }
        logoWrap.addView(logo, FrameLayout.LayoutParams(Ui.dp(activity, 108f), Ui.dp(activity, 108f), Gravity.CENTER))
        column.addView(logoWrap, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 118f)
        ).apply { topMargin = Ui.dp(activity, 8f) })

        column.addView(
            Ui.text(activity, Brand.NAME, 27f, palette.text, bold = true, gravity = Gravity.CENTER),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        column.addView(
            Ui.text(activity, "v${Brand.VERSION} \u00B7 ${Brand.BYLINE}", 12f, palette.textDim, bold = true, font = "sans-serif-medium", gravity = Gravity.CENTER),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(activity, 3f)
            }
        )

        val tagline = Ui.text(activity, "\u201C${Brand.TAGLINE}\u201D", 12.5f, palette.text, font = "sans-serif-condensed", gravity = Gravity.CENTER)
        tagline.setLineSpacing(Ui.dpf(activity, 3f), 1f)
        val taglineCard = Ui.card(activity, palette, 22f)
        taglineCard.addView(tagline)
        column.addView(taglineCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 16f) })

        val about = Ui.text(
            activity,
            "${Brand.NAME} is a tiny, offline-first voice studio. You type the gossip, it does the talking in ten very different voices, then hands you a WAV, MP3 or M4A you can keep forever. Nothing is uploaded, nothing needs an account, and the only subscription is your friend group's patience.",
            12.5f, palette.textDim, font = "sans-serif"
        )
        about.setLineSpacing(Ui.dpf(activity, 4f), 1f)
        column.addView(about, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 16f) })

        column.addView(
            Ui.text(activity, "Everything inside (${features.size})", 15f, palette.text, bold = true),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(activity, 20f)
            }
        )
        val grid = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        features.chunked(2).forEach { pair ->
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { feature ->
                val chip = Ui.text(activity, feature, 10.5f, palette.text, font = "sans-serif-medium")
                chip.setPadding(Ui.dp(activity, 10f), Ui.dp(activity, 8f), Ui.dp(activity, 10f), Ui.dp(activity, 8f))
                chip.background = Ui.rounded(palette.card, Ui.dpf(activity, 14f), palette.outline, Ui.dp(activity, 1f))
                row.addView(chip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = Ui.dp(activity, 6f)
                })
            }
            if (pair.size == 1) {
                row.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
            }
            grid.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = Ui.dp(activity, 6f) })
        }
        column.addView(grid, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 8f) })

        column.addView(
            Ui.text(activity, "Under the hood", 15f, palette.text, bold = true),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(activity, 18f)
            }
        )
        val tech = Ui.card(activity, palette, 22f)
        listOf(
            "Voice engine" to "On-device neural TTS + NanoVoice DSP fallback",
            "MP3 encoder" to "LAME ported to pure Java (no NDK)",
            "M4A encoder" to "Android MediaCodec AAC + MP4 metadata",
            "Cover art" to "ID3 APIC + iTunes covr atoms",
            "Threading" to "Main-thread UI, background synthesis",
            "Permissions" to "Mic (optional), notifications (optional)"
        ).forEachIndexed { index, row ->
            val line = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, Ui.dp(activity, 6f), 0, Ui.dp(activity, 6f))
            }
            line.addView(Ui.text(activity, row.first, 11f, palette.textDim, font = "sans-serif-medium"), LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ))
            line.addView(
                Ui.text(activity, row.second, 11f, palette.text, bold = true, font = "sans-serif-medium", gravity = Gravity.END),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f)
            )
            tech.addView(line)
            if (index < 5) {
                tech.addView(View(activity).apply { setBackgroundColor(Ui.withAlpha(palette.outline, 0.6f)) }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1
                ))
            }
        }
        column.addView(tech, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 8f) })

        val actions = Ui.row(activity)
        val share = BrandButton(activity, palette, "Share app", BrandButton.Variant.PRIMARY, Icons.Kind.SHARE, compact = true)
        share.setOnClick {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "${Brand.NAME} by ${Brand.AUTHOR} \u2014 ${Brand.TAGLINE}")
            }
            activity.startActivity(Intent.createChooser(intent, "Share GossipAudio"))
        }
        val licenses = BrandButton(activity, palette, "Licenses", BrandButton.Variant.GHOST, Icons.Kind.INFO, compact = true)
        licenses.setOnClick {
            Overlays.info(
                activity, "Open-source licenses",
                listOf(
                    "GossipAudio" to "\u00A9 2025 Beyoneer XYZ",
                    "jump3r / LAME" to "LGPL-2.1 (MP3 encoding)",
                    "AndroidX" to "not used",
                    "Fonts / icons" to "system fonts + hand-drawn vectors",
                    "Butterfly" to "drawn in code, no assets"
                )
            )
        }
        actions.addView(share)
        actions.addView(Ui.space(activity, Ui.dp(activity, 8f)))
        actions.addView(licenses)
        column.addView(actions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 18f) })

        for ((label, action) in listOf<Pair<String, () -> Unit>>(
            "Help & FAQ" to { activity.router.push(HelpScreen(activity)) },
            "Diagnostics & self-test" to { activity.router.push(DiagnosticsScreen(activity)) }
        )) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(activity, 14f), Ui.dp(activity, 14f), Ui.dp(activity, 14f), Ui.dp(activity, 14f))
                background = Ui.rounded(palette.card, Ui.dpf(activity, 18f), palette.outline, Ui.dp(activity, 1f))
                isClickable = true
            }
            row.addView(IconView(activity, Icons.Kind.HELP, palette.text, 17f), LinearLayout.LayoutParams(
                Ui.dp(activity, 18f), Ui.dp(activity, 18f)
            ))
            row.addView(
                Ui.text(activity, label, 13.5f, palette.text, bold = true, font = "sans-serif-medium"),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = Ui.dp(activity, 10f) }
            )
            row.addView(IconView(activity, Icons.Kind.NEXT, palette.textDim, 13f), LinearLayout.LayoutParams(
                Ui.dp(activity, 14f), Ui.dp(activity, 14f)
            ))
            Ui.pressable(row, 0.99f, action)
            column.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = Ui.dp(activity, 8f) })
        }

        val footer = Ui.text(
            activity,
            "\u00A9 2025 ${Brand.AUTHOR}. Made with \u2764, zero cloud and an unreasonable amount of yellow.",
            10f, palette.textDim, gravity = Gravity.CENTER, font = "sans-serif"
        )
        footer.setPadding(0, Ui.dp(activity, 24f), 0, 0)
        column.addView(footer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        return root
    }

    private fun topBar(title: String): View {
        val row = LinearLayout(activity).apply {
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
        row.addView(back)
        row.addView(
            Ui.text(activity, title, 17f, palette.text, bold = true),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = Ui.dp(activity, 12f)
            }
        )
        return row
    }

    @Suppress("unused")
    private fun accent(): Int = Color.TRANSPARENT
}
