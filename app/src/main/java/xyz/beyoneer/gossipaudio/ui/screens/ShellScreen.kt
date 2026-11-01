package xyz.beyoneer.gossipaudio.ui.screens

import android.graphics.BitmapFactory
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import xyz.beyoneer.gossipaudio.R
import xyz.beyoneer.gossipaudio.audio.PlaybackHub
import xyz.beyoneer.gossipaudio.data.Clip
import xyz.beyoneer.gossipaudio.ui.Brand
import xyz.beyoneer.gossipaudio.ui.MainActivity
import xyz.beyoneer.gossipaudio.ui.Screen
import xyz.beyoneer.gossipaudio.ui.Tab
import xyz.beyoneer.gossipaudio.ui.tabs.LibraryTab
import xyz.beyoneer.gossipaudio.ui.tabs.SettingsTab
import xyz.beyoneer.gossipaudio.ui.tabs.StudioTab
import xyz.beyoneer.gossipaudio.ui.tabs.VoicesTab
import xyz.beyoneer.gossipaudio.ui.theme.Palette
import xyz.beyoneer.gossipaudio.ui.theme.ThemeController
import xyz.beyoneer.gossipaudio.ui.theme.Ui
import xyz.beyoneer.gossipaudio.ui.widgets.CoverView
import xyz.beyoneer.gossipaudio.ui.widgets.GlowBackdrop
import xyz.beyoneer.gossipaudio.ui.widgets.IconView
import xyz.beyoneer.gossipaudio.ui.widgets.Icons
import xyz.beyoneer.gossipaudio.ui.widgets.MiniBars

/** App shell: brand bar, tab host, mini player and the yellow bottom navigation. */
class ShellScreen(activity: MainActivity) : Screen(activity) {

    lateinit var studio: StudioTab
        private set
    lateinit var voices: VoicesTab
        private set
    lateinit var library: LibraryTab
        private set
    lateinit var settings: SettingsTab
        private set

    private val tabs = ArrayList<Tab>()
    private val host = FrameLayout(activity)
    private var current = -1
    private lateinit var navIndicator: View
    private val navIcons = ArrayList<IconView>()
    private val navLabels = ArrayList<TextView>()
    private val navCells = ArrayList<View>()
    private lateinit var mini: MiniPlayerView
    private lateinit var miniHost: FrameLayout

    private val hubListener: (PlaybackHub.State) -> Unit = { state -> mini.update(state) }

    val act: MainActivity get() = activity
    val colors: Palette get() = palette

