package com.ceshi.printtool.document

import android.content.Context
import java.io.InputStream
import java.util.zip.ZipInputStream

// 按类型挑加载器；txt/docx 先抽成纯文本再走文本分页，老 .doc 不支持。
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

    // 极简陋的 DOCX 转纯文本：补换行、剥标签、反转义
    private fun parseDocxXml(xml: String): String {
        val withBreaks = xml
            .replace(Regex("<w:p[ >]"), "\n")
            .replace(Regex("</w:tab>"), "    ")
            .replace(Regex("</w:br>"), "\n")
        val text = withBreaks.replace(Regex("<[^>]+>"), "")
        val unescaped = text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#160;", " ")
        return unescaped.replace(Regex("\n{3,}"), "\n\n").trim()
    }
}