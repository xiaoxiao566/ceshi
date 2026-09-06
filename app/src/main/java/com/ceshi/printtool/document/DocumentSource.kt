package com.ceshi.printtool.document

import android.graphics.Bitmap
import android.net.Uri

/** 支持的文档类型 */
enum class DocType { PDF, IMAGE, TEXT, DOCX, DOC, UNSUPPORTED }

/** 描述一个待打印文件 */
data class PrintFile(
    val uri: Uri,
    val name: String,
    val mime: String?,
    val type: DocType
)

/**
 * 统一的文档渲染接口。
 * 无线打印与 OTG 打印共用此接口：把「某一页」渲染成位图，
 * 无线打印再由 PrintDocumentAdapter 画到系统打印画布，OTG 打印则编码成 PCL/ZJS 发送。
 */
interface DocumentSource {
    val pageCount: Int

    /** 把第 index 页渲染为目标尺寸位图（0 起） */
    fun renderPage(index: Int, widthPx: Int, heightPx: Int): Bitmap

    fun close()
}