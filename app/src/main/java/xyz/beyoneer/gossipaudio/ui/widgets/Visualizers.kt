package xyz.beyoneer.gossipaudio.ui.widgets

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import xyz.beyoneer.gossipaudio.R
import xyz.beyoneer.gossipaudio.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Live waveform: real peaks from the clip, seekable, and it dances while playing. */
class WaveformView(context: Context, private val palette: Palette, private val bars: Int = 56) : View(context) {

    private var peaks = FloatArray(0)
    private var phase = 0f
    var progress = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }
    var playing = false
        set(value) {
            field = value
            if (value) start() else stop()
            invalidate()
        }
    var onSeek: ((Float) -> Unit)? = null
    private var animator: ValueAnimator? = null
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val idlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.outline }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui2.dp(context, 78f))
        activePaint.shader = LinearGradient(
            0f, 0f, 0f, Ui2.dp(context, 78f).toFloat(),
            intArrayOf(palette.yellowBright, palette.yellow, palette.yellowDeep),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
    }

    fun setPeaks(values: FloatArray) {
        peaks = values
        invalidate()
    }

    fun setProgressFraction(fraction: Float) {
        progress = fraction
    }

    private fun start() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stop() {
        animator?.cancel()
        animator = null
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val count = bars
        val gap = width.toFloat() / count
        val barW = gap * 0.55f
        val cx = height / 2f
        val maxH = height * 0.82f
        val activeIndex = (progress * count).toInt()
        for (i in 0 until count) {
            val raw = if (peaks.isNotEmpty()) {
                peaks[(i * peaks.size / count).coerceIn(0, peaks.size - 1)]
            } else {
                0.35f + 0.3f * abs(sin(i * 0.7f))
            }
            val dance = if (playing) 1f + 0.22f * sin((phase * 2f * PI).toFloat() + i * 0.45f) else 1f
            val h = (maxH * (0.16f + raw * 0.84f) * dance).coerceIn(barW, height * 0.96f)
            val left = i * gap + (gap - barW) / 2f
            rect.set(left, cx - h / 2, left + barW, cx + h / 2)
            val isActive = i <= activeIndex
            if (isActive) {
                glowPaint.color = Color.argb(38, Color.red(palette.yellow), Color.green(palette.yellow), Color.blue(palette.yellow))
                canvas.drawRoundRect(
                    RectF(rect.left - barW * 0.3f, rect.top - barW * 0.3f, rect.right + barW * 0.3f, rect.bottom + barW * 0.3f),
                    barW, barW, glowPaint
                )
                canvas.drawRoundRect(rect, barW / 2, barW / 2, activePaint)
            } else {
                canvas.drawRoundRect(rect, barW / 2, barW / 2, idlePaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val f = (event.x / width).coerceIn(0f, 1f)
                progress = f
                onSeek?.invoke(f)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
}

/** CPU / RAM history chart used by the live telemetry panel. */
class Sparkline(context: Context, private val palette: Palette) : View(context) {

    private var data: List<Pair<Float, Float>> = emptyList()
    private val cpuPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Ui2.dpf(context, 2f)
        color = palette.yellow
        strokeCap = Paint.Cap.ROUND
    }
    private val memPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Ui2.dpf(context, 1.5f)
        color = if (palette.dark) 0x99FFFFFF.toInt() else 0x998A6A00.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.outline
        strokeWidth = 1f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui2.dp(context, 56f))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(Ui2Alpha(palette.yellow, 0.35f), Color.TRANSPARENT),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
    }

    fun setData(values: List<Pair<Float, Float>>) {
        data = values
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (i in 1..2) {
            val y = height * i / 3f
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }
        if (data.size < 2) return
        val cpu = Path()
        val mem = Path()
        val area = Path()
        data.forEachIndexed { i, pair ->
            val x = width * i / (data.size - 1).toFloat()
            val yc = height - (pair.first / 100f) * height * 0.92f - height * 0.04f
            val ym = height - (pair.second / 100f) * height * 0.92f - height * 0.04f
            if (i == 0) {
                cpu.moveTo(x, yc)
                mem.moveTo(x, ym)
                area.moveTo(x, height.toFloat())
                area.lineTo(x, yc)
            } else {
                cpu.lineTo(x, yc)
                mem.lineTo(x, ym)
                area.lineTo(x, yc)
            }
        }
        area.lineTo(width.toFloat(), height.toFloat())
        area.close()
        canvas.drawPath(area, fillPaint)
        canvas.drawPath(mem, memPaint)
        canvas.drawPath(cpu, cpuPaint)
    }

    private fun Ui2Alpha(color: Int, alpha: Float): Int = Color.argb(
        (alpha * 255).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color)
    )
}

