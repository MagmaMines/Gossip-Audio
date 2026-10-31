package xyz.beyoneer.gossipaudio.ui.tabs

import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import xyz.beyoneer.gossipaudio.audio.PlaybackHub
import xyz.beyoneer.gossipaudio.core.TextKit
import xyz.beyoneer.gossipaudio.core.VoiceCatalog
import xyz.beyoneer.gossipaudio.data.Clip
import xyz.beyoneer.gossipaudio.ui.ClipActions
import xyz.beyoneer.gossipaudio.ui.Overlays
import xyz.beyoneer.gossipaudio.ui.Tab
import xyz.beyoneer.gossipaudio.ui.screens.PlayerScreen
import xyz.beyoneer.gossipaudio.ui.screens.ShellScreen
import xyz.beyoneer.gossipaudio.ui.theme.Ui
import xyz.beyoneer.gossipaudio.ui.widgets.AvatarView
import xyz.beyoneer.gossipaudio.ui.widgets.BrandButton
import xyz.beyoneer.gossipaudio.ui.widgets.Chip
import xyz.beyoneer.gossipaudio.ui.widgets.CoverView
import xyz.beyoneer.gossipaudio.ui.widgets.IconView
import xyz.beyoneer.gossipaudio.ui.widgets.Icons
import xyz.beyoneer.gossipaudio.util.Artwork
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything you have ever said, searchable and downloadable. */
class LibraryTab(private val shell: ShellScreen) : Tab {

    private val activity get() = shell.act
    private val palette get() = shell.colors

    private lateinit var listBox: LinearLayout
    private lateinit var summary: TextView
    private lateinit var emptyBox: LinearLayout
    private lateinit var searchField: EditText
    private var query = ""
    private var likedOnly = false
    private var lastSignature = ""

    override val root: View by lazy { build() }

    override fun onShow() {
        render()
        PlaybackHub.addListener(hubListener)
    }

    override fun onHide() {
        PlaybackHub.removeListener(hubListener)
    }

    private val hubListener: (PlaybackHub.State) -> Unit = { state ->
        val signature = "${state.clipId}-${state.playing}"
        if (signature != lastSignature) {
            lastSignature = signature
            render()
        }
    }


