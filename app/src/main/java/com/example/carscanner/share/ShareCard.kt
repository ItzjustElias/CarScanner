package com.example.carscanner.share

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.example.carscanner.R
import com.example.carscanner.data.CarEntity
import com.example.carscanner.domain.Rarity
import com.example.carscanner.ui.components.formatCompactUsd
import com.example.carscanner.ui.theme.color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip

object ShareCard {
    private const val W = 1080
    private const val H = 1350

    private val RAINBOW = intArrayOf(
        0xFFFF5A5A.toInt(), 0xFFFFB347.toInt(), 0xFFFFE45E.toInt(), 0xFF5CE08A.toInt(),
        0xFF4FD1FF.toInt(), 0xFF7C8CFF.toInt(), 0xFFC77DFF.toInt(), 0xFFFF5A5A.toInt(),
    )

    fun render(context: Context, car: CarEntity): Bitmap {
        val bitmap = createBitmap(W, H)
        val canvas = Canvas(bitmap)
        val rarity = car.rarityEnum
        val accent = rarity.color().toArgb()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Background
        paint.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), 0xFF1B2540.toInt(), 0xFF0E1118.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
        paint.shader = null

        // Photo with the rarity border
        val frame = RectF(60f, 60f, W - 60f, 870f)
        drawPhoto(canvas, car.imagePath, frame, 48f)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 16f
            if (rarity == Rarity.DIVINE) shader = rainbow(frame.left, frame.right) else color = accent
            if (rarity >= Rarity.LEGENDARY) setShadowLayer(40f, 0f, 0f, accent)
        }
        canvas.drawRoundRect(frame, 48f, 48f, border)

        // Rarity pill
        val pillText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 40f
            typeface = Typeface.DEFAULT_BOLD
            if (rarity == Rarity.DIVINE) shader = rainbow(60f, 400f) else color = accent
        }
        val pillWidth = pillText.measureText(rarity.label) + 64f
        val pill = RectF(60f, 910f, 60f + pillWidth, 978f)
        val pillFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            if (rarity == Rarity.DIVINE) shader = rainbow(60f, 60f + pillWidth) else color = accent
            alpha = 56
        }
        canvas.drawRoundRect(pill, 34f, 34f, pillFill)
        canvas.drawText(rarity.label, pill.left + 32f, baselineFor(pill, pillText), pillText)

        // Title and subtitle
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF2F4F8.toInt()
            typeface = Typeface.DEFAULT_BOLD
            textSize = fitTextSize(this, car.title, W - 120f, 76f, 38f)
        }
        canvas.drawText(car.title, 60f, 1058f, title)
        val sub = listOf(car.yearText, car.bodyStyle).filter { it.isNotBlank() }.joinToString(", ")
        if (sub.isNotEmpty()) {
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF9AA3B5.toInt()
                textSize = fitTextSize(this, sub, W - 120f, 38f, 24f)
            }
            canvas.drawText(sub, 60f, 1110f, subPaint)
        }

        // Points and value
        val points = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFC857.toInt()
            typeface = Typeface.DEFAULT_BOLD
            textSize = 84f
        }
        canvas.drawText("+${car.points} pts", 60f, 1218f, points)
        car.valueUsd?.let {
            val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFB8C0D0.toInt()
                textSize = 46f
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("${formatCompactUsd(it)} value", W - 60f, 1218f, value)
        }

        // Footer
        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF6F7890.toInt()
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Spotted with ${context.getString(R.string.app_name)}", W / 2f, 1304f, footer)
        return bitmap
    }

    /** Renders the card to a PNG in the cache folder and returns a ready-to-send share intent. */
    suspend fun createShareIntent(context: Context, car: CarEntity): Intent = withContext(Dispatchers.Default) {
        val dir = File(context.cacheDir, "shared").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() } // only the latest card is kept
        }
        val file = File(dir, "car_${car.id}.png")
        val bitmap = render(context, car)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Spotted a ${car.rarityEnum.label} ${car.title}!")
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun share(context: Context, car: CarEntity) {
        try {
            val chooser = Intent.createChooser(createShareIntent(context, car), "Share your find")
            if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't create the share card.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun drawPhoto(canvas: Canvas, path: String, frame: RectF, radius: Float) {
        val photo = BitmapFactory.decodeFile(path)
        canvas.withClip(Path().apply { addRoundRect(frame, radius, radius, Path.Direction.CW) }) {
            if (photo == null) {
                drawRect(frame, Paint().apply { color = 0xFF212633.toInt() })
            } else {
                // "Center crop": scale to cover the frame, trim the overflow equally on both sides.
                val scale = maxOf(frame.width() / photo.width, frame.height() / photo.height)
                val srcW = frame.width() / scale
                val srcH = frame.height() / scale
                val left = (photo.width - srcW) / 2f
                val top = (photo.height - srcH) / 2f
                val src =
                    Rect(left.toInt(), top.toInt(), (left + srcW).toInt(), (top + srcH).toInt())
                drawBitmap(
                    photo,
                    src,
                    frame,
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                )
                photo.recycle()
            }
        }
    }

    private fun rainbow(startX: Float, endX: Float): Shader =
        LinearGradient(startX, 0f, endX, 0f, RAINBOW, null, Shader.TileMode.MIRROR)

    private fun baselineFor(box: RectF, paint: Paint): Float {
        val fm = paint.fontMetrics
        return box.centerY() - (fm.ascent + fm.descent) / 2f
    }

    private fun fitTextSize(paint: Paint, text: String, maxWidth: Float, start: Float, min: Float): Float {
        var size = start
        paint.textSize = size
        while (paint.measureText(text) > maxWidth && size > min) {
            size -= 2f
            paint.textSize = size
        }
        return size
    }
}