/** Circular avatar with the voice's brand colour and a soft ring. */
class AvatarView(
    context: Context,
    private val palette: Palette,
    letter: String,
    private val accent: Int,
    private val sizeDp: Float = 46f,
    private val selected: Boolean = false
) : View(context) {

    private val initial = letter.take(1).uppercase()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        color = palette.onYellow
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        layoutParams = ViewGroup.LayoutParams(Ui2.dp(context, sizeDp), Ui2.dp(context, sizeDp))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fill.shader = RadialGradient(
            w * 0.35f, h * 0.3f, w * 0.8f,
            intArrayOf(Color.WHITE, accent, Ui2Darken(accent)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        glow.shader = RadialGradient(
            w / 2f, h / 2f, w * 0.62f,
            intArrayOf(Ui2Alpha(accent, 0.35f), Color.TRANSPARENT),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        textPaint.textSize = w * 0.42f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) * 0.44f
        canvas.drawCircle(cx, cy, r * 1.35f, glow)
        canvas.drawCircle(cx, cy, r, fill)
        ring.color = if (selected) palette.yellow else Ui2Alpha(palette.outline, 0.9f)
        ring.strokeWidth = if (selected) Ui2.dpf(context, 2.5f) else Ui2.dpf(context, 1.2f)
        canvas.drawCircle(cx, cy, r, ring)
        canvas.drawText(initial, cx, cy + textPaint.textSize * 0.36f, textPaint)
        if (selected) {
            ring.color = Ui2Alpha(palette.yellow, 0.35f)
            ring.strokeWidth = Ui2.dpf(context, 1f)
            canvas.drawCircle(cx, cy, r + Ui2.dp(context, 4f), ring)
        }
    }

    private fun Ui2Alpha(color: Int, alpha: Float): Int = Color.argb(
        (alpha * 255).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color)
    )

    private fun Ui2Darken(color: Int): Int = Color.rgb(
        (Color.red(color) * 0.55f).toInt().coerceIn(0, 255),
        (Color.green(color) * 0.55f).toInt().coerceIn(0, 255),
        (Color.blue(color) * 0.55f).toInt().coerceIn(0, 255)
    )
}

/** Rounded artwork view with a graceful fallback to the brand glyph. */
class CoverView(context: Context, private val palette: Palette, private val sizeDp: Float = 64f, private val circular: Boolean = false) : View(context) {

    private var bitmap: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, 100f, 100f,
            intArrayOf(palette.yellowDeep, palette.yellow, palette.yellowBright),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
    }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = palette.yellowDeep
        strokeWidth = Ui2.dpf(context, 1.2f)
    }
    private var fallback: Bitmap? = null
    private val rect = RectF()

    init {
        layoutParams = ViewGroup.LayoutParams(Ui2.dp(context, sizeDp), Ui2.dp(context, sizeDp))
        fallback = BitmapFactory.decodeResource(context.resources, R.drawable.gossipaudio_glyph)
    }

    fun setImage(bmp: Bitmap?) {
        bitmap = bmp
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = Ui2.dp(context, 1f).toFloat()
        rect.set(pad, pad, width - pad, height - pad)
        val radius = if (circular) width / 2f else width * 0.26f
        if (circular) {
            canvas.save()
            val path = Path().apply { addCircle(width / 2f, height / 2f, width / 2f - pad, Path.Direction.CW) }
            canvas.clipPath(path)
            canvas.drawRect(rect, bg)
            val bmp = bitmap
            if (bmp != null) {
                canvas.drawBitmap(bmp, null, rect, paint)
            } else {
                canvas.drawRect(rect, bg)
            }
            canvas.restore()
            canvas.drawCircle(width / 2f, height / 2f, width / 2f - pad, border)
        } else {
            canvas.drawRoundRect(rect, radius, radius, bg)
            val bmp = bitmap
            if (bmp != null) {
                val path = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
                canvas.save()
                canvas.clipPath(path)
                // centre-crop
                val scale = maxOf(rect.width() / bmp.width, rect.height() / bmp.height)
                val w = bmp.width * scale
                val h = bmp.height * scale
                val left = rect.centerX() - w / 2
                val top = rect.centerY() - h / 2
                canvas.drawBitmap(bmp, null, RectF(left, top, left + w, top + h), paint)
                canvas.restore()
            } else {
                val glyph = fallback
                if (glyph != null) {
                    val s = minOf(width, height) * 0.55f
                    val left = (width - s) / 2
                    val top = (height - s) / 2
                    canvas.drawBitmap(glyph, null, RectF(left, top, left + s, top + s), paint)
                }
            }
            canvas.drawRoundRect(rect, radius, radius, border)
        }
    }
}

