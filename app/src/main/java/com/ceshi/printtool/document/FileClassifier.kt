package com.ceshi.printtool.document

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

object FileClassifier {

    private val imageExt = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
    private val textExt = setOf("txt", "md", "log", "csv", "xml", "json")

    fun classify(mime: String?, name: String): DocType {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            mime == "application/pdf" || ext == "pdf" -> DocType.PDF
            mime?.startsWith("image/") == true || ext in imageExt -> DocType.IMAGE
            mime == "text/plain" || ext in textExt -> DocType.TEXT
            mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || ext == "docx" -> DocType.DOCX
            mime == "application/msword" || ext == "doc" -> DocType.DOC
            else -> DocType.UNSUPPORTED
        }
    }

    fun queryName(cr: ContentResolver, uri: Uri): String {
        var name: String? = uri.lastPathSegment
        try {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = c.getString(idx)
                }
            }
        } catch (_: Exception) {
            // 忽略，使用 lastPathSegment
        }
        return name ?: "untitled"
    }

    fun fromUri(cr: ContentResolver, uri: Uri): PrintFile {
        val mime = cr.getType(uri)
        val name = queryName(cr, uri)
        return PrintFile(uri, name, mime, classify(mime, name))
    }
}