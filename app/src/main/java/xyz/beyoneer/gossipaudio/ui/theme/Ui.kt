package xyz.beyoneer.gossipaudio.ui.theme

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView

/** Programmatic UI helpers. Keeps screens readable and the whole app dependency-free. */
object Ui {

    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    fun dpf(context: Context, value: Float): Float = value * context.resources.displayMetrics.density

    fun sp(context: Context, value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, context.resources.displayMetrics)

    fun rounded(color: Int, radius: Float, strokeColor: Int? = null, strokeWidth: Int = 0): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = radius
        d.setColor(color)
        if (strokeColor != null && strokeWidth > 0) d.setStroke(strokeWidth, strokeColor)
        return d
    }

    fun gradient(colors: IntArray, radius: Float, angle: GradientDrawable.Orientation = GradientDrawable.Orientation.TL_BR, strokeColor: Int? = null, strokeWidth: Int = 0): GradientDrawable {
        val d = GradientDrawable(angle, colors)
        d.cornerRadius = radius
        if (strokeColor != null && strokeWidth > 0) d.setStroke(strokeWidth, strokeColor)
        return d
    }

    fun ripple(content: GradientDrawable, rippleColor: Int): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(rippleColor), content, null)

    fun text(
        context: Context,
        value: CharSequence,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false,
        font: String = if (bold) "sans-serif-black" else "sans-serif",
        gravity: Int = Gravity.START
    ): TextView = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        typeface = Typeface.create(font, if (bold) Typeface.BOLD else Typeface.NORMAL)
        this.gravity = gravity
        includeFontPadding = false
    }

    fun column(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    fun row(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    fun lp(width: Int, height: Int, weight: Float = 0f): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(width, height, weight)

    fun margins(params: ViewGroup.MarginLayoutParams, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0): ViewGroup.MarginLayoutParams {
        params.setMargins(l, t, r, b)
        return params
    }

    fun LinearLayout.add(view: View, width: Int = ViewGroup.LayoutParams.MATCH_PARENT, height: Int = ViewGroup.LayoutParams.WRAP_CONTENT, top: Int = 0, bottom: Int = 0): View {
        val params = LinearLayout.LayoutParams(width, height)
        params.topMargin = top
        params.bottomMargin = bottom
        addView(view, params)
        return view
    }

    fun space(context: Context, heightPx: Int): View = Space(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, heightPx)
    }

    fun card(context: Context, palette: Palette, radiusDp: Float = 24f, fill: Int? = null): LinearLayout {
        val c = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = gradient(
                intArrayOf(fill ?: palette.card, blend(fill ?: palette.card, palette.yellowSoft, if (palette.dark) 0.18f else 0.10f)),
                dpf(context, radiusDp),
                strokeColor = palette.outline,
                strokeWidth = dp(context, 1f)
            )
            elevation = dpf(context, 1.5f)
            setPadding(dp(context, 18f), dp(context, 18f), dp(context, 18f), dp(context, 18f))
        }
        return c
    }

    fun blend(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        fun mix(x: Int, y: Int) = (x + (y - x) * tt).toInt().coerceIn(0, 255)
        return Color.argb(
            mix(Color.alpha(a), Color.alpha(b)),
            mix(Color.red(a), Color.red(b)),
            mix(Color.green(a), Color.green(b)),
            mix(Color.blue(a), Color.blue(b))
        )
    }

    fun withAlpha(color: Int, alpha: Float): Int = Color.argb(
        (alpha.coerceIn(0f, 1f) * 255).toInt(),
        Color.red(color), Color.green(color), Color.blue(color)
    )

    /** Springy press feedback used by every tappable surface. */
    fun pressable(view: View, scale: Float = 0.965f, onClick: (() -> Unit)? = null) {
        if (onClick != null) view.setOnClickListener { v ->
            v.animate().scaleX(1f).scaleY(1f).setDuration(180)
                .setInterpolator(OvershootInterpolator(2f)).start()
            onClick()
        }
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(scale).scaleY(scale).setDuration(90)
                        .setInterpolator(DecelerateInterpolator()).start()
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(220)
                        .setInterpolator(OvershootInterpolator(2.2f)).start()
            }
            false
        }
    }

    fun fadeInUp(view: View, delay: Long = 0, distanceDp: Float = 18f) {
        val distance = dpf(view.context, distanceDp)
        view.alpha = 0f
        view.translationY = distance
        view.animate().alpha(1f).translationY(0f)
            .setStartDelay(delay)
            .setDuration(420)
            .setInterpolator(DecelerateInterpolator(1.4f))
            .start()
    }

    fun popIn(view: View, delay: Long = 0) {
        view.alpha = 0f
        view.scaleX = 0.86f
        view.scaleY = 0.86f
        view.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(delay)
            .setDuration(360)
            .setInterpolator(OvershootInterpolator(1.6f))
            .start()
    }

    fun fadeOutShrink(view: View) {
        view.animate().alpha(0f).translationY(-Ui.dpf(view.context, 4f)).setDuration(120).withEndAction {
            view.visibility = View.GONE
            view.alpha = 1f
            view.translationY = 0f
        }.start()
    }

    fun sectionTitle(context: Context, palette: Palette, title: String, action: String? = null, onAction: (() -> Unit)? = null): LinearLayout {
        val row = row(context)
        val t = text(context, title, 15f, palette.text, bold = true, font = "sans-serif-black")
        row.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (action != null) {
            val a = text(context, action, 12.5f, palette.yellowDeep, bold = true)
            a.setPadding(dp(context, 8f), dp(context, 4f), 0, dp(context, 4f))
            if (onAction != null) pressable(a, onClick = onAction)
            row.addView(a)
        }
        return row
    }
}
