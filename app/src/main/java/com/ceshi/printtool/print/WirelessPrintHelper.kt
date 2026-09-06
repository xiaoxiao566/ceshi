package com.ceshi.printtool.print

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.ceshi.printtool.document.DocumentSource
import java.io.FileOutputStream

// 无线打印：直接甩给系统打印框架。系统会弹打印框，Mopria / IPP / 各品牌插件都能接，
// 支持 IPP 的网络打印机基本都能打（含 HP 的网络机型）。
class WirelessPrintHelper(private val context: Context) {

    fun print(source: DocumentSource, jobName: String, options: PrintOptions = PrintOptions()) {
        val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val media = when (options.paperSize) {
            "Letter" -> PrintAttributes.MediaSize.NA_LETTER
            "Legal" -> PrintAttributes.MediaSize.NA_LEGAL
            else -> PrintAttributes.MediaSize.ISO_A4
        }
        val attrs = PrintAttributes.Builder()
            .setMediaSize(media)
            .setColorMode(if (options.color) PrintAttributes.COLOR_MODE_COLOR else PrintAttributes.COLOR_MODE_MONOCHROME)
            .setDuplexMode(if (options.duplex) PrintAttributes.DUPLEX_MODE_LONG_EDGE else PrintAttributes.DUPLEX_MODE_NONE)
            .build()
        pm.print(jobName, SourcePrintAdapter(source, jobName), attrs)
    }
}

private class SourcePrintAdapter(
    private val source: DocumentSource,
    private val jobName: String
) : PrintDocumentAdapter() {

    private val renderDpi = 200f
    private var pageWidthMils = 8500
    private var pageHeightMils = 11000

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: android.os.Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val size = newAttributes?.mediaSize ?: oldAttributes?.mediaSize ?: PrintAttributes.MediaSize.ISO_A4
        pageWidthMils = size.widthMils
        pageHeightMils = size.heightMils
        val info = PrintDocumentInfo.Builder(jobName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(source.pageCount.coerceAtLeast(1))
            .build()
        callback.onLayoutFinished(info, newAttributes != oldAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback
    ) {
        val pdf = PdfDocument()
        try {
            val written = mutableListOf<PageRange>()
            for (range in pages) {
                for (pageNum in range.start..range.end) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onWriteCancelled()
                        return
                    }
                    val info = PdfDocument.PageInfo.Builder(
                        pageWidthMils / 1000 * 72,
                        pageHeightMils / 1000 * 72,
                        pageNum
                    ).create()
                    val page = pdf.startPage(info)
                    drawPage(page.canvas, pageNum)
                    pdf.finishPage(page)
                    written.add(PageRange(pageNum, pageNum))
                }
            }
            FileOutputStream(destination.fileDescriptor).use { out ->
                pdf.writeTo(out)
                out.flush()
            }
            callback.onWriteFinished(written.toTypedArray())
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        } finally {
            pdf.close()
        }
    }

    private fun drawPage(canvas: Canvas, pageIndex: Int) {
        val ptsW = pageWidthMils / 1000f * 72f
        val ptsH = pageHeightMils / 1000f * 72f
        val pxW = (ptsW / 72f * renderDpi).toInt().coerceAtLeast(1)
        val pxH = (ptsH / 72f * renderDpi).toInt().coerceAtLeast(1)
        val bmp = source.renderPage(pageIndex, pxW, pxH)
        canvas.drawBitmap(bmp, null, Rect(0, 0, canvas.width, canvas.height), null)
        bmp.recycle()
    }

    override fun onFinish() {
        source.close()
    }
}