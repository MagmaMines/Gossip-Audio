package xyz.beyoneer.gossipaudio.ui.widgets

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import xyz.beyoneer.gossipaudio.ui.theme.Palette
import xyz.beyoneer.gossipaudio.ui.theme.Ui
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pill button with the mandatory yellow fill + dark-yellow outline combo. */
class BrandButton(
    context: Context,
    private val palette: Palette,
    text: String,
    private val variant: Variant = Variant.PRIMARY,
    icon: Icons.Kind? = null,
    private val compact: Boolean = false
) : LinearLayout(context) {

    enum class Variant { PRIMARY, INK, GHOST, DANGER }

    private val label = TextView(context)
    private var iconView: IconView? = null
    private var loading = false
    private var baseText: String = text

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(
            Ui.dp(context, if (compact) 14f else 22f),
            Ui.dp(context, if (compact) 9f else 15f),
            Ui.dp(context, if (compact) 14f else 22f),
            Ui.dp(context, if (compact) 9f else 15f)
        )
        isClickable = true
        isFocusable = true
        background = when (variant) {
            Variant.PRIMARY -> Ui.gradient(
                intArrayOf(palette.yellowBright, palette.yellow),
                Ui.dpf(context, 100f),
                strokeColor = palette.yellowDeep, strokeWidth = Ui.dp(context, 2f)
            )
            Variant.INK -> Ui.gradient(
                intArrayOf(palette.dark.let { if (it) 0xFF23231F.toInt() else 0xFF17171A.toInt() }, Color.BLACK),
                Ui.dpf(context, 100f),
                strokeColor = palette.yellow, strokeWidth = Ui.dp(context, 1.5f)
            )
            Variant.GHOST -> Ui.rounded(Color.TRANSPARENT, Ui.dpf(context, 100f), palette.outline, Ui.dp(context, 1.5f))
            Variant.DANGER -> Ui.rounded(0x22FF5252, Ui.dpf(context, 100f), palette.danger, Ui.dp(context, 1.5f))
        }
        elevation = Ui.dpf(context, if (variant == Variant.PRIMARY) 3f else 1f)

        val textColor = when (variant) {
            Variant.PRIMARY -> palette.onYellow
            Variant.INK -> Color.WHITE
            Variant.GHOST -> palette.text
            Variant.DANGER -> palette.danger
        }
        if (icon != null) {
            iconView = IconView(context, icon, textColor, if (compact) 18f else 21f)
            addView(iconView)
            addView(View(context).apply { layoutParams = ViewGroup.LayoutParams(Ui.dp(context, 8f), 1) })
        }
        label.text = text
        label.setTextColor(textColor)
        label.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (compact) 13.5f else 16f)
        label.includeFontPadding = false
        label.gravity = Gravity.CENTER
        addView(label)

        Ui.pressable(this, 0.96f)
        if (variant == Variant.PRIMARY) startGlow()
    }

    private fun startGlow() {
        // a very soft breathing glow — subtle, 120fps friendly, can be disabled
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { v ->
                val f = v.animatedValue as Float
                elevation = Ui.dpf(context, 2.5f + f * 3.5f)
            }
            start()
        }
    }

    fun setText(value: String) {
        baseText = value
        label.text = value
    }

    fun text(): String = baseText

    fun setLoading(value: Boolean) {
        loading = value
        isEnabled = !value
        label.text = if (value) "Working\u2026" else baseText
        iconView?.visibility = if (value) View.GONE else View.VISIBLE
        alpha = if (value) 0.85f else 1f
    }

    fun setOnClick(action: () -> Unit) {
        setOnClickListener {
            if (loading) return@setOnClickListener
            action()
        }
    }
}

/** Rounded filter chip. */
class Chip(
    context: Context,
    private val palette: Palette,
    text: String,
    selected: Boolean = false,
    private val accent: Int? = null,
    private val onClick: (() -> Unit)? = null
) : TextView(context) {

    private var isSelected = selected

    init {
        this.text = text
        gravity = Gravity.CENTER
        setPadding(Ui.dp(context, 14f), Ui.dp(context, 8f), Ui.dp(context, 14f), Ui.dp(context, 8f))
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12.5f)
        includeFontPadding = false
        paint()
        setOnClickListener {
            isSelected = !isSelected
            paint()
            Ui.pressable(this, 0.94f)
            onClick?.invoke()
        }
        Ui.pressable(this, 0.94f)
    }

    fun setSelectedState(value: Boolean) {
        isSelected = value
        paint()
    }

    fun selected(): Boolean = isSelected

    private fun paint() {
        val a = accent ?: palette.yellow
        background = if (isSelected) {
            Ui.gradient(
                intArrayOf(Ui.blend(a, Color.WHITE, 0.25f), a),
                Ui.dpf(context, 100f),
                strokeColor = palette.yellowDeep, strokeWidth = Ui.dp(context, 1.5f)
            )
        } else {
            Ui.rounded(palette.card, Ui.dpf(context, 100f), palette.outline, Ui.dp(context, 1f))
        }
        setTextColor(if (isSelected) palette.onYellow else palette.textDim)
    }
}

/** Minimal custom switch with a springy knob. */
class ToggleSwitch(context: Context, private val palette: Palette, initial: Boolean = false) : View(context) {

