package xyz.beyoneer.gossipaudio.ui.widgets

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import xyz.beyoneer.gossipaudio.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The GossipAudio loading signature: a butterfly flapping through a ring of
 * light. Pure Canvas drawing, capped at display refresh, zero allocations in
 * onDraw, and it freezes politely when the user enables reduced motion.
 */
class ButterflyLoader(context: Context, private val palette: Palette, private val sizeDp: Float = 120f) : View(context) {

    private var animator: ValueAnimator? = null
    private var t = 0f
    var reduceMotion: Boolean = false
    var label: String = ""
        set(value) {
            field = value
            invalidate()
        }

    private val wingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.text }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.textDim
        textSize = context.resources.displayMetrics.scaledDensity * 12f
        textAlign = Paint.Align.CENTER
    }
    private val ringPath = Path()
    private val wingPath = Path()
    private val veinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    init {
        setWillNotDraw(false)
        layoutParams = ViewGroup.LayoutParams(Ui2.dp(context, sizeDp), Ui2.dp(context, sizeDp))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        glowPaint.shader = RadialGradient(
            w / 2f, h / 2f, w * 0.52f,
            intArrayOf(withAlpha(palette.yellow, 0.35f), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        wingPaint.shader = LinearGradient(
            0f, h * 0.2f, w.toFloat(), h * 0.8f,
            intArrayOf(palette.yellowBright, palette.yellow, palette.yellowDeep),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    fun start() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2600
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                t = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!reduceMotion) start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) * 0.42f

        canvas.drawCircle(cx, cy, r * 1.22f, glowPaint)

        // orbit ring + moving spark
        ringPaint.color = withAlpha(palette.yellow, 0.22f)
        ringPaint.strokeWidth = r * 0.035f
        canvas.drawCircle(cx, cy, r, ringPaint)
        ringPaint.color = withAlpha(palette.yellowBright, 0.9f)
        ringPaint.strokeWidth = r * 0.06f
        ringPath.reset()
        val sweep = 70f
        val startAngle = if (reduceMotion) 0f else (t * 360f) - 90f
        ringPath.addArc(cx - r, cy - r, cx + r, cy + r, startAngle, sweep)
        canvas.drawPath(ringPath, ringPaint)

        // butterfly
        val flap = if (reduceMotion) 0.85f else (0.55 + 0.45 * sin(t.toDouble() * 2.0 * PI * 3.0)).toFloat()
        val scale = r * 0.78f
        drawWing(canvas, cx, cy, scale, flap, true)
        drawWing(canvas, cx, cy, scale, flap, false)
        bodyPaint.color = palette.text
        canvas.drawRoundRect(
            cx - scale * 0.05f, cy - scale * 0.42f,
            cx + scale * 0.05f, cy + scale * 0.5f,
            scale * 0.05f, scale * 0.05f, bodyPaint
        )
        // antennae
        ringPaint.color = palette.text
        ringPaint.strokeWidth = scale * 0.02f
        canvas.drawLine(cx - scale * 0.02f, cy - scale * 0.4f, cx - scale * 0.18f, cy - scale * 0.58f, ringPaint)
        canvas.drawLine(cx + scale * 0.02f, cy - scale * 0.4f, cx + scale * 0.18f, cy - scale * 0.58f, ringPaint)

        // sparkles
        dotPaint.color = withAlpha(palette.yellowBright, 0.85f)
        for (i in 0 until 3) {
            val angle = (t * 2 * PI + i * 2.1).toFloat()
            val radius = r * (0.85f + 0.15f * sin(angle * 2f))
            val dx = cx + cos(angle) * radius
            val dy = cy + sin(angle) * radius
            canvas.drawCircle(dx, dy, r * 0.035f, dotPaint)
        }

        if (label.isNotEmpty()) {
            canvas.drawText(label, cx, cy + r * 1.55f, textPaint)
        }
    }

    private fun drawWing(canvas: Canvas, cx: Float, cy: Float, scale: Float, flap: Float, left: Boolean) {
        val dir = if (left) -1f else 1f
        val path = wingPath
        path.reset()
        path.moveTo(cx, cy - scale * 0.08f)
        path.cubicTo(
            cx + dir * scale * 0.5f * flap, cy - scale * 0.85f,
            cx + dir * scale * 1.05f * flap, cy - scale * 0.55f,
            cx + dir * scale * 0.62f * flap, cy + scale * 0.02f
        )
        path.cubicTo(
            cx + dir * scale * 1.0f * flap, cy + scale * 0.3f,
            cx + dir * scale * 0.55f * flap, cy + scale * 0.72f,
            cx, cy + scale * 0.3f
        )
        path.close()
        canvas.drawPath(path, wingPaint)
        // wing veins
        veinPaint.color = withAlpha(palette.onYellow, 0.25f)
        veinPaint.strokeWidth = scale * 0.018f
        canvas.drawLine(cx, cy - scale * 0.04f, cx + dir * scale * 0.6f * flap, cy - scale * 0.42f, veinPaint)
        canvas.drawLine(cx, cy + scale * 0.1f, cx + dir * scale * 0.52f * flap, cy + scale * 0.38f, veinPaint)
    }

    private fun withAlpha(color: Int, alpha: Float): Int = Color.argb(
        (alpha.coerceIn(0f, 1f) * 255).toInt(),
        Color.red(color), Color.green(color), Color.blue(color)
    )
}
