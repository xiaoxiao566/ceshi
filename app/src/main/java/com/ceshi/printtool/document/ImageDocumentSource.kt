package com.ceshi.printtool.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.net.Uri
import kotlin.math.min

// 图片，JPG/PNG/GIF/WebP/BMP 都算，一页搞定
class ImageDocumentSource private constructor(
    private val bitmap: Bitmap
) : DocumentSource {

    override val pageCount: Int = 1

    override fun renderPage(index: Int, widthPx: Int, heightPx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        val canvas = Canvas(bmp)
        val scale = min(widthPx.toFloat() / bitmap.width, heightPx.toFloat() / bitmap.height)
        val dw = bitmap.width * scale
        val dh = bitmap.height * scale
        val dx = (widthPx - dw) / 2f
        val dy = (heightPx - dh) / 2f
        canvas.drawBitmap(bitmap, null, RectF(dx, dy, dx + dw, dy + dh), null)
        return bmp
    }

    override fun close() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }

    companion object {
        fun open(context: Context, uri: Uri): ImageDocumentSource? {
            return try {
                context.contentResolver.openInputStream(uri)?.use { ins ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
                    val bmp = BitmapFactory.decodeStream(ins, null, opts)
                    bmp?.let { ImageDocumentSource(it) }
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}