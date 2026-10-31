package xyz.beyoneer.gossipaudio.ui.widgets

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import xyz.beyoneer.gossipaudio.ui.MainActivity
import xyz.beyoneer.gossipaudio.ui.theme.Ui

/**
 * Full-screen "generating" overlay with the butterfly loader, live percentage
 * and a cancel affordance. Blocks input while synthesis runs.
 */
class LoadingOverlay(private val activity: MainActivity) {

    private val root = FrameLayout(activity)
    private val label: TextView
    private val hint: TextView
    private val bar: ProgressBar
    private var shown = false
    val butterfly: ButterflyLoader

    var onCancel: (() -> Unit)? = null
    var cancelled = false
        private set

    init {
        val palette = activity.palette
        root.setBackgroundColor(0xF20B0B0C.toInt())
        root.isClickable = true
        root.visibility = View.GONE

        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(Ui.dp(activity, 32f), 0, Ui.dp(activity, 32f), 0)
        }
        butterfly = ButterflyLoader(activity, palette, 160f).apply {
            reduceMotion = activity.prefs.reduceMotion
        }
        column.addView(butterfly, LinearLayout.LayoutParams(Ui.dp(activity, 190f), Ui.dp(activity, 190f)))
        label = Ui.text(activity, "Waking the voice\u2026", 17f, Color.WHITE, bold = true, gravity = Gravity.CENTER)
        column.addView(label, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 18f) })
        hint = Ui.text(activity, "All synthesis happens on your device", 11.5f, 0x99FFFFFF.toInt(), gravity = Gravity.CENTER)
        column.addView(hint, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 8f) })

        bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(palette.yellow)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x33FFFFFF)
        }
        column.addView(bar, LinearLayout.LayoutParams(Ui.dp(activity, 220f), Ui.dp(activity, 6f)).apply {
            topMargin = Ui.dp(activity, 22f)
        })

        val cancel = TextView(activity).apply {
            text = "Cancel"
            setTextColor(0xCCFFFFFF.toInt())
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            setPadding(Ui.dp(activity, 20f), Ui.dp(activity, 12f), Ui.dp(activity, 20f), Ui.dp(activity, 12f))
            setOnClickListener {
                cancelled = true
                onCancel?.invoke()
                hide()
            }
        }
        column.addView(cancel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 18f) })

        root.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        ))
    }

    fun show(title: String, hintText: String = "All synthesis happens on your device") {
        cancelled = false
        label.text = title
        hint.text = hintText
        bar.progress = 0
        if (!shown) {
            shown = true
            (activity.contentRoot as? ViewGroup)?.addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        root.visibility = View.VISIBLE
        root.alpha = 0f
        root.animate().alpha(1f).setDuration(200).start()
        butterfly.start()
    }

    fun setProgress(fraction: Float, text: String? = null) {
        bar.progress = (fraction.coerceIn(0f, 1f) * 100).toInt()
        if (text != null) label.text = text
    }

    fun hide() {
        butterfly.stop()
        root.animate().alpha(0f).setDuration(180).withEndAction {
            root.visibility = View.GONE
            (activity.contentRoot as? ViewGroup)?.removeView(root)
            shown = false
        }.start()
    }

    fun isShowing(): Boolean = shown && root.visibility == View.VISIBLE
}
