package com.ireader.engines.epub.internal.content

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

class EpubContentProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? {
        val resolved = resolveFile(uri) ?: return null
        val ext = resolved.extension.lowercase()
        if (ext.isBlank()) return "application/octet-stream"

        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: when (ext) {
                "xhtml", "html", "htm" -> "application/xhtml+xml"
                "css" -> "text/css"
                "svg" -> "image/svg+xml"
                "ttf" -> "font/ttf"
                "otf" -> "font/otf"
                "woff" -> "font/woff"
                "woff2" -> "font/woff2"
                else -> "application/octet-stream"
            }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = resolveFile(uri) ?: throw FileNotFoundException("No such file: $uri")
        if (!file.exists() || !file.isFile) throw FileNotFoundException("No such file: $uri")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun resolveFile(uri: Uri): File? {
        val parsed = EpubUri.parse(uri) ?: return null
        val root = EpubStore.extractedRootOf(parsed.docId) ?: return null
        val target = File(root, parsed.relativePath)

        val rootCanonical = root.canonicalFile
        val targetCanonical = target.canonicalFile
        if (!targetCanonical.path.startsWith(rootCanonical.path)) {
            return null
        }
        return targetCanonical
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