    override fun onCreateView(): View {
        val root = FrameLayout(activity)
        root.addView(GlowBackdrop(activity, palette), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        studio = StudioTab(this)
        voices = VoicesTab(this)
        library = LibraryTab(this)
        settings = SettingsTab(this)
        tabs.add(studio)
        tabs.add(voices)
        tabs.add(library)
        tabs.add(settings)

        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        column.addView(buildTopBar())
        val hostWrap = FrameLayout(activity)
        hostWrap.addView(host, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        val bottomFade = View(activity).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(android.graphics.Color.TRANSPARENT, palette.bg)
            )
            isClickable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        hostWrap.addView(bottomFade, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 26f), Gravity.BOTTOM
        ))
        column.addView(hostWrap, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        miniHost = FrameLayout(activity)
        mini = MiniPlayerView()
        miniHost.addView(mini, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 66f)
        ).apply { setMargins(Ui.dp(activity, 14f), 0, Ui.dp(activity, 14f), Ui.dp(activity, 8f)) })
        miniHost.visibility = View.GONE
        column.addView(miniHost, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        column.addView(buildNavBar())
        root.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        selectTab(0, animate = false)
        return root
    }

    private fun buildTopBar(): View {
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(activity, 18f), Ui.dp(activity, 14f), Ui.dp(activity, 14f), Ui.dp(activity, 8f))
        }
        val logo = ImageView(activity).apply { setImageResource(R.drawable.gossipaudio_glyph) }
        bar.addView(logo, LinearLayout.LayoutParams(Ui.dp(activity, 34f), Ui.dp(activity, 34f)))
        val names = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(activity, 9f), 0, 0, 0)
        }
        names.addView(Ui.text(activity, Brand.NAME, 17f, palette.text, bold = true))
        names.addView(Ui.text(activity, Brand.BYLINE, 10f, palette.textDim, bold = true, font = "sans-serif-medium"))
        bar.addView(names, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val theme = iconButton(
            if (palette.dark) Icons.Kind.SUN else Icons.Kind.MOON,
            if (palette.dark) "Switch to light theme" else "Switch to dark theme"
        ) { activity.toggleTheme() }
        bar.addView(theme)
        val bell = iconButton(Icons.Kind.BELL, "Open reminder settings") {
            selectTab(3)
            settings.focusReminder()
        }
        bar.addView(bell)
        val info = iconButton(Icons.Kind.INFO, "About GossipAudio") { openAbout() }
        bar.addView(info)
        return bar
    }

    private fun iconButton(kind: Icons.Kind, label: String, action: () -> Unit): View {
        val box = FrameLayout(activity).apply {
            background = Ui.rounded(palette.card, Ui.dpf(activity, 100f), palette.outline, Ui.dp(activity, 1f))
            isClickable = true
            contentDescription = label
        }
        box.addView(IconView(activity, kind, palette.text, 19f), FrameLayout.LayoutParams(
            Ui.dp(activity, 20f), Ui.dp(activity, 20f), Gravity.CENTER
        ))
        box.layoutParams = LinearLayout.LayoutParams(Ui.dp(activity, 44f), Ui.dp(activity, 44f)).apply {
            marginStart = Ui.dp(activity, 8f)
        }
        Ui.pressable(box, 0.9f, action)
        return box
    }

    private fun buildNavBar(): View {
        val wrap = FrameLayout(activity)
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.gradient(
                intArrayOf(palette.card, Ui.blend(palette.card, palette.yellowSoft, if (palette.dark) 0.25f else 0.15f)),
                Ui.dpf(activity, 26f),
                strokeColor = palette.outline, strokeWidth = Ui.dp(activity, 1f)
            )
            elevation = Ui.dpf(activity, 6f)
            setPadding(Ui.dp(activity, 8f), Ui.dp(activity, 8f), Ui.dp(activity, 8f), Ui.dp(activity, 10f))
        }
        val items = listOf(
            Triple("Studio", Icons.Kind.SPARKLE, 0),
            Triple("Voices", Icons.Kind.PERSON, 1),
            Triple("Library", Icons.Kind.LIST, 2),
            Triple("Settings", Icons.Kind.GEAR, 3)
        )
        navIndicator = View(activity)
        items.forEach { (label, icon, index) ->
            val cell = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                isClickable = true
                setPadding(0, Ui.dp(activity, 4f), 0, Ui.dp(activity, 2f))
            }
            val iconView = IconView(activity, icon, palette.textDim, 22f)
            val labelView = Ui.text(activity, label, 10f, palette.textDim, bold = true, font = "sans-serif-medium", gravity = Gravity.CENTER)
            labelView.setPadding(0, Ui.dp(activity, 3f), 0, 0)
            cell.addView(iconView, LinearLayout.LayoutParams(Ui.dp(activity, 22f), Ui.dp(activity, 22f)))
            cell.addView(labelView)
            cell.contentDescription = "$label tab"
            cell.setOnClickListener { selectTab(index) }
            Ui.pressable(cell, 0.92f)
            navIcons.add(iconView)
            navLabels.add(labelView)
            navCells.add(cell)
            bar.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        wrap.addView(bar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(Ui.dp(activity, 14f), 0, Ui.dp(activity, 14f), Ui.dp(activity, 14f)) })
        wrap.setPadding(0, 0, 0, 0)
        return wrap
    }

    private fun updateNav() {
        navCells.forEachIndexed { index, cell ->
            val selected = index == current
            cell.isSelected = selected
            navIcons[index].setTintColor(if (selected) palette.onYellow else palette.textDim)
            navLabels[index].setTextColor(if (selected) palette.text else palette.textDim)
            cell.background = if (selected) {
                Ui.gradient(
                    intArrayOf(palette.yellowBright, palette.yellow),
                    Ui.dpf(activity, 18f),
                    strokeColor = palette.yellowDeep, strokeWidth = Ui.dp(activity, 1.2f)
                )
            } else {
                null
            }
            if (selected && !activity.prefs.reduceMotion) {
                Ui.popIn(cell)
            }
        }
    }

    fun selectTab(index: Int, animate: Boolean = true) {
        if (index == current) return
        val previousIndex = current
        current = index
        val newView = tabs[index].root
        if (previousIndex >= 0) {
            tabs[previousIndex].onHide()
            val old = host.getChildAt(0)
            if (old != null) {
                if (animate && !activity.prefs.reduceMotion) {
                    old.animate().alpha(0f).scaleX(0.985f).scaleY(0.985f).setDuration(160)
                        .withEndAction { host.removeView(old) }.start()
                } else {
                    host.removeView(old)
                }
            }
        }
        host.addView(newView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        if (animate && !activity.prefs.reduceMotion) {
            newView.alpha = 0f
            newView.translationY = Ui.dpf(activity, 14f)
            newView.animate().alpha(1f).translationY(0f).setDuration(280)
                .setInterpolator(PathInterpolator(0.2f, 0.9f, 0.1f, 1f)).start()
        }
        tabs[index].onShow()
        updateNav()
    }

    fun openPlayer(clip: Clip) {
        activity.router.push(PlayerScreen(activity, clip.id))
    }

    fun openAbout() {
        activity.router.push(AboutScreen(activity))
    }

    fun openDiagnostics() {
        activity.router.push(DiagnosticsScreen(activity))
    }

    fun openHelp() {
        activity.router.push(HelpScreen(activity))
    }

    fun startGenerationInStudio() {
        selectTab(0)
    }

    fun playClip(clip: Clip) {
        val file = activity.app.store.fileFor(clip)
        if (!file.exists()) {
            activity.toast("Audio file is missing for that clip.")
            return
        }
        val cover = activity.app.store.coverFor(clip)
        PlaybackHub.playFile(file, clip.id, clip.title, cover)
        if (activity.prefs.notifications) activity.requestNotifications()
    }

    override fun onShown() {
        PlaybackHub.addListener(hubListener)
        hubListener(PlaybackHub.state)
        if (current >= 0) tabs[current].onShow()
    }

    override fun onHidden() {
        if (current >= 0) tabs[current].onHide()
        PlaybackHub.removeListener(hubListener)
    }

    override fun onDestroyed() {
        if (current >= 0) tabs[current].onHide()
        PlaybackHub.removeListener(hubListener)
    }

    override fun onBack(): Boolean {
        if (current != 0) {
            selectTab(0)
            return true
        }
        return false
    }

    inner class MiniPlayerView : LinearLayout(activity) {

        private val cover: CoverView = CoverView(activity, palette, 46f)
        private val title = Ui.text(activity, "", 13f, palette.text, bold = true, font = "sans-serif-medium")
        private val subtitle = Ui.text(activity, "", 10.5f, palette.textDim, font = "sans-serif")
        private val bars = MiniBars(activity, palette)
        private val play = FrameLayout(activity)
        private val progress = View(activity)
        private var lastCoverPath: String? = null

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Ui.gradient(
                intArrayOf(Ui.blend(palette.card, palette.yellowSoft, if (palette.dark) 0.3f else 0.2f), palette.card),
                Ui.dpf(activity, 20f),
                strokeColor = palette.yellowDeep, strokeWidth = Ui.dp(activity, 1.4f)
            )
            elevation = Ui.dpf(activity, 5f)
            setPadding(Ui.dp(activity, 10f), Ui.dp(activity, 8f), Ui.dp(activity, 10f), Ui.dp(activity, 8f))
            addView(cover, LinearLayout.LayoutParams(Ui.dp(activity, 46f), Ui.dp(activity, 46f)))
            val texts = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(activity, 10f), 0, Ui.dp(activity, 6f), 0)
            }
            texts.addView(title)
            texts.addView(subtitle)
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            play.background = Ui.gradient(
                intArrayOf(palette.yellowBright, palette.yellow), Ui.dpf(activity, 100f),
                strokeColor = palette.yellowDeep, strokeWidth = Ui.dp(activity, 1.4f)
            )
            play.addView(IconView(activity, Icons.Kind.PAUSE, palette.onYellow, 18f), FrameLayout.LayoutParams(
                Ui.dp(activity, 18f), Ui.dp(activity, 18f), Gravity.CENTER
            ))
            addView(play, LinearLayout.LayoutParams(Ui.dp(activity, 40f), Ui.dp(activity, 40f)))
            addView(bars, LinearLayout.LayoutParams(Ui.dp(activity, 20f), Ui.dp(activity, 20f)).apply {
                marginStart = Ui.dp(activity, 8f)
            })
            play.contentDescription = "Pause or resume"
            play.setOnClickListener {
                PlaybackHub.toggle()
            }
            Ui.pressable(play, 0.9f)
            setOnClickListener {
                if (PlaybackHub.state.clipId != null) {
                    val clip = activity.app.store.byId(PlaybackHub.state.clipId!!)
                    if (clip != null) openPlayer(clip) else activity.toast("That clip was removed.")
                }
            }
            Ui.pressable(this, 0.98f)
        }

        fun update(state: PlaybackHub.State) {
            visibility = if (state.clipId == null) View.GONE else View.VISIBLE
            miniHost.visibility = if (state.clipId == null) View.GONE else View.VISIBLE
            if (state.clipId == null) return
            title.text = state.title.ifBlank { "Now playing" }
            val secs = state.positionMs / 1000
            val total = state.durationMs / 1000
            subtitle.text = String.format("%d:%02d / %d:%02d", secs / 60, secs % 60, total / 60, total % 60)
            bars.playing = state.playing
            (play.getChildAt(0) as? IconView)?.kind = if (state.playing) Icons.Kind.PAUSE else Icons.Kind.PLAY
            (play.getChildAt(0) as? IconView)?.invalidate()
            val path = state.coverPath
            if (path != null && path != lastCoverPath) {
                lastCoverPath = path
                cover.setImage(BitmapFactory.decodeFile(path))
            } else if (path == null) {
                cover.setImage(null)
                lastCoverPath = null
            }
        }
    }
}
