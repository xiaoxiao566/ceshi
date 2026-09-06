package com.ceshi.printtool.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlin.math.min

// PDF，直接用系统的 PdfRenderer 一页页画
class PdfDocumentSource private constructor(
    private val renderer: PdfRenderer
) : DocumentSource {

    override val pageCount: Int = renderer.pageCount

    override fun renderPage(index: Int, widthPx: Int, heightPx: Int): Bitmap {
        val page = renderer.openPage(index)
        return try {
            val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, fitMatrix(page.width, page.height, widthPx, heightPx), PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        } finally {
            page.close()
        }
    }

    override fun close() {
        renderer.close()
    }

    companion object {
        fun open(context: Context, uri: Uri): PdfDocumentSource? {
            return try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
                PdfDocumentSource(PdfRenderer(pfd))
            } catch (e: Exception) {
                null
            }
        }

        private fun fitMatrix(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Matrix {
            val scale = min(dstW.toFloat() / srcW, dstH.toFloat() / srcH)
            val dx = (dstW - srcW * scale) / 2f
            val dy = (dstH - srcH * scale) / 2f
            val m = Matrix()
            m.setScale(scale, scale)
            m.postTranslate(dx, dy)
            return m
        }
    }
}