package com.bddhy.pindou.logic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log

/** 从相册 Uri 载入图片，长边压到 [MAX_SIDE] 以内，省内存也够拼豆用。 */
object ImageLoader {

    private const val TAG = "PixelImage"
    private const val MAX_SIDE = 2048

    fun load(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                decodeModern(context, uri)?.let { return it }
            } catch (t: Throwable) {
                Log.w(TAG, "ImageDecoder 解码失败: $uri", t)
            }
        }
        return try {
            decodeLegacy(context, uri)
        } catch (t: Throwable) {
            Log.w(TAG, "BitmapFactory 解码失败: $uri", t)
            null
        }
    }

    private fun sampleSize(longest: Int): Int {
        var sample = 1
        while (longest / sample > MAX_SIDE) sample *= 2
        return sample
    }

    private fun decodeModern(context: Context, uri: Uri): Bitmap? {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.setTargetSampleSize(sampleSize(maxOf(info.size.width, info.size.height)))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
    }

    private fun decodeLegacy(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(if (longest > 0) longest else 1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }
}
