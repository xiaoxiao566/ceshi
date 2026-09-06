package com.ceshi.printtool.document

import android.graphics.Bitmap
import android.net.Uri

enum class DocType { PDF, IMAGE, TEXT, DOCX, DOC, UNSUPPORTED }

// 准备打印的一个文件
data class PrintFile(
    val uri: Uri,
    val name: String,
    val mime: String?,
    val type: DocType
)

// 一个统一的页面来源，无线和 OTG 都靠它拿第 N 页的图
interface DocumentSource {
    val pageCount: Int

    fun renderPage(index: Int, widthPx: Int, heightPx: Int): Bitmap

    fun close()
}