    private fun build(): View {
        val scroll = ScrollView(activity)
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(activity, 18f), Ui.dp(activity, 8f), Ui.dp(activity, 18f), Ui.dp(activity, 28f))
        }
        scroll.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        column.addView(Ui.text(activity, "Your library", 25f, palette.text, bold = true))
        summary = Ui.text(activity, "", 12f, palette.textDim, font = "sans-serif-medium")
        column.addView(summary, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 3f) })

        val searchCard = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(palette.card, Ui.dpf(activity, 100f), palette.outline, Ui.dp(activity, 1f))
            setPadding(Ui.dp(activity, 12f), 0, Ui.dp(activity, 12f), 0)
        }
        searchCard.addView(IconView(activity, Icons.Kind.SEARCH, palette.textDim, 17f), LinearLayout.LayoutParams(
            Ui.dp(activity, 18f), Ui.dp(activity, 18f)
        ))
        searchField = EditText(activity).apply {
            hint = "Search titles and text"
            setHintTextColor(palette.textDim)
            setTextColor(palette.text)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13.5f)
            background = null
            setPadding(Ui.dp(activity, 8f), Ui.dp(activity, 11f), 0, Ui.dp(activity, 11f))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            contentDescription = "Search your library"
        }
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                render()
            }
        })
        searchCard.addView(searchField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        column.addView(searchCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 14f) })

        val filters = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val allChip = Chip(activity, palette, "All", !likedOnly)
        val likedChip = Chip(activity, palette, "Liked \u2764", likedOnly)
        allChip.setOnClickListener {
            likedOnly = false
            allChip.setSelectedState(true)
            likedChip.setSelectedState(false)
            render()
        }
        likedChip.setOnClickListener {
            likedOnly = true
            allChip.setSelectedState(false)
            likedChip.setSelectedState(true)
            render()
        }
        filters.addView(allChip)
        filters.addView(Ui.space(activity, Ui.dp(activity, 8f)))
        filters.addView(likedChip)
        column.addView(filters, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 12f) })

        emptyBox = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(Ui.dp(activity, 16f), Ui.dp(activity, 34f), Ui.dp(activity, 16f), Ui.dp(activity, 10f))
        }
        val orb = FrameLayout(activity)
        val circle = View(activity).apply {
            background = Ui.gradient(
                intArrayOf(palette.yellowBright, palette.yellow), Ui.dpf(activity, 100f),
                strokeColor = palette.yellowDeep, strokeWidth = Ui.dp(activity, 2f)
            )
        }
        orb.addView(circle, FrameLayout.LayoutParams(Ui.dp(activity, 86f), Ui.dp(activity, 86f), Gravity.CENTER))
        orb.addView(IconView(activity, Icons.Kind.WAVE, palette.onYellow, 36f), FrameLayout.LayoutParams(
            Ui.dp(activity, 38f), Ui.dp(activity, 38f), Gravity.CENTER
        ))
        emptyBox.addView(orb, LinearLayout.LayoutParams(Ui.dp(activity, 96f), Ui.dp(activity, 96f)))
        emptyBox.addView(Ui.text(activity, "No gossip here yet", 17f, palette.text, bold = true, gravity = Gravity.CENTER), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 12f) })
        emptyBox.addView(
            Ui.text(activity, "Generate your first clip and it will live here, ready to download.", 12.5f, palette.textDim, gravity = Gravity.CENTER),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(activity, 6f)
            }
        )
        val openStudio = BrandButton(activity, palette, "Open Studio", BrandButton.Variant.PRIMARY, Icons.Kind.SPARKLE, compact = true)
        openStudio.setOnClick { shell.selectTab(0) }
        emptyBox.addView(openStudio, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 16f) })
        column.addView(emptyBox, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        listBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        column.addView(listBox, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 10f) })

        val bulk = Ui.row(activity)
        val sweep = BrandButton(activity, palette, "Clean orphan files", BrandButton.Variant.GHOST, Icons.Kind.TRASH, compact = true)
        sweep.setOnClick {
            val removed = activity.app.store.sweepOrphans()
            activity.toast(if (removed == 0) "Nothing to clean. Tidy!" else "Removed $removed orphan file(s).")
            render()
        }
        bulk.addView(sweep)
        bulk.addView(Ui.space(activity, 1), LinearLayout.LayoutParams(0, 1, 1f))
        val deleteAll = BrandButton(activity, palette, "Delete all", BrandButton.Variant.GHOST, Icons.Kind.CLOSE, compact = true)
        deleteAll.setOnClick {
            Overlays.confirm(
                activity, "Delete everything?", "Every clip in your library will be erased from this device. Downloads you already saved stay in Downloads.",
                "Delete all", danger = true
            ) {
                PlaybackHub.stop()
                activity.app.store.all().forEach { activity.app.store.delete(it.id) }
                render()
                activity.toast("Library cleared.")
            }
        }
        bulk.addView(deleteAll)
        column.addView(bulk, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 18f) })
        return scroll
    }

    private fun render() {
        if (!::listBox.isInitialized) return
        val all = activity.app.store.all()
        val shown = if (likedOnly) activity.app.store.liked() else all
        val filtered = if (query.isBlank()) shown else shown.filter {
            it.title.lowercase().contains(query.lowercase()) ||
                it.text.lowercase().contains(query.lowercase()) ||
                VoiceCatalog.byId(it.voiceId).name.lowercase().contains(query.lowercase())
        }
        listBox.removeAllViews()
        val bytes = all.sumOf { it.sizeBytes }
        summary.text = "${all.size} clip${if (all.size == 1) "" else "s"} \u00B7 ${TextKit.formatBytes(bytes)} of audio on disk"
        emptyBox.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        filtered.forEach { clip ->
            listBox.addView(clipRow(clip), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = Ui.dp(activity, 10f) })
        }
        Ui.fadeInUp(listBox, 0, 6f)
    }

    private fun clipRow(clip: Clip): View {
        val playing = PlaybackHub.state.clipId == clip.id && PlaybackHub.state.playing
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.rounded(palette.card, Ui.dpf(activity, 20f), palette.outline, Ui.dp(activity, 1f))
            setPadding(Ui.dp(activity, 10f), Ui.dp(activity, 10f), Ui.dp(activity, 8f), Ui.dp(activity, 10f))
            isClickable = true
        }
        val cover = CoverView(activity, palette, 54f)
        activity.app.store.coverFor(clip)?.let { cover.setImage(Artwork.loadScaled(it, 120)) }
        card.addView(cover, LinearLayout.LayoutParams(Ui.dp(activity, 54f), Ui.dp(activity, 54f)))

        val info = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(activity, 10f), 0, Ui.dp(activity, 6f), 0)
        }
        val titleView = Ui.text(activity, clip.title, 13.5f, palette.text, bold = true, font = "sans-serif-medium")
        titleView.maxLines = 1
        info.addView(titleView)
        val voice = VoiceCatalog.byId(clip.voiceId)
        val date = SimpleDateFormat("dd MMM \u00B7 HH:mm", Locale.getDefault()).format(Date(clip.createdAt))
        info.addView(
            Ui.text(
                activity,
                "${voice.flag} ${voice.name} \u00B7 ${TextKit.formatDuration(clip.durationMs)} \u00B7 $date",
                10.5f, palette.textDim, font = "sans-serif"
            ),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(activity, 2f)
            }
        )
        card.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val like = IconView(activity, Icons.Kind.HEART, if (clip.liked) palette.yellow else palette.textDim, 18f)
        like.filled = clip.liked
        val likeBox = FrameLayout(activity)
        likeBox.addView(like, FrameLayout.LayoutParams(Ui.dp(activity, 18f), Ui.dp(activity, 18f), Gravity.CENTER))
        likeBox.layoutParams = LinearLayout.LayoutParams(Ui.dp(activity, 40f), Ui.dp(activity, 40f))
        likeBox.contentDescription =
            if (clip.liked) "Remove like from ${clip.title}" else "Like ${clip.title}"
        Ui.pressable(likeBox, 0.86f) {
            activity.app.store.toggleLike(clip.id)
            render()
        }
        card.addView(likeBox)

        val playBtn = FrameLayout(activity).apply {
            background = Ui.gradient(
                intArrayOf(palette.yellowBright, palette.yellow), Ui.dpf(activity, 100f),
                strokeColor = palette.yellowDeep, strokeWidth = Ui.dp(activity, 1.4f)
            )
        }
        playBtn.addView(
            IconView(activity, if (playing) Icons.Kind.PAUSE else Icons.Kind.PLAY, palette.onYellow, 16f),
            FrameLayout.LayoutParams(Ui.dp(activity, 16f), Ui.dp(activity, 16f), Gravity.CENTER)
        )
        card.addView(playBtn, LinearLayout.LayoutParams(Ui.dp(activity, 44f), Ui.dp(activity, 44f)))
        playBtn.contentDescription = if (playing) "Pause ${clip.title}" else "Play ${clip.title}"
        Ui.pressable(playBtn, 0.9f) {
            if (playing) {
                PlaybackHub.pause()
            } else {
                shell.playClip(clip)
                if (activity.prefs.autoPlay) {
                    activity.router.push(PlayerScreen(activity, clip.id))
                }
            }
            render()
        }

        val more = FrameLayout(activity)
        more.addView(Ui.text(activity, "\u22EE", 18f, palette.textDim, bold = true, gravity = Gravity.CENTER), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        more.layoutParams = LinearLayout.LayoutParams(Ui.dp(activity, 40f), Ui.dp(activity, 44f))
        more.contentDescription = "More actions for ${clip.title}"
        Ui.pressable(more, 0.86f) { ClipActions.show(activity, clip) { render() } }
        card.addView(more)

        card.setOnClickListener {
            shell.playClip(clip)
            activity.router.push(PlayerScreen(activity, clip.id))
        }
        card.setOnLongClickListener {
            ClipActions.show(activity, clip) { render() }
            true
        }
        Ui.pressable(card, 0.985f)
        return card
    }

    @Suppress("unused")
    private fun avatarFor(clip: Clip): AvatarView {
        val voice = VoiceCatalog.byId(clip.voiceId)
        return AvatarView(activity, palette, voice.name, voice.accent, 40f)
    }
}
