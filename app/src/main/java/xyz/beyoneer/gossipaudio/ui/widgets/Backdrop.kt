package xyz.beyoneer.gossipaudio.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.ViewGroup
import xyz.beyoneer.gossipaudio.ui.theme.Palette

/** Soft yellow glows + dotted texture behind every screen. Very cheap to draw. */
class GlowBackdrop(context: Context, private val palette: Palette) : View(context) {

    private val topGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bottomGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dots = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wash = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setLayerType(LAYER_TYPE_HARDWARE, null)
        dots.color = if (palette.dark) 0x14FFFFFF else 0x0D000000
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        topGlow.shader = RadialGradient(
            w * 0.85f, -h * 0.05f, w * 0.95f,
            intArrayOf(withAlpha(palette.yellow, if (palette.dark) 0.20f else 0.24f), Color.TRANSPARENT),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        bottomGlow.shader = RadialGradient(
            w * 0.05f, h * 0.95f, w * 1.1f,
            intArrayOf(withAlpha(palette.yellow, if (palette.dark) 0.10f else 0.16f), Color.TRANSPARENT),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        wash.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(palette.bg, palette.bg, palette.bgAlt),
            floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), wash)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), topGlow)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bottomGlow)
        val step = context.resources.displayMetrics.density * 26f
        var y = step
        while (y < height) {
            var x = step
            while (x < width) {
                canvas.drawCircle(x, y, 1.1f, dots)
                x += step
            }
            y += step
        }
    }

    private fun withAlpha(color: Int, alpha: Float): Int = Color.argb(
        (alpha * 255).toInt().coerceIn(0, 255),
        Color.red(color), Color.green(color), Color.blue(color)
    )
}