    var checked = initial
        set(value) {
            field = value
            animate()
            invalidate()
        }
    var onChanged: ((Boolean) -> Unit)? = null

    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG)
    private var anim = if (initial) 1f else 0f

    init {
        layoutParams = ViewGroup.LayoutParams(Ui.dp(context, 52f), Ui.dp(context, 30f))
        setOnClickListener {
            checked = !checked
            ValueAnimator.ofFloat(anim, if (checked) 1f else 0f).apply {
                duration = 220
                addUpdateListener {
                    anim = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
            onChanged?.invoke(checked)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()
        val w = width.toFloat()
        track.color = Ui.blend(palette.outline, palette.yellow, anim)
        canvas.drawRoundRect(RectF(0f, 0f, w, h), h / 2, h / 2, track)
        knob.color = if (anim > 0.5f) palette.onYellow else palette.textDim
        val r = h * 0.38f
        val cx = h / 2 + (w - h) * anim
        canvas.drawCircle(cx, h / 2, r, knob)
    }
}

/** Segmented control used for theme + format pickers. */
class Segmented(context: Context, private val palette: Palette, options: List<String>, selectedIndex: Int = 0) : android.widget.FrameLayout(context) {

    var selectedIndex = selectedIndex
        private set
    var onSelect: ((Int) -> Unit)? = null
    private val labels = ArrayList<TextView>()
    private val indicator = View(context)

    init {
        background = Ui.rounded(palette.card, Ui.dpf(context, 100f), palette.outline, Ui.dp(context, 1f))
        setPadding(Ui.dp(context, 4f), Ui.dp(context, 4f), Ui.dp(context, 4f), Ui.dp(context, 4f))
        indicator.background = Ui.gradient(
            intArrayOf(palette.yellowBright, palette.yellow), Ui.dpf(context, 100f),
            strokeColor = palette.yellowDeep, strokeWidth = Ui.dp(context, 1.5f)
        )
        addView(indicator, LayoutParams(0, LayoutParams.MATCH_PARENT))
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        options.forEachIndexed { index, option ->
            val tv = TextView(context).apply {
                text = option
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12.5f)
                includeFontPadding = false
                setPadding(Ui.dp(context, 6f), Ui.dp(context, 8f), Ui.dp(context, 6f), Ui.dp(context, 8f))
                isClickable = true
                setOnClickListener { select(index) }
            }
            labels.add(tv)
            row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        updateColors()
        post { layoutIndicator() }
    }

    private fun select(index: Int) {
        selectedIndex = index
        updateColors()
        layoutIndicator()
        onSelect?.invoke(index)
    }

    private fun layoutIndicator() {
        if (width == 0 || labels.isEmpty()) return
        val cell = width.toFloat() / labels.size
        val lp = indicator.layoutParams as LayoutParams
        lp.width = (cell - Ui.dp(context, 1f)).toInt()
        lp.height = height - Ui.dp(context, 8f)
        lp.leftMargin = (cell * selectedIndex).toInt() + Ui.dp(context, 1f)
        lp.topMargin = Ui.dp(context, 4f)
        indicator.layoutParams = lp
    }

    private fun updateColors() {
        labels.forEachIndexed { index, tv ->
            tv.setTextColor(if (index == selectedIndex) palette.onYellow else palette.textDim)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutIndicator()
    }
}

/** Smooth custom slider (no SeekBar theming pain, full control of the look). */
class NeonSlider(
    context: Context,
    private val palette: Palette,
    private val min: Float,
    private val max: Float,
    value: Float,
    private val step: Float = 0.01f
) : View(context) {

    var value: Float = value.coerceIn(min, max)
        set(v) {
            field = v.coerceIn(min, max)
            invalidate()
        }
    var onChanged: ((Float) -> Unit)? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbRing = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 40f))
        fillPaint.shader = LinearGradient(
            0f, 0f, 600f, 0f,
            intArrayOf(palette.yellowDeep, palette.yellowBright, palette.yellow),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cy = height / 2f
        val pad = Ui.dpf(context, 14f)
        val trackH = Ui.dpf(context, 8f)
        val usable = width - pad * 2
        val frac = (value - min) / (max - min)
        trackPaint.color = palette.outline
        canvas.drawRoundRect(RectF(pad, cy - trackH / 2, width - pad, cy + trackH / 2), trackH / 2, trackH / 2, trackPaint)
        val end = pad + usable * frac
        canvas.drawRoundRect(RectF(pad, cy - trackH / 2, end, cy + trackH / 2), trackH / 2, trackH / 2, fillPaint)
        thumbPaint.color = palette.yellow
        thumbRing.color = palette.yellowDeep
        thumbRing.style = Paint.Style.STROKE
        thumbRing.strokeWidth = Ui.dpf(context, 2f)
        canvas.drawCircle(end, cy, Ui.dpf(context, 10f), thumbPaint)
        canvas.drawCircle(end, cy, Ui.dpf(context, 10f), thumbRing)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val pad = Ui.dpf(context, 14f)
                val usable = (width - pad * 2).coerceAtLeast(1f)
                val frac = ((event.x - pad) / usable).coerceIn(0f, 1f)
                val raw = min + frac * (max - min)
                val snapped = (raw / step).roundToInt() * step
                if (abs(snapped - value) >= step * 0.5f) {
                    value = snapped
                    onChanged?.invoke(value)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }
}
