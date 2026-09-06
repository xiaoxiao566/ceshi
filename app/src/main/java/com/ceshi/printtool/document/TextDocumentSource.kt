package com.ceshi.printtool.document

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.roundToInt

// 文本分页。先按 A4@300dpi 的逻辑尺寸排一遍（换行+分页），页数就固定了；
// 画的时候再等比缩到目标尺寸，无线和 OTG 出来的是同一版排版。
class TextDocumentSource private constructor(
    private val pages: List<List<String>>
) : DocumentSource {

    override val pageCount: Int = pages.size

    override fun renderPage(index: Int, widthPx: Int, heightPx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        val canvas = Canvas(bmp)

        val scale = widthPx.toFloat() / CANONICAL_WIDTH
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = FONT_SIZE * scale
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }
        val lineHeight = LINE_HEIGHT * scale
        val margin = MARGIN * scale

        var y = margin + lineHeight
        for (line in pages[index]) {
            canvas.drawText(line, margin, y, paint)
            y += lineHeight
        }
        return bmp
    }

    override fun close() {
        // 无原生资源可释放
    }

    companion object {
        // A4 @300dpi ≈ 2480 x 3508 px
        const val CANONICAL_WIDTH = 2480f
        const val CANONICAL_HEIGHT = 3508f
        const val FONT_SIZE = 42f
        const val LINE_HEIGHT = 58f
        const val MARGIN = 120f

        fun create(text: String): TextDocumentSource {
            val paint = Paint().apply {
                textSize = FONT_SIZE
                typeface = Typeface.MONOSPACE
            }
            val usableWidth = CANONICAL_WIDTH - MARGIN * 2
            val allLines = wrapLines(text, paint, usableWidth)
            val pages = packPages(allLines)
            return TextDocumentSource(pages)
        }

        private fun wrapLines(text: String, paint: Paint, maxWidth: Float): List<String> {
            val lines = mutableListOf<String>()
            for (raw in text.split('\n')) {
                if (raw.isEmpty()) {
                    lines.add("")
                    continue
                }
                var line = StringBuilder()
                for (ch in raw) {
                    line.append(ch)
                    if (paint.measureText(line.toString()) > maxWidth) {
                        val s = line.toString()
                        val last = s.last()
                        lines.add(s.dropLast(1))
                        line = StringBuilder(last.toString())
                    }
                }
                lines.add(line.toString())
            }
            if (lines.isEmpty()) lines.add("")
            return lines
        }

        private fun packPages(allLines: List<String>): List<List<String>> {
            val usableHeight = CANONICAL_HEIGHT - MARGIN * 2
            val linesPerPage = (usableHeight / LINE_HEIGHT).roundToInt().coerceAtLeast(1)
            val pages = mutableListOf<List<String>>()
            var page = mutableListOf<String>()
            for (ln in allLines) {
                if (page.size >= linesPerPage) {
                    pages.add(page)
                    page = mutableListOf()
                }
                page.add(ln)
            }
            if (page.isNotEmpty()) pages.add(page)
            if (pages.isEmpty()) pages.add(listOf(""))
            return pages
        }
    }
}