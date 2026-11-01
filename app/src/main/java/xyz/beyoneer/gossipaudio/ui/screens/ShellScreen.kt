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
import xyz.beyoneer.gossipaudio.ui.theme.Motion
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
    private val navIcons = ArrayList<IconView>()
    private val navLabels = ArrayList<TextView>()
    private val navCells = ArrayList<View>()
    private var dock: FrameLayout? = null
    private var navRow: LinearLayout? = null
    private var pill: View? = null
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
        root.post { positionPill(animate = false) }
        // a share intent or a launcher shortcut may be waiting for this screen
        root.post { activity.dispatchPending() }
        return root
    }

    private fun buildTopBar(): View {
        val wrap = FrameLayout(activity)
        // A soft yellow wash behind the brand bar. Before this the logo floated on a
        // flat cream page and the app read as "default Android with yellow buttons".
        val wash = View(activity).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Ui.withAlpha(palette.yellow, if (palette.dark) 0.14f else 0.34f),
                    android.graphics.Color.TRANSPARENT
                )
            )
            isClickable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        // Fixed height, not MATCH_PARENT: a MATCH_PARENT child of a wrap_content
        // FrameLayout measures against the whole screen, which made the brand bar the
        // full height of the display and pushed every tab off the bottom.
        wrap.addView(wash, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 104f)
        ))

        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(activity, 16f), Ui.dp(activity, 12f), Ui.dp(activity, 12f), Ui.dp(activity, 10f))
        }

        // logo in an ink badge: the glyph is yellow, so it needs the dark plate
        val badge = FrameLayout(activity).apply {
            background = Ui.gradient(
                intArrayOf(0xFF24241E.toInt(), 0xFF0B0B0C.toInt()),
                Ui.dpf(activity, 14f),
                strokeColor = Ui.withAlpha(palette.yellow, 0.55f), strokeWidth = Ui.dp(activity, 1.2f)
            )
            elevation = Ui.dpf(activity, 4f)
        }
        badge.addView(
            ImageView(activity).apply { setImageResource(R.drawable.gossipaudio_glyph) },
            FrameLayout.LayoutParams(Ui.dp(activity, 24f), Ui.dp(activity, 24f), Gravity.CENTER)
        )
        bar.addView(badge, LinearLayout.LayoutParams(Ui.dp(activity, 40f), Ui.dp(activity, 40f)))

        val names = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(activity, 10f), 0, Ui.dp(activity, 4f), 0)
        }
        val narrow = activity.resources.displayMetrics.widthPixels /
            activity.resources.displayMetrics.density < 360f
        val title = Ui.text(activity, Brand.NAME, if (narrow) 16f else 18f, palette.text, bold = true)
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        title.letterSpacing = -0.02f
        names.addView(title)
        val byline = Ui.text(
            activity,
            (if (narrow) "BY ${Brand.AUTHOR}" else Brand.BYLINE).uppercase(),
            8.5f, palette.textDim, bold = true, font = "sans-serif-medium"
        )
        byline.maxLines = 1
        byline.letterSpacing = 0.14f
        byline.ellipsize = android.text.TextUtils.TruncateAt.END
        names.addView(byline, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 2f) })
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
        wrap.addView(bar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        return wrap
    }

    private fun iconButton(kind: Icons.Kind, label: String, action: () -> Unit): View {
        val box = FrameLayout(activity).apply {
            background = Ui.rounded(palette.card, Ui.dpf(activity, 100f))
            elevation = Ui.dpf(activity, 2f)
            isClickable = true
            contentDescription = label
        }
        box.addView(IconView(activity, kind, palette.text, 18f), FrameLayout.LayoutParams(
            Ui.dp(activity, 19f), Ui.dp(activity, 19f), Gravity.CENTER
        ))
        box.layoutParams = LinearLayout.LayoutParams(Ui.dp(activity, 38f), Ui.dp(activity, 38f)).apply {
            marginStart = Ui.dp(activity, 6f)
        }
        Ui.pressable(box, 0.9f, action)
        return box
    }

    private fun buildNavBar(): View {
        val wrap = FrameLayout(activity)
        val dockView = FrameLayout(activity)
        dock = dockView
        dockView.background = Ui.gradient(
            intArrayOf(Ui.blend(INK_DOCK, 0xFFFFFFFF.toInt(), 0.10f), INK_DOCK),
            Ui.dpf(activity, 32f),
            strokeColor = Ui.withAlpha(palette.yellow, 0.30f), strokeWidth = Ui.dp(activity, 1f)
        )
        dockView.elevation = Ui.dpf(activity, 14f)
        dockView.setPadding(Ui.dp(activity, 6f), Ui.dp(activity, 6f), Ui.dp(activity, 6f), Ui.dp(activity, 6f))

        // One yellow pill that *slides* between tabs. The old version stamped a
        // background onto each cell and bounced it with popIn(), which is why the
        // selection felt cheap: four separate highlights popping in and out.
        pill = View(activity).apply {
            background = Ui.gradient(
                intArrayOf(palette.yellowBright, palette.yellow),
                Ui.dpf(activity, 26f),
                strokeColor = palette.yellowDeep, strokeWidth = Ui.dp(activity, 1f)
            )
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        dockView.addView(pill, FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT))

        val bar = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        dockView.addView(bar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        navRow = bar

        val items = listOf(
            Triple("Studio", Icons.Kind.SPARKLE, 0),
            Triple("Voices", Icons.Kind.PERSON, 1),
            Triple("Library", Icons.Kind.LIST, 2),
            Triple("Settings", Icons.Kind.GEAR, 3)
        )
        items.forEach { (label, icon, index) ->
            val cell = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                isClickable = true
                setPadding(0, Ui.dp(activity, 9f), 0, Ui.dp(activity, 9f))
            }
            val iconView = IconView(activity, icon, NAV_IDLE, 21f)
            val labelView = Ui.text(activity, label, 9.5f, NAV_IDLE, bold = true, font = "sans-serif-medium", gravity = Gravity.CENTER)
            labelView.letterSpacing = 0.04f
            labelView.setPadding(0, Ui.dp(activity, 4f), 0, 0)
            cell.addView(iconView, LinearLayout.LayoutParams(Ui.dp(activity, 21f), Ui.dp(activity, 21f)))
            cell.addView(labelView)
            cell.contentDescription = "$label tab"
            cell.setOnClickListener { selectTab(index) }
            Ui.pressable(cell, 0.96f)
            navIcons.add(iconView)
            navLabels.add(labelView)
            navCells.add(cell)
            bar.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        bar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> positionPill(animate = false) }

        wrap.addView(dockView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(Ui.dp(activity, 14f), 0, Ui.dp(activity, 14f), Ui.dp(activity, 12f)) })
        return wrap
    }

    /** Slides the yellow pill under the active tab. */
    private fun positionPill(animate: Boolean) {
        val row = navRow ?: return
        val marker = pill ?: return
        val cellWidth = row.width.toFloat() / navCells.size.coerceAtLeast(1)
        if (cellWidth <= 0f) return
        val inset = Ui.dp(activity, 3f).toFloat()
        val lp = marker.layoutParams as FrameLayout.LayoutParams
        val targetWidth = (cellWidth - inset * 2f).toInt()
        val targetX = cellWidth * current + inset
        lp.width = targetWidth
        lp.height = FrameLayout.LayoutParams.MATCH_PARENT
        lp.leftMargin = Ui.dp(activity, 6f)
        lp.topMargin = Ui.dp(activity, 6f)
        lp.bottomMargin = Ui.dp(activity, 6f)
        marker.layoutParams = lp
        if (animate && Motion.enabled && row.width > 0) {
            marker.animate()
                .translationX(targetX)
                .setDuration(320)
                .setInterpolator(PathInterpolator(0.2f, 0.9f, 0.1f, 1f))
                .start()
        } else {
            marker.translationX = targetX
        }
    }

    private fun updateNav() {
        navCells.forEachIndexed { index, cell ->
            val selected = index == current
            cell.isSelected = selected
            navIcons[index].setTintColor(if (selected) palette.onYellow else NAV_IDLE)
            navLabels[index].setTextColor(if (selected) palette.onYellow else NAV_IDLE)
            // A 6 % lift on the active icon, animated: enough to feel deliberate, small
            // enough not to look like a toy.
            val target = if (selected) 1.06f else 1f
            if (Motion.enabled) {
                navIcons[index].animate().scaleX(target).scaleY(target).setDuration(220).start()
            } else {
                navIcons[index].scaleX = target
                navIcons[index].scaleY = target
            }
        }
        positionPill(animate = true)
    }

    private companion object {
        /** The dock is always ink; the yellow pill is what moves. */
        const val INK_DOCK = 0xFF0B0B0C.toInt()

        /** Idle colour on that dark dock: warm grey, readable in both themes. */
        const val NAV_IDLE = 0xFFB4B4AC.toInt()
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
    }

    override fun onHidden() {
        PlaybackHub.removeListener(hubListener)
    }

    override fun onDestroyed() {
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
