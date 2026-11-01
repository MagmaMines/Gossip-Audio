package xyz.beyoneer.gossipaudio.ui.tabs

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import xyz.beyoneer.gossipaudio.core.VoiceCatalog
import xyz.beyoneer.gossipaudio.core.VoiceGender
import xyz.beyoneer.gossipaudio.core.VoiceProfile
import xyz.beyoneer.gossipaudio.ui.Tab
import xyz.beyoneer.gossipaudio.ui.VoicePreview
import xyz.beyoneer.gossipaudio.ui.screens.ShellScreen
import xyz.beyoneer.gossipaudio.ui.theme.Ui
import xyz.beyoneer.gossipaudio.ui.widgets.AvatarView
import xyz.beyoneer.gossipaudio.ui.widgets.BrandButton
import xyz.beyoneer.gossipaudio.ui.widgets.Chip
import xyz.beyoneer.gossipaudio.ui.widgets.IconView
import xyz.beyoneer.gossipaudio.ui.widgets.Icons

/** The voice library: ten personalities you can audition and adopt. */
class VoicesTab(private val shell: ShellScreen) : Tab {

    private val activity get() = shell.act
    private val palette get() = shell.colors

    private lateinit var list: LinearLayout
    private var filter: VoiceGender? = null
    private var previewing: String? = null
    private val filterChips = ArrayList<Pair<VoiceGender?, Chip>>()

    override val root: View by lazy { build() }

    override fun onHide() = Unit

    override fun onShow() {
        render()
    }

    private fun build(): View {
        val scroll = ScrollView(activity)
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(activity, 18f), Ui.dp(activity, 8f), Ui.dp(activity, 18f), Ui.dp(activity, 30f))
        }
        Ui.capWidth(column)
        scroll.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        column.addView(Ui.text(activity, "Your voice", 25f, palette.text, bold = true))
        column.addView(
            Ui.text(
                activity,
                "One full-precision English neural voice, bundled in the app. Tap Preview to hear it.",
                12.5f, palette.textDim, font = "sans-serif-medium"
            ),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(activity, 3f)
            }
        )

        list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        column.addView(list, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 14f) })
        return scroll
    }

    private fun render() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        val voices = VoiceCatalog.all.filter { filter == null || it.gender == filter }
        voices.forEach { voice ->
            list.addView(voiceCard(voice), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = Ui.dp(activity, 12f) })
        }
    }

    private fun voiceCard(voice: VoiceProfile): View {
        val selected = VoiceCatalog.byId(activity.app.prefs.selectedVoiceId).id == voice.id
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = if (selected) {
                Ui.gradient(
                    intArrayOf(Ui.blend(palette.card, palette.yellowSoft, 0.55f), palette.card),
                    Ui.dpf(activity, 24f),
                    strokeColor = palette.yellow, strokeWidth = Ui.dp(activity, 2f)
                )
            } else {
                Ui.rounded(palette.card, Ui.dpf(activity, 24f), palette.outline, Ui.dp(activity, 1f))
            }
            setPadding(Ui.dp(activity, 14f), Ui.dp(activity, 14f), Ui.dp(activity, 14f), Ui.dp(activity, 12f))
            elevation = Ui.dpf(activity, if (selected) 4f else 1f)
        }
        val header = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        header.addView(AvatarView(activity, palette, voice.name, voice.accent, 52f, selected), LinearLayout.LayoutParams(
            Ui.dp(activity, 52f), Ui.dp(activity, 52f)
        ))
        val info = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(activity, 12f), 0, 0, 0)
        }
        val nameRow = Ui.row(activity)
        nameRow.addView(Ui.text(activity, voice.name, 17f, palette.text, bold = true))
        val flag = Ui.text(activity, "  ${voice.flag} ${voice.region}", 11.5f, palette.textDim, bold = true, font = "sans-serif-medium")
        flag.setPadding(0, Ui.dp(activity, 3f), 0, 0)
        nameRow.addView(flag)
        info.addView(nameRow)
        val metaRow = Ui.row(activity)
        val genderChip = Ui.text(activity, voice.genderLabel, 9.5f, palette.onYellow, bold = true, font = "sans-serif-black")
        genderChip.setPadding(Ui.dp(activity, 7f), Ui.dp(activity, 2f), Ui.dp(activity, 7f), Ui.dp(activity, 2f))
        genderChip.background = Ui.rounded(voice.accent, Ui.dpf(activity, 100f))
        metaRow.addView(genderChip)
        val localeChip = Ui.text(activity, "  ${voice.locale}", 10f, palette.textDim, font = "sans-serif-medium")
        metaRow.addView(localeChip)
        info.addView(metaRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 5f) })
        info.addView(
            Ui.text(activity, voice.vibe, 11f, palette.yellowDeep, bold = true, font = "sans-serif-medium"),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(activity, 4f)
            }
        )
        header.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (selected) {
            val check = FrameLayout(activity).apply {
                background = Ui.rounded(palette.yellow, Ui.dpf(activity, 100f))
            }
            check.addView(IconView(activity, Icons.Kind.CHECK, palette.onYellow, 15f), FrameLayout.LayoutParams(
                Ui.dp(activity, 15f), Ui.dp(activity, 15f), Gravity.CENTER
            ))
            header.addView(check, LinearLayout.LayoutParams(Ui.dp(activity, 26f), Ui.dp(activity, 26f)))
        }
        card.addView(header)

        val bio = Ui.text(activity, voice.bio, 11.5f, palette.textDim, font = "sans-serif")
        bio.setLineSpacing(Ui.dpf(activity, 3f), 1f)
        bio.setPadding(0, Ui.dp(activity, 10f), 0, Ui.dp(activity, 4f))
        card.addView(bio)

        val actions = Ui.row(activity)
        val preview = BrandButton(activity, palette, "Preview", BrandButton.Variant.GHOST, Icons.Kind.PLAY, compact = true)
        val use = BrandButton(
            activity, palette,
            if (selected) "Selected" else "Use this voice",
            if (selected) BrandButton.Variant.INK else BrandButton.Variant.PRIMARY,
            if (selected) Icons.Kind.CHECK else Icons.Kind.SPARKLE,
            compact = true
        )
        actions.addView(preview)
        actions.addView(Ui.space(activity, 1), LinearLayout.LayoutParams(0, 1, 1f))
        actions.addView(use)
        card.addView(actions, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 10f) })

        // The whole card selects the voice — nobody should have to aim at a button.
        card.isClickable = true
        card.setOnClickListener {
            activity.app.prefs.selectedVoiceId = voice.id
            render()
            activity.toast("${voice.name} is now your gossip voice.")
        }
        card.contentDescription = "${voice.name}, ${voice.genderLabel}, ${voice.region}. ${voice.vibe}"
        Ui.pressable(card, 0.985f)

        preview.setOnClick {
            if (previewing == voice.id) {
                activity.toast("Already preparing that sample\u2026")
                return@setOnClick
            }
            previewing = voice.id
            preview.setLoading(true)
            VoicePreview.preview(activity, voice) { state ->
                when (state) {
                    VoicePreview.State.LOADING -> Unit
                    VoicePreview.State.PLAYING -> {
                        previewing = null
                        preview.setLoading(false)
                        activity.toast("${voice.name}: \u201C${voice.vibe}\u201D")
                    }
                    else -> {
                        previewing = null
                        preview.setLoading(false)
                    }
                }
            }
        }
        use.setOnClick {
            activity.app.prefs.selectedVoiceId = voice.id
            render()
            activity.toast("${voice.name} is now your gossip voice.")
        }
        return card
    }
}
