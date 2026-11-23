package com.bddhy.pindou.logic

import android.graphics.Bitmap
import com.bddhy.pindou.data.BeadColor
import kotlin.math.roundToInt

/** 统计界面里的一组色号（按 A、B、C… 前缀分组）。 */
data class BeadGroup(
    val prefix: String,
    val items: List<Item>,
    val total: Int,
) {
    data class Item(val color: BeadColor, val count: Int)
}

/**
 * 一次生成的结果。
 *
 * [codes] / [argbs] 按行优先存放，长度都是 cols * rows。
 */
class BeadGrid(
    val cols: Int,
    val rows: Int,
    val codes: Array<String>,
    val argbs: IntArray,
    val colors: Map<String, BeadColor>,
) {
    val total: Int get() = cols * rows

    /** 按前缀分组统计，和 Python 版 /count 接口的输出顺序一致（A..M，其余归到「其他」）。 */
    fun groups(): List<BeadGroup> {
        val counts = LinkedHashMap<String, Int>()
        for (code in codes) {
            counts[code] = (counts[code] ?: 0) + 1
        }
        val byPrefix = counts.entries.groupBy { it.key.take(1).uppercase() }
        val ordered = ('A'..'M').map { it.toString() }
        val result = ArrayList<BeadGroup>()

        for (prefix in ordered) {
            val entries = byPrefix[prefix] ?: continue
            val items = entries.sortedBy { it.key }.mapNotNull { entry ->
                colors[entry.key]?.let { BeadGroup.Item(it, entry.value) }
            }
            if (items.isNotEmpty()) {
                result += BeadGroup(prefix, items, items.sumOf { it.count })
            }
        }

        val others = byPrefix.filterKeys { it !in ordered }.values.flatten()
        if (others.isNotEmpty()) {
            val items = others.sortedBy { it.key }.mapNotNull { entry ->
                colors[entry.key]?.let { BeadGroup.Item(it, entry.value) }
            }
            result += BeadGroup("其他", items, items.sumOf { it.count })
        }
        return result
    }
}

object BeadEngine {

    /**
     * 把图片压成 cols × rows 的拼豆网格。
     *
     * 每格的边界按比例分配：第 col 列覆盖 `[col×宽/列数, (col+1)×宽/列数)`，
     * 最后一行/列会吸收余数，保证**整张图都被覆盖**（格子宽度最多相差 1 像素）。
     * 早期版本用「块边长 = 宽 / 列数」整数除，会丢掉 `宽 mod 列数` 个像素，
     * 窄图在高列数下会直接少掉半张，已修正。
     *
     * 每块取平均色，先按 gamma 提亮/压暗，再用加权 RGB 距离找最近的 MARD 色号。
     */
    fun generate(
        bitmap: Bitmap,
        cols: Int,
        gamma: Float,
        palette: List<BeadColor>,
    ): BeadGrid {
        require(palette.isNotEmpty()) { "色卡为空" }
        val width = bitmap.width
        val height = bitmap.height
        require(width > 0 && height > 0) { "图片尺寸异常" }

        val safeCols = cols.coerceIn(1, width)
        val rows = GridMath.rowsFor(width, height, safeCols)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val count = safeCols * rows
        val codes = Array(count) { "" }
        val argbs = IntArray(count)
        val colorMap = HashMap<String, BeadColor>(256)
        val safeGamma = if (gamma.isFinite() && gamma > 0f) gamma else 1f

        var index = 0
        for (row in 0 until rows) {
            // 按比例分配边界，余数落到最后一格，保证不缺边
            val top = (row.toLong() * height / rows).toInt()
            val yEnd = ((row + 1).toLong() * height / rows).toInt()
            for (col in 0 until safeCols) {
                val left = (col.toLong() * width / safeCols).toInt()
                val xEnd = ((col + 1).toLong() * width / safeCols).toInt()

                var sumR = 0L
                var sumG = 0L
                var sumB = 0L
                var samples = 0
                for (y in top until yEnd) {
                    val base = y * width
                    for (x in left until xEnd) {
                        val pixel = pixels[base + x]
                        val alpha = (pixel ushr 24) and 0xFF
                        // 半透明像素先铺到白底上，避免边缘发黑
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        if (alpha == 255) {
                            sumR += r
                            sumG += g
                            sumB += b
                        } else {
                            sumR += (r * alpha + 255 * (255 - alpha)) / 255
                            sumG += (g * alpha + 255 * (255 - alpha)) / 255
                            sumB += (b * alpha + 255 * (255 - alpha)) / 255
                        }
                        samples++
                    }
                }

                val divisor = samples.coerceAtLeast(1)
                val avgR = (sumR.toFloat() / divisor).roundToInt()
                val avgG = (sumG.toFloat() / divisor).roundToInt()
                val avgB = (sumB.toFloat() / divisor).roundToInt()

                val nearest = nearestColor(
                    r = (avgR * safeGamma).coerceIn(0f, 255f),
                    g = (avgG * safeGamma).coerceIn(0f, 255f),
                    b = (avgB * safeGamma).coerceIn(0f, 255f),
                    palette = palette,
                )

                codes[index] = nearest.code
                argbs[index] = nearest.argb
                colorMap[nearest.code] = nearest
                index++
            }
        }

        return BeadGrid(cols = safeCols, rows = rows, codes = codes, argbs = argbs, colors = colorMap)
    }

    /** 加权 RGB 距离（0.299 / 0.587 / 0.114），与原版 find_closest_color 相同。 */
    private fun nearestColor(r: Float, g: Float, b: Float, palette: List<BeadColor>): BeadColor {
        var best = palette[0]
        var bestDistance = Float.MAX_VALUE
        for (color in palette) {
            val dr = (r - color.r) * 0.299f
            val dg = (g - color.g) * 0.587f
            val db = (b - color.b) * 0.114f
            val distance = dr * dr + dg * dg + db * db
            if (distance < bestDistance) {
                bestDistance = distance
                best = color
            }
        }
        return best
    }
}
