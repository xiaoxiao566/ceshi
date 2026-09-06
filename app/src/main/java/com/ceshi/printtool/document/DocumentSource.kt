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
 * 统一的文档渲染接口，无线打印和 OTG 打印共用。
 * 负责把「某一页」渲染成位图：无线路由到系统打印画布，OTG 则编码成 PCL/ZJS 发出去。
 */
interface DocumentSource {
    val pageCount: Int

    /** 渲染第 index 页为指定尺寸位图，页号从 0 开始 */
    fun renderPage(index: Int, widthPx: Int, heightPx: Int): Bitmap

    fun close()
}