/** Tiny animated equalizer used in the mini player. */
class MiniBars(context: Context, private val palette: Palette, private val count: Int = 4) : View(context) {

    private var animator: ValueAnimator? = null
    private var phase = 0f
    var playing = false
        set(value) {
            field = value
            if (value) start() else stop()
        }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.yellow }

    init {
        layoutParams = ViewGroup.LayoutParams(Ui2.dp(context, 20f), Ui2.dp(context, 20f))
    }

    private fun start() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stop() {
        animator?.cancel()
        animator = null
        invalidate()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gap = width.toFloat() / count
        val barW = gap * 0.5f
        for (i in 0 until count) {
            val base = if (playing) {
                0.3f + 0.6f * abs(sin((phase * 2f * PI).toFloat() + i * 0.8f))
            } else {
                0.25f
            }
            val h = height * base
            canvas.drawRoundRect(
                RectF(i * gap + (gap - barW) / 2, (height - h) / 2, i * gap + (gap + barW) / 2, (height + h) / 2),
                barW / 2, barW / 2, paint
            )
        }
    }
}

/** Rotating vinyl for the full player. Freezes when reduced motion is on. */
class VinylView(context: Context, private val palette: Palette, private val sizeDp: Float = 240f) : View(context) {

    var playing = false
        set(value) {
            field = value
            if (value) start() else stop()
        }
    var reduceMotion = false
    private var angle = 0f
    private var animator: ValueAnimator? = null
    private var bitmap: Bitmap? = null
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val groove = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = if (palette.dark) 0x22FFFFFF else 0x22000000
        strokeWidth = Ui2.dpf(context, 1f)
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Ui2.dpf(context, 2f)
        color = palette.yellowDeep
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val art = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val spindle = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fallback = BitmapFactory.decodeResource(context.resources, R.drawable.gossipaudio_glyph)

    init {
        layoutParams = ViewGroup.LayoutParams(Ui2.dp(context, sizeDp), Ui2.dp(context, sizeDp))
    }

    fun setArtwork(bmp: Bitmap?) {
        bitmap = bmp
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        disc.color = if (palette.dark) 0xFF17171A.toInt() else 0xFFFFFFFF.toInt()
        spindle.color = palette.bg
        glow.shader = RadialGradient(
            w / 2f, h / 2f, w * 0.52f,
            intArrayOf(Color.argb(70, Color.red(palette.yellow), Color.green(palette.yellow), Color.blue(palette.yellow)), Color.TRANSPARENT),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
    }

    private fun start() {
        if (animator != null || reduceMotion) return
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 9000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                angle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stop() {
        animator?.cancel()
        animator = null
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) * 0.46f
        canvas.drawCircle(cx, cy, r * 1.18f, glow)
        canvas.drawCircle(cx, cy, r, disc)
        for (i in 1..5) {
            canvas.drawCircle(cx, cy, r * (0.55f + i * 0.075f), groove)
        }
        val artR = r * 0.52f
        // label area: a clean disc, then the artwork *contained* inside it (cover
        // art used to be cropped to a circle, which decapitated our own logo)
        canvas.drawCircle(cx, cy, artR, disc)
        canvas.save()
        canvas.rotate(angle, cx, cy)
        val bmp = bitmap ?: fallback
        if (bmp != null) {
            val box = artR * 1.62f
            val scale = minOf(box / bmp.width, box / bmp.height)
            val w = bmp.width * scale
            val h = bmp.height * scale
            canvas.drawBitmap(bmp, null, RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), art)
        }
        canvas.restore()
        canvas.drawCircle(cx, cy, artR, ring)
        canvas.drawCircle(cx, cy, r, ring)
        // spindle
        canvas.drawCircle(cx, cy, r * 0.045f, spindle)
        canvas.drawCircle(cx, cy, r * 0.045f, ring)
    }
}
