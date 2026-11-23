package com.bddhy.pindou.logic

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/** 把生成的网格导出成一张 PNG，写进系统相册。 */
object Exporter {

    private const val CELL = 40
    private const val PADDING = 28

    @Suppress("DEPRECATION")
    fun save(context: Context, grid: BeadGrid, withCodes: Boolean, withGuides: Boolean = false): Uri? {
        val bitmap = render(grid, withCodes, withGuides)
        return persist(context, bitmap).also { if (it == null) bitmap.recycle() }
    }

    fun render(grid: BeadGrid, withCodes: Boolean, withGuides: Boolean = false): Bitmap {
        val width = grid.cols * CELL + PADDING * 2
        val height = grid.rows * CELL + PADDING * 2
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        for (row in 0 until grid.rows) {
            for (col in 0 until grid.cols) {
                fill.color = grid.argbs[row * grid.cols + col]
                val left = (PADDING + col * CELL).toFloat()
                val top = (PADDING + row * CELL).toFloat()
                canvas.drawRect(left, top, left + CELL, top + CELL, fill)
            }
        }

        // 淡淡的网格线，方便对着拼豆板数格子
        val line = Paint().apply {
            color = 0x33000000
            strokeWidth = 1f
        }
        val right = (PADDING + grid.cols * CELL).toFloat()
        val bottom = (PADDING + grid.rows * CELL).toFloat()
        for (col in 0..grid.cols) {
            val x = (PADDING + col * CELL).toFloat()
            canvas.drawLine(x, PADDING.toFloat(), x, bottom, line)
        }
        for (row in 0..grid.rows) {
            val y = (PADDING + row * CELL).toFloat()
            canvas.drawLine(PADDING.toFloat(), y, right, y, line)
        }

        // 每 10 格一条红色虚线，方便对照 10×10 拼豆板
        if (withGuides) {
            val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFE53935.toInt()
                strokeWidth = 3f
                pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
            }
            for (col in 10 until grid.cols step 10) {
                val x = (PADDING + col * CELL).toFloat()
                canvas.drawLine(x, PADDING.toFloat(), x, bottom, guide)
            }
            for (row in 10 until grid.rows step 10) {
                val y = (PADDING + row * CELL).toFloat()
                canvas.drawLine(PADDING.toFloat(), y, right, y, guide)
            }
        }

        if (withCodes) {
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textSize = CELL * 0.42f
            }
            val metrics = text.fontMetrics
            val baselineOffset = (metrics.descent - metrics.ascent) / 2f - metrics.descent
            for (row in 0 until grid.rows) {
                for (col in 0 until grid.cols) {
                    val code = grid.codes[row * grid.cols + col]
                    val color = grid.colors[code] ?: continue
                    text.color = if (color.luminance > 128f) Color.BLACK else Color.WHITE
                    val cx = PADDING + col * CELL + CELL / 2f
                    val cy = PADDING + row * CELL + CELL / 2f
                    canvas.drawText(code, cx, cy + baselineOffset, text)
                }
            }
        }
        return bitmap
    }

    private fun persist(context: Context, bitmap: Bitmap): Uri? {
        val fileName = "pixelart_${System.currentTimeMillis()}.png"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PixelArt")
            }
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = context.contentResolver.insert(collection, values) ?: return null
            val ok = context.contentResolver.openOutputStream(uri)?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            } ?: false
            if (ok) uri else null
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "PixelArt",
            )
            if (!dir.exists() && !dir.mkdirs()) return null
            val file = File(dir, fileName)
            val ok = try {
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } catch (e: Exception) {
                false
            }
            if (ok) Uri.fromFile(file) else null
        }
    }
}
