package xyz.beyoneer.gossipaudio.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import xyz.beyoneer.gossipaudio.ui.theme.Palette
import xyz.beyoneer.gossipaudio.ui.theme.Ui
import xyz.beyoneer.gossipaudio.ui.widgets.BrandButton
import xyz.beyoneer.gossipaudio.ui.widgets.IconView
import xyz.beyoneer.gossipaudio.ui.widgets.Icons
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Animated toasts, bottom sheets and confirm dialogs with a consistent brand feel. */
object Overlays {

    private val layers = WeakHashMap<MainActivity, MutableList<View>>()
    private val handler = Handler(Looper.getMainLooper())

    private fun stack(activity: MainActivity): MutableList<View> {
        return layers.getOrPut(activity) { ArrayList() }
    }

    fun dismissTop(activity: MainActivity): Boolean {
        val list = layers[activity] ?: return false
        val top = list.lastOrNull() ?: return false
        remove(activity, top)
        return true
    }

    private fun remove(activity: MainActivity, view: View) {
        val list = layers[activity] ?: return
        list.remove(view)
        (activity.contentRoot as? ViewGroup)?.removeView(view)
    }

    fun toast(activity: MainActivity, message: String) {
        try {
            val palette = activity.palette
            val root = FrameLayout(activity)
            root.isClickable = false
            val pill = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Ui.dp(activity, 18f), Ui.dp(activity, 13f), Ui.dp(activity, 18f), Ui.dp(activity, 13f))
                background = Ui.gradient(
                    intArrayOf(if (palette.dark) 0xFF1F1D14.toInt() else 0xFF141416.toInt(), Color.BLACK),
                    Ui.dpf(activity, 100f),
                    strokeColor = palette.yellow, strokeWidth = Ui.dp(activity, 1.5f)
                )
                elevation = Ui.dpf(activity, 8f)
            }
            val icon = IconView(activity, Icons.Kind.SPARKLE, palette.yellow, 16f)
            pill.addView(icon)
            val tv = Ui.text(activity, message, 13.5f, Color.WHITE, bold = true, font = "sans-serif-medium")
            // Custom toast = invisible to TalkBack unless we say it out loud.
            tv.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
            tv.setPadding(Ui.dp(activity, 10f), 0, 0, 0)
            tv.maxWidth = Ui.dp(activity, 260f)
            pill.addView(tv)
            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
            lp.bottomMargin = Ui.dp(activity, 108f)
            root.addView(pill, lp)
            (activity.contentRoot as? ViewGroup)?.addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            stack(activity).add(root)
            tv.announceForAccessibility(message)
            pill.alpha = 0f
            pill.translationY = Ui.dpf(activity, 24f)
            pill.animate().alpha(1f).translationY(0f).setDuration(260)
                .setInterpolator(PathInterpolator(0.2f, 0.9f, 0.1f, 1f)).start()
            handler.postDelayed({
                pill.animate().alpha(0f).translationY(Ui.dpf(activity, 18f)).setDuration(240)
                    .withEndAction { remove(activity, root) }.start()
            }, 2400)
        } catch (_: Throwable) {
        }
    }

    class Handle(private val activity: MainActivity, private val root: View) {
        fun dismiss() {
            val palette = activity.palette
            root.animate().alpha(0f).setDuration(180).withEndAction { remove(activity, root) }.start()
        }
    }

    fun sheet(activity: MainActivity, title: String?, content: View): Handle {
        val palette = activity.palette
        val root = FrameLayout(activity)
        val scrim = View(activity)
        scrim.setBackgroundColor(0xB3000000.toInt())
        scrim.alpha = 0f
        // Tapping the scrim dismisses; talk to the sheet content instead of the dimmer.
        scrim.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        root.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable()
            bg.shape = GradientDrawable.RECTANGLE
            bg.cornerRadii = floatArrayOf(
                Ui.dpf(activity, 28f), Ui.dpf(activity, 28f),
                Ui.dpf(activity, 28f), Ui.dpf(activity, 28f),
                0f, 0f, 0f, 0f
            )
            bg.setColor(palette.card)
            bg.setStroke(Ui.dp(activity, 1f), palette.outline)
            background = bg
            setPadding(Ui.dp(activity, 20f), Ui.dp(activity, 12f), Ui.dp(activity, 20f), Ui.dp(activity, 24f))
            elevation = Ui.dpf(activity, 18f)
        }
        val grabber = View(activity).apply {
            background = Ui.rounded(palette.outline, Ui.dpf(activity, 100f))
        }
        panel.addView(grabber, LinearLayout.LayoutParams(Ui.dp(activity, 44f), Ui.dp(activity, 4f)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = Ui.dp(activity, 4f)
            bottomMargin = Ui.dp(activity, 12f)
        })
        if (title != null) {
            panel.addView(Ui.text(activity, title, 19f, palette.text, bold = true), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = Ui.dp(activity, 10f) })
        }
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            addView(content)
        }
        panel.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        lp.bottomMargin = 0
        root.addView(panel, lp)
        scrim.setOnClickListener { Handle(activity, root).dismiss() }
        (activity.contentRoot as? ViewGroup)?.addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        stack(activity).add(root)
        panel.translationY = Ui.dpf(activity, 420f)
        root.alpha = 1f
        scrim.animate().alpha(1f).setDuration(220).start()
        panel.animate().translationY(0f).setDuration(380)
            .setInterpolator(PathInterpolator(0.16f, 1f, 0.1f, 1f)).start()
        return Handle(activity, root)
    }

    fun confirm(activity: MainActivity, title: String, message: String, confirmLabel: String = "Yes", danger: Boolean = false, onConfirm: () -> Unit) {
        val palette = activity.palette
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        content.addView(Ui.text(activity, message, 14f, palette.textDim, font = "sans-serif"))
        val row = Ui.row(activity)
        row.addView(Ui.space(activity, 1), LinearLayout.LayoutParams(0, 1, 1f))
        val cancel = BrandButton(activity, palette, "Cancel", BrandButton.Variant.GHOST, compact = true)
        val ok = BrandButton(activity, palette, confirmLabel, if (danger) BrandButton.Variant.DANGER else BrandButton.Variant.PRIMARY, compact = true)
        row.addView(cancel)
        row.addView(Ui.space(activity, Ui.dp(activity, 10f)))
        row.addView(ok)
        content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(activity, 18f)
        })
        val handle = sheet(activity, title, content)
        cancel.setOnClick { handle.dismiss() }
        ok.setOnClick {
            handle.dismiss()
            onConfirm()
        }
    }

    fun prompt(activity: MainActivity, title: String, initial: String, hint: String = "Name it", onOk: (String) -> Unit) {
        val palette = activity.palette
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val input = EditText(activity).apply {
            setText(initial)
            setHint(hint)
            setTextColor(palette.text)
            setHintTextColor(palette.textDim)
            setSingleLine(false)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = Ui.rounded(palette.bg, Ui.dpf(activity, 16f), palette.outline, Ui.dp(activity, 1f))
            setPadding(Ui.dp(activity, 14f), Ui.dp(activity, 12f), Ui.dp(activity, 14f), Ui.dp(activity, 12f))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
            contentDescription = hint
        }
        content.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val row = Ui.row(activity)
        row.gravity = Gravity.END
        val ok = BrandButton(activity, palette, "Save", BrandButton.Variant.PRIMARY, compact = true)
        row.addView(ok)
        content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(activity, 16f)
        })
        val handle = sheet(activity, title, content)
        input.requestFocus()
        ok.setOnClick {
            val text = input.text?.toString()?.trim().orEmpty()
            handle.dismiss()
            onOk(text)
        }
    }

    fun info(activity: MainActivity, title: String, rows: List<Pair<String, String>>) {
        val palette = activity.palette
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        rows.forEach { (label, value) ->
            val row = Ui.row(activity)
            row.setPadding(0, Ui.dp(activity, 7f), 0, Ui.dp(activity, 7f))
            val l = Ui.text(activity, label, 13f, palette.textDim, font = "sans-serif-medium")
            val v = Ui.text(activity, value, 13f, palette.text, bold = true, font = "sans-serif-medium", gravity = Gravity.END)
            row.addView(l, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f))
            content.addView(row)
        }
        val close = BrandButton(activity, palette, "Close", BrandButton.Variant.INK, compact = true)
        content.addView(close, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(activity, 14f)
        })
        val handle = sheet(activity, title, content)
        close.setOnClick { handle.dismiss() }
    }

    fun sheetWithClose(activity: MainActivity, title: String, content: View): Handle {
        return sheet(activity, title, content)
    }

    @Suppress("unused")
    private fun ref(activity: MainActivity): WeakReference<MainActivity> = WeakReference(activity)

    fun paletteOf(activity: MainActivity): Palette = activity.palette
}
