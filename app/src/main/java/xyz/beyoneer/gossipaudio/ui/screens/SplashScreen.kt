package xyz.beyoneer.gossipaudio.ui.screens

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import xyz.beyoneer.gossipaudio.R
import xyz.beyoneer.gossipaudio.ui.Brand
import xyz.beyoneer.gossipaudio.ui.MainActivity
import xyz.beyoneer.gossipaudio.ui.Router
import xyz.beyoneer.gossipaudio.ui.Screen
import xyz.beyoneer.gossipaudio.ui.theme.Palette
import xyz.beyoneer.gossipaudio.ui.theme.Ui
import xyz.beyoneer.gossipaudio.ui.widgets.ButterflyLoader
import xyz.beyoneer.gossipaudio.ui.widgets.GlowBackdrop

/** Brand-first loading screen: glow, logo pop, wordmark, butterfly, tagline. */
class SplashScreen(activity: MainActivity) : Screen(activity) {

    private val handler = Handler(Looper.getMainLooper())
    private var navigated = false
    private var butterfly: ButterflyLoader? = null

    override fun onCreateView(): View {
        val dark = Palette.DARK
        val root = FrameLayout(activity)
        root.setBackgroundColor(Color.BLACK)
        root.addView(GlowBackdrop(activity, dark), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(Ui.dp(activity, 28f), 0, Ui.dp(activity, 28f), Ui.dp(activity, 30f))
        }
        val logo = ImageView(activity).apply {
            setImageResource(R.drawable.gossipaudio_glyph)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        column.addView(logo, LinearLayout.LayoutParams(Ui.dp(activity, 132f), Ui.dp(activity, 132f)))

        val title = Ui.text(activity, Brand.NAME, 36f, Color.WHITE, bold = true, font = "sans-serif-black", gravity = Gravity.CENTER)
        title.letterSpacing = 0.01f
        column.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 10f) })

        val byline = Ui.text(activity, Brand.BYLINE, 12.5f, dark.yellow, bold = true, font = "sans-serif-medium", gravity = Gravity.CENTER)
        byline.letterSpacing = 0.16f
        column.addView(byline, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 6f) })

        butterfly = ButterflyLoader(activity, dark, 120f).apply {
            label = "warming up the tea\u2026"
            reduceMotion = activity.prefs.reduceMotion
        }
        column.addView(butterfly, LinearLayout.LayoutParams(Ui.dp(activity, 150f), Ui.dp(activity, 150f)).apply {
            topMargin = Ui.dp(activity, 26f)
        })

        val tagline = Ui.text(activity, Brand.TAGLINE, 11.5f, 0xCCFFFFFF.toInt(), font = "sans-serif-condensed", gravity = Gravity.CENTER)
        tagline.maxWidth = Ui.dp(activity, 300f)
        tagline.setLineSpacing(Ui.dpf(activity, 3f), 1f)
        column.addView(tagline, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 18f) })

        val version = Ui.text(activity, "v${Brand.VERSION} \u00B7 made by ${Brand.AUTHOR}", 10.5f, 0x88FFFFFF.toInt(), font = "sans-serif", gravity = Gravity.CENTER)
        column.addView(version, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 10f) })

        root.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        ))
        root.setOnClickListener { advance() }
        return root
    }

    override fun onShown() {
        val view = root
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(300).start()
        handler.postDelayed({ advance() }, 2500)
    }

    private fun advance() {
        if (navigated) return
        navigated = true
        handler.removeCallbacksAndMessages(null)
        butterfly?.stop()
        val next: Screen = if (activity.prefs.onboardingDone) ShellScreen(activity) else OnboardingScreen(activity)
        activity.router.start(next)
    }

    override fun onDestroyed() {
        handler.removeCallbacksAndMessages(null)
        butterfly?.stop()
    }
}
