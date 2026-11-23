package com.bddhy.pindou.data

import android.content.Context
import org.json.JSONObject

/**
 * 一颗拼豆色卡颜色。
 * [code] 已经过归一化处理：像 "A1" 这种两位色号会补零变成 "A01"，与原 Python 版本保持一致。
 */
data class BeadColor(
    val code: String,
    val r: Int,
    val g: Int,
    val b: Int,
    val group: String,
) {
    /** 打包成 0xAARRGGBB，可直接用于 Compose / Canvas。 */
    val argb: Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** 感知亮度，用来决定色号文字用黑还是白。 */
    val luminance: Float = 0.299f * r + 0.587f * g + 0.114f * b
}

/** 读取 assets/mard_colors.json（MARD 221 色卡）。 */
object MardPalette {

    @Volatile
    private var cached: List<BeadColor>? = null

    fun load(context: Context): List<BeadColor> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val list = read(context.applicationContext)
            cached = list
            return list
        }
    }

    private fun read(context: Context): List<BeadColor> {
        val raw = context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val array = JSONObject(raw).getJSONArray("colors")
        val list = ArrayList<BeadColor>(array.length())
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val rawCode = item.getString("code")
            val code = if (rawCode.length == 2) "${rawCode[0]}0${rawCode[1]}" else rawCode
            val rgb = item.getJSONArray("rgb")
            list += BeadColor(
                code = code,
                r = rgb.getInt(0).coerceIn(0, 255),
                g = rgb.getInt(1).coerceIn(0, 255),
                b = rgb.getInt(2).coerceIn(0, 255),
                group = item.optString("group").ifEmpty { code.take(1) },
            )
        }
        return list
    }

    private const val ASSET_NAME = "mard_colors.json"
}
