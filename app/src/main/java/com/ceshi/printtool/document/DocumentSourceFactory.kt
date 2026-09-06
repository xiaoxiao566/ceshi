package com.ceshi.printtool.document

import android.content.Context
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * 工厂与文本提取工具。
 * PDF / 图片 走专门实现；TXT / DOCX 统一抽取为纯文本后交给文本分页渲染。
 * 旧版二进制 .doc（OLE 格式）目前不可靠，返回 null 由上层提示。
 */
object DocumentSourceFactory {

    fun open(context: Context, file: PrintFile): DocumentSource? {
        return when (file.type) {
            DocType.PDF -> PdfDocumentSource.open(context, file.uri)
            DocType.IMAGE -> ImageDocumentSource.open(context, file.uri)
            DocType.TEXT, DocType.DOCX -> {
                val text = TextExtractor.extract(context, file) ?: return null
                TextDocumentSource.create(text)
            }
            DocType.DOC -> null
            DocType.UNSUPPORTED -> null
        }
    }
}

object TextExtractor {

    fun extract(context: Context, file: PrintFile): String? {
        return try {
            val input = context.contentResolver.openInputStream(file.uri) ?: return null
            input.use { stream ->
                when (file.type) {
                    DocType.TEXT -> stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    DocType.DOCX -> extractDocxText(stream)
                    else -> null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractDocxText(stream: InputStream): String? {
        return try {
            ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                var xml: String? = null
                while (entry != null) {
                    if (entry.name.equals("word/document.xml", ignoreCase = true)) {
                        xml = zip.readBytes().toString(Charsets.UTF_8)
                        break
                    }
                    entry = zip.nextEntry
                }
                xml?.let { parseDocxXml(it) }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 极简 DOCX XML 转纯文本：段落换行 + 抓 w:t 文本 + 反转义，并做简单中文断句 */
    private fun parseDocxXml(xml: String): String {
        // 段落结束/单元格结束处补换行
        val withBreaks = xml
            .replace(Regex("<w:p[ >]"), "\n")
            .replace(Regex("</w:tab>"), "    ")
            .replace(Regex("</w:br>"), "\n")
        // 去除所有标签
        val text = withBreaks.replace(Regex("<[^>]+>"), "")
        // 反转义
        val unescaped = text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#160;", " ")
        // 压缩多余空行，但保留段落结构
        return unescaped.replace(Regex("\n{3,}"), "\n\n").trim()
    }
}