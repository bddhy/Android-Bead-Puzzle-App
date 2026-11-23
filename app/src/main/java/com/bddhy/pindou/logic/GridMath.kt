package com.bddhy.pindou.logic

import kotlin.math.roundToInt

/** 网格尺寸相关的换算：列数 ↔ 总块数，两个输入框靠它联动。 */
object GridMath {

    /**
     * 行数：保持格子接近正方形，`行数 = round(列数 × 高 / 宽)`。
     *
     * 注意这里不能用「块边长 = 宽 / 列数（整数除）」再去除高：
     * 那样会丢掉 `宽 mod 列数` 个像素，窄图在高列数下会直接少掉半张。
     */
    fun rowsFor(width: Int, height: Int, cols: Int): Int {
        if (width <= 0 || height <= 0) return 1
        val safe = cols.coerceIn(1, width)
        return (safe.toDouble() * height / width).roundToInt().coerceAtLeast(1)
    }

    fun totalFor(width: Int, height: Int, cols: Int): Int {
        if (width <= 0 || height <= 0) return cols
        return cols.coerceIn(1, width) * rowsFor(width, height, cols)
    }

    /** 由总块数反推最接近的列数（在 [minCols]..[maxCols] 之间找误差最小的一档）。 */
    fun colsForTotal(
        width: Int,
        height: Int,
        total: Int,
        minCols: Int,
        maxCols: Int,
    ): Int {
        if (width <= 0 || height <= 0) return minCols.coerceAtLeast(1)
        val lo = minCols.coerceAtLeast(1)
        val hi = maxCols.coerceAtMost(width).coerceAtLeast(lo)
        var best = lo
        var bestDiff = Long.MAX_VALUE
        for (c in lo..hi) {
            val diff = kotlin.math.abs(totalFor(width, height, c).toLong() - total.toLong())
            if (diff < bestDiff) {
                bestDiff = diff
                best = c
            }
        }
        return best
    }
}
