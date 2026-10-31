package xyz.beyoneer.gossipaudio.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import xyz.beyoneer.gossipaudio.speech.Stt
import xyz.beyoneer.gossipaudio.ui.theme.Palette
import xyz.beyoneer.gossipaudio.ui.theme.Ui
import xyz.beyoneer.gossipaudio.ui.widgets.BrandButton
import xyz.beyoneer.gossipaudio.ui.widgets.Icons
import kotlin.math.sin

/** Voice dictation sheet: pulsing mic, live partial text, never blocks the app. */
object SttSheet {

    fun show(activity: MainActivity, languageTag: String, onText: (String) -> Unit) {
        val palette = activity.palette
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, Ui.dp(activity, 4f), 0, Ui.dp(activity, 4f))
        }
        val orb = MicOrb(activity, palette)
        content.addView(orb, LinearLayout.LayoutParams(Ui.dp(activity, 128f), Ui.dp(activity, 128f)))
        val status = Ui.text(activity, "Listening\u2026", 17f, palette.text, bold = true, gravity = Gravity.CENTER)
        content.addView(status, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 10f) })
        val partial = Ui.text(activity, "Speak naturally \u2014 Hindi, English, whatever.", 13f, palette.textDim, gravity = Gravity.CENTER)
        partial.setLineSpacing(Ui.dpf(activity, 3f), 1f)
        partial.setPadding(0, Ui.dp(activity, 8f), 0, Ui.dp(activity, 8f))
        content.addView(partial, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        val row = Ui.row(activity)
        row.gravity = Gravity.CENTER
        val use = BrandButton(activity, palette, "Use this", BrandButton.Variant.PRIMARY, Icons.Kind.CHECK, compact = true)
        use.isEnabled = false
        use.alpha = 0.5f
        val cancel = BrandButton(activity, palette, "Cancel", BrandButton.Variant.GHOST, compact = true)
        row.addView(cancel)
        row.addView(Ui.space(activity, Ui.dp(activity, 10f)))
        row.addView(use)
        content.addView(row, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = Ui.dp(activity, 12f) })

        val handle = Overlays.sheet(activity, "Speak the gossip", content)
        var latest = ""
        val stt = Stt(activity)
        orb.startListening()
        stt.start(languageTag, activity.prefs.preferOfflineStt, object : Stt.Listener {
            override fun onListening() {
                status.text = "Listening\u2026"
            }

            override fun onPartial(text: String) {
                if (text.isNotBlank()) {
                    latest = text
                    partial.text = text
                    partial.setTextColor(palette.text)
                    use.isEnabled = true
                    use.alpha = 1f
                }
            }

            override fun onResult(text: String) {
                latest = text
                partial.text = text
                partial.setTextColor(palette.text)
                use.isEnabled = true
                use.alpha = 1f
                status.text = "Got it \u2705"
            }

            override fun onError(message: String) {
                status.text = "Mic trouble"
                partial.text = message
            }

            override fun onEnd() {
                orb.stopListening()
                stt.destroy()
            }
        })
        use.setOnClick {
            if (latest.isNotBlank()) onText(latest)
            stt.destroy()
            handle.dismiss()
        }
        cancel.setOnClick {
            stt.destroy()
            handle.dismiss()
        }
    }

    private class MicOrb(context: Context, palette: Palette) : View(context) {

        private val paletteRef = palette
        private val core = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = paletteRef.yellow
            strokeWidth = (2f * resources.displayMetrics.density)
        }
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        private var animator: ValueAnimator? = null
        private var t = 0f
        private var listening = false

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            core.shader = RadialGradient(
                w * 0.4f, h * 0.35f, w * 0.6f,
                intArrayOf(Color.WHITE, paletteRef.yellowBright, paletteRef.yellow, paletteRef.yellowDeep),
                floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP
            )
            glow.shader = RadialGradient(
                w / 2f, h / 2f, w * 0.52f,
                intArrayOf(Color.argb(90, Color.red(paletteRef.yellow), Color.green(paletteRef.yellow), Color.blue(paletteRef.yellow)), Color.TRANSPARENT),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val base = width * 0.30f
            canvas.drawCircle(cx, cy, width * 0.5f, glow)
            if (listening) {
                for (i in 0 until 3) {
                    val phase = ((t + i / 3f) % 1f)
                    val radius = base + phase * width * 0.18f
                    ring.alpha = ((1f - phase) * 180).toInt().coerceIn(0, 255)
                    canvas.drawCircle(cx, cy, radius, ring)
                }
            }
            canvas.drawCircle(cx, cy, base, core)
            val iconSize = width * 0.22f
            canvas.save()
            canvas.translate(cx - iconSize / 2, cy - iconSize / 2)
            Icons.draw(canvas, Icons.Kind.MIC, iconSize / 2, iconSize / 2, iconSize, paletteRef.onYellow, iconSize * 0.12f)
            canvas.restore()
            if (listening) {
                val wave = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paletteRef.yellow }
                for (i in 0 until 7) {
                    val h = (8 + 14 * kotlin.math.abs(sin((t * 6.28 + i * 0.9).toDouble()))).toFloat()
                    val x = cx - width * 0.2f + i * width * 0.066f
                    canvas.drawRoundRect(x, cy + base + 8f, x + 3f, cy + base + 8f + h, 2f, 2f, wave)
                }
            }
        }

        fun startListening() {
            listening = true
            animator?.cancel()
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1600
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    t = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        fun stopListening() {
            listening = false
            animator?.cancel()
            animator = null
            invalidate()
        }

        override fun onDetachedFromWindow() {
            stopListening()
            super.onDetachedFromWindow()
        }

        @Suppress("unused")
        private fun unusedPalette() = paletteRef
    }
}
