package xyz.beyoneer.gossipaudio.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import xyz.beyoneer.gossipaudio.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Cover-art helpers: user photos, brand cards and the circular player disc. */
object Artwork {

    private const val YELLOW = 0xFFF5C518.toInt()
    private const val DARK_YELLOW = 0xFF8A6A00.toInt()
    private const val INK = 0xFF0B0B0C.toInt()

    fun loadScaled(file: File, maxSize: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxSize && bounds.outHeight / (sample * 2) >= maxSize) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (_: Throwable) {
            null
        }
    }

    fun copyAsCover(context: Context, source: File, dest: File): Boolean {
        return try {
            val bitmap = loadScaled(source, 720) ?: return false
            val square = centerCropSquare(bitmap)
            FileOutputStream(dest).use { out ->
                square.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun centerCropSquare(src: Bitmap): Bitmap {
        val size = minOf(src.width, src.height)
        val x = (src.width - size) / 2
        val y = (src.height - size) / 2
        return Bitmap.createBitmap(src, x, y, size, size)
    }

    /** Branded cover when the user has not attached a picture. */
    fun generateBrandCover(
        context: Context,
        title: String,
        voiceName: String,
        styleLabel: String,
        outFile: File
    ): Boolean {
        return try {
            val size = 1024
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val bg = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, size.toFloat(), size.toFloat(),
                    intArrayOf(INK, 0xFF1A1608.toInt(), INK),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bg)
            val glow = Paint().apply {
                shader = RadialGradient(
                    size * 0.5f, size * 0.34f, size * 0.62f,
                    intArrayOf(0x88F5C518.toInt(), 0x22F5C518.toInt(), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), glow)

            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = size * 0.012f
                color = DARK_YELLOW
            }
            canvas.drawRoundRect(RectF(28f, 28f, size - 28f, size - 28f), 72f, 72f, border)

            val logo = BitmapFactory.decodeResource(context.resources, R.drawable.gossipaudio_glyph)
            if (logo != null) {
                val logoSize = size * 0.24f
                val left = size * 0.5f - logoSize / 2f
                val top = size * 0.13f
                canvas.drawBitmap(logo, null, RectF(left, top, left + logoSize, top + logoSize), Paint(Paint.FILTER_BITMAP_FLAG))
            }

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                textSize = size * 0.088f
                textAlign = Paint.Align.CENTER
            }
            val words = title.split(" ")
            var line = StringBuilder()
            var y = size * 0.56f
            val maxWidth = size * 0.82f
            for (word in words) {
                val attempt = if (line.isEmpty()) word else "$line $word"
                if (titlePaint.measureText(attempt) > maxWidth && line.isNotEmpty()) {
                    canvas.drawText(line.toString(), size / 2f, y, titlePaint)
                    y += titlePaint.textSize * 1.12f
                    line = StringBuilder(word)
                } else {
                    line = StringBuilder(attempt)
                }
            }
            if (line.isNotEmpty()) canvas.drawText(line.toString(), size / 2f, y, titlePaint)

            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = YELLOW
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                textSize = size * 0.044f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("$voiceName  \u2022  $styleLabel", size / 2f, size * 0.84f, subPaint)
            val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xCCFFFFFF.toInt()
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                textSize = size * 0.036f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("GOSSIPAUDIO \u00B7 BEYONEER XYZ", size / 2f, size * 0.91f, brand)
            val date = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x99FFFFFF.toInt()
                textSize = size * 0.026f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            canvas.drawText(
                SimpleDateFormat("dd MMM yyyy \u00B7 HH:mm", Locale.getDefault()).format(Date()),
                size / 2f, size * 0.955f, date
            )
            FileOutputStream(outFile).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun circular(src: Bitmap, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        val scaled = Bitmap.createScaledBitmap(src, size, size, true)
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        return out
    }
}
