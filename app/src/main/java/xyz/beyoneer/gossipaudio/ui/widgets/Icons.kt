package xyz.beyoneer.gossipaudio.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup

/** Hand-drawn vector icons — no icon font, no external assets, perfectly crisp. */
object Icons {

    enum class Kind {
        PLAY, PAUSE, STOP, REPLAY, NEXT, PREV, MIC, WAVE, HEART, DOWNLOAD, SHARE, GEAR,
        BACK, CLOSE, CHECK, SEARCH, PLUS, DICE, BELL, MOON, SUN, TRASH, EDIT, INFO, HELP,
        SPEED, TIMER, FX, SPARKLE, PERSON, LIST, HOME
    }

    fun draw(canvas: Canvas, kind: Kind, cx: Float, cy: Float, size: Float, color: Int, stroke: Float = size * 0.12f, filled: Boolean = false) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.isAntiAlias = true
        }
        val s = size / 2f
        fun x(v: Float) = cx + v * s
        fun y(v: Float) = cy + v * s
        val fill = Paint(p).apply { style = Paint.Style.FILL }
        when (kind) {
            Kind.PLAY -> {
                val path = Path().apply {
                    moveTo(x(-0.42f), y(-0.55f)); lineTo(x(0.58f), y(0f)); lineTo(x(-0.42f), y(0.55f)); close()
                }
                canvas.drawPath(path, fill)
            }
            Kind.PAUSE -> {
                val w = s * 0.26f
                val h = s * 1.1f
                canvas.drawRoundRect(RectF(x(-0.46f), cy - h / 2, x(-0.46f) + w, cy + h / 2), w * 0.4f, w * 0.4f, fill)
                canvas.drawRoundRect(RectF(x(0.20f), cy - h / 2, x(0.20f) + w, cy + h / 2), w * 0.4f, w * 0.4f, fill)
            }
            Kind.STOP -> canvas.drawRoundRect(
                RectF(x(-0.45f), y(-0.45f), x(0.45f), y(0.45f)), s * 0.18f, s * 0.18f, fill
            )
            Kind.REPLAY -> {
                val oval = RectF(x(-0.55f), y(-0.55f), x(0.55f), y(0.55f))
                canvas.drawArc(oval, 40f, 285f, false, p)
                val path = Path().apply {
                    moveTo(x(0.02f), y(-0.85f)); lineTo(x(0.55f), y(-0.42f)); lineTo(x(-0.05f), y(-0.22f)); close()
                }
                canvas.drawPath(path, fill)
            }
            Kind.NEXT, Kind.PREV -> {
                val dir = if (kind == Kind.NEXT) 1f else -1f
                val path = Path().apply {
                    moveTo(x(-0.5f * dir), y(-0.5f)); lineTo(x(0.25f * dir), y(0f)); lineTo(x(-0.5f * dir), y(0.5f)); close()
                }
                canvas.drawPath(path, fill)
                canvas.drawLine(x(0.55f * dir), y(-0.5f), x(0.55f * dir), y(0.5f), p)
            }
            Kind.MIC -> {
                canvas.drawRoundRect(
                    RectF(x(-0.26f), y(-0.72f), x(0.26f), y(0.18f)), s * 0.26f, s * 0.26f, if (filled) fill else p
                )
                canvas.drawArc(RectF(x(-0.55f), y(-0.35f), x(0.55f), y(0.5f)), 0f, 180f, false, p)
                canvas.drawLine(cx, y(0.5f), cx, y(0.78f), p)
            }
            Kind.WAVE -> {
                val heights = floatArrayOf(0.35f, 0.72f, 0.5f, 0.9f, 0.4f)
                heights.forEachIndexed { i, h ->
                    val px = x(-0.66f + i * 0.33f)
                    canvas.drawLine(px, cy - s * h * 0.5f, px, cy + s * h * 0.5f, p)
                }
            }
            Kind.HEART -> {
                val path = Path().apply {
                    moveTo(x(0f), y(0.62f))
                    cubicTo(x(-0.95f), y(-0.05f), x(-0.55f), y(-0.8f), x(0f), y(-0.32f))
                    cubicTo(x(0.55f), y(-0.8f), x(0.95f), y(-0.05f), x(0f), y(0.62f))
                    close()
                }
                canvas.drawPath(path, fill)
            }
            Kind.DOWNLOAD -> {
                canvas.drawLine(cx, y(-0.68f), cx, y(0.22f), p)
                val arrow = Path().apply {
                    moveTo(x(-0.34f), y(-0.12f)); lineTo(cx, y(0.3f)); lineTo(x(0.34f), y(-0.12f))
                }
                canvas.drawPath(arrow, p)
                canvas.drawLine(x(-0.55f), y(0.66f), x(0.55f), y(0.66f), p)
            }
            Kind.SHARE -> {
                canvas.drawCircle(x(-0.55f), y(-0.42f), s * 0.16f, fill)
                canvas.drawCircle(x(0.55f), y(0f), s * 0.16f, fill)
                canvas.drawCircle(x(-0.55f), y(0.42f), s * 0.16f, fill)
                canvas.drawLine(x(-0.4f), y(-0.34f), x(0.4f), y(-0.06f), p)
                canvas.drawLine(x(-0.4f), y(0.34f), x(0.4f), y(0.06f), p)
            }
            Kind.GEAR -> {
                canvas.drawCircle(cx, cy, s * 0.42f, p)
                canvas.drawCircle(cx, cy, s * 0.14f, p)
                for (i in 0 until 8) {
                    val a = Math.toRadians((i * 45).toDouble())
                    val r1 = s * 0.55f
                    val r2 = s * 0.78f
                    canvas.drawLine(
                        cx + (Math.cos(a) * r1).toFloat(), cy + (Math.sin(a) * r1).toFloat(),
                        cx + (Math.cos(a) * r2).toFloat(), cy + (Math.sin(a) * r2).toFloat(), p
                    )
                }
            }
            Kind.BACK -> {
                val path = Path().apply {
                    moveTo(x(0.18f), y(-0.5f)); lineTo(x(-0.32f), y(0f)); lineTo(x(0.18f), y(0.5f))
                }
                canvas.drawPath(path, p)
            }
            Kind.CLOSE -> {
                canvas.drawLine(x(-0.42f), y(-0.42f), x(0.42f), y(0.42f), p)
                canvas.drawLine(x(0.42f), y(-0.42f), x(-0.42f), y(0.42f), p)
            }
            Kind.CHECK -> {
                val path = Path().apply {
                    moveTo(x(-0.5f), y(0.05f)); lineTo(x(-0.12f), y(0.42f)); lineTo(x(0.55f), y(-0.4f))
                }
                canvas.drawPath(path, p)
            }
            Kind.SEARCH -> {
                canvas.drawCircle(x(-0.12f), y(-0.12f), s * 0.52f, p)
                canvas.drawLine(x(0.26f), y(0.26f), x(0.66f), y(0.66f), p)
            }
            Kind.PLUS -> {
                canvas.drawLine(cx, y(-0.5f), cx, y(0.5f), p)
                canvas.drawLine(x(-0.5f), cy, x(0.5f), cy, p)
            }
            Kind.DICE -> {
                val rect = RectF(x(-0.62f), y(-0.62f), x(0.62f), y(0.62f))
                canvas.drawRoundRect(rect, s * 0.22f, s * 0.22f, p)
                canvas.drawCircle(x(-0.25f), y(-0.25f), s * 0.1f, fill)
                canvas.drawCircle(cx, cy, s * 0.1f, fill)
                canvas.drawCircle(x(0.25f), y(0.25f), s * 0.1f, fill)
            }
            Kind.BELL -> {
                val path = Path().apply {
                    moveTo(x(-0.55f), y(0.3f))
                    cubicTo(x(-0.45f), y(-0.1f), x(-0.42f), y(-0.62f), x(0f), y(-0.62f))
                    cubicTo(x(0.42f), y(-0.62f), x(0.45f), y(-0.1f), x(0.55f), y(0.3f))
                    close()
                }
                canvas.drawPath(path, p)
                canvas.drawLine(x(-0.62f), y(0.34f), x(0.62f), y(0.34f), p)
                canvas.drawCircle(cx, y(0.56f), s * 0.12f, fill)
            }
            Kind.MOON -> {
                val path = Path().apply {
                    addCircle(cx, cy, s * 0.62f, Path.Direction.CW)
                    addCircle(x(0.3f), y(-0.25f), s * 0.55f, Path.Direction.CCW)
                }
                canvas.drawPath(path, fill)
            }
            Kind.SUN -> {
                canvas.drawCircle(cx, cy, s * 0.36f, fill)
                for (i in 0 until 8) {
                    val a = Math.toRadians((i * 45).toDouble())
                    canvas.drawLine(
                        cx + (Math.cos(a) * s * 0.58f).toFloat(), cy + (Math.sin(a) * s * 0.58f).toFloat(),
                        cx + (Math.cos(a) * s * 0.82f).toFloat(), cy + (Math.sin(a) * s * 0.82f).toFloat(), p
                    )
                }
            }
            Kind.TRASH -> {
                canvas.drawLine(x(-0.55f), y(-0.45f), x(0.55f), y(-0.45f), p)
                canvas.drawLine(x(-0.2f), y(-0.45f), x(-0.2f), y(-0.62f), p)
                canvas.drawLine(x(0.2f), y(-0.45f), x(0.2f), y(-0.62f), p)
                val body = Path().apply {
                    moveTo(x(-0.46f), y(-0.36f)); lineTo(x(-0.32f), y(0.58f)); lineTo(x(0.32f), y(0.58f)); lineTo(x(0.46f), y(-0.36f))
                }
                canvas.drawPath(body, p)
            }
            Kind.EDIT -> {
                canvas.drawLine(x(-0.4f), y(0.42f), x(-0.2f), y(0.2f), p)
                val pencil = Path().apply {
                    moveTo(x(-0.2f), y(0.2f)); lineTo(x(0.5f), y(-0.5f)); lineTo(x(0.32f), y(-0.66f)); lineTo(x(-0.36f), y(0.04f)); close()
                }
                canvas.drawPath(pencil, if (filled) fill else p)
            }
            Kind.INFO -> {
                canvas.drawCircle(cx, cy, s * 0.62f, p)
                canvas.drawCircle(cx, y(-0.22f), s * 0.08f, fill)
                canvas.drawLine(cx, y(-0.02f), cx, y(0.36f), p)
            }
            Kind.HELP -> {
                canvas.drawCircle(cx, cy, s * 0.62f, p)
                val tp = Paint(p).apply {
                    style = Paint.Style.FILL
                    textSize = s * 0.9f
                    typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("?", cx, cy + s * 0.32f, tp)
            }
            Kind.SPEED -> {
                canvas.drawArc(RectF(x(-0.6f), y(-0.45f), x(0.6f), y(0.75f)), 200f, 140f, false, p)
                canvas.drawLine(cx, y(0.15f), x(0.35f), y(-0.3f), p)
            }
            Kind.TIMER -> {
                canvas.drawCircle(cx, y(0.08f), s * 0.52f, p)
                canvas.drawLine(cx, y(0.08f), cx, y(-0.22f), p)
                canvas.drawLine(cx, y(0.08f), x(0.22f), y(0.08f), p)
                canvas.drawLine(x(-0.18f), y(-0.62f), x(0.18f), y(-0.62f), p)
            }
            Kind.FX -> {
                for (i in 0 until 3) {
                    val py = y(-0.45f + i * 0.45f)
                    canvas.drawLine(x(-0.62f), py, x(0.62f), py, p)
                    canvas.drawCircle(x(-0.2f + i * 0.25f), py, s * 0.12f, fill)
                }
            }
            Kind.SPARKLE -> {
                val path = Path().apply {
                    moveTo(cx, y(-0.7f))
                    lineTo(x(0.16f), y(-0.16f)); lineTo(x(0.7f), cy); lineTo(x(0.16f), y(0.16f))
                    lineTo(cx, y(0.7f)); lineTo(x(-0.16f), y(0.16f)); lineTo(x(-0.7f), cy); lineTo(x(-0.16f), y(-0.16f))
                    close()
                }
                canvas.drawPath(path, fill)
            }
            Kind.PERSON -> {
                canvas.drawCircle(cx, y(-0.32f), s * 0.3f, if (filled) fill else p)
                canvas.drawArc(RectF(x(-0.55f), y(0.02f), x(0.55f), y(0.9f)), 180f, 180f, false, p)
            }
            Kind.LIST, Kind.HOME -> {
                for (i in 0 until 3) {
                    val py = y(-0.45f + i * 0.45f)
                    canvas.drawCircle(x(-0.55f), py, s * 0.09f, fill)
                    canvas.drawLine(x(-0.3f), py, x(0.6f), py, p)
                }
            }
        }
    }

}

/** A View that renders one [Icons.Kind]. */
class IconView(
    context: Context,
    var kind: Icons.Kind,
    var tint: Int,
    private val sizeDp: Float = 22f,
    private val strokeFactor: Float = 0.13f
) : View(context) {

    init {
        layoutParams = ViewGroup.LayoutParams(Ui2.dp(context, sizeDp), Ui2.dp(context, sizeDp))
    }

    var filled = false
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        Icons.draw(
            canvas, kind, width / 2f, height / 2f, size,
            tint, stroke = size * strokeFactor, filled = filled
        )
    }

    fun setTintColor(color: Int) {
        tint = color
        invalidate()
    }
}

/** Local dp helper so widgets do not depend on the Ui object's context plumbing. */
object Ui2 {
    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
