package com.ireader.core.files.source

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.ireader.reader.source.DocumentSource
import java.io.FileNotFoundException
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContentUriDocumentSource(
    context: Context,
    override val uri: Uri,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DocumentSource {
    private val resolver: ContentResolver = context.contentResolver

    override val displayName: String? by lazy { queryString(OpenableColumns.DISPLAY_NAME) }
    override val sizeBytes: Long? by lazy { queryLong(OpenableColumns.SIZE) }
    override val mimeType: String? by lazy { resolver.getType(uri) }

    override suspend fun openInputStream(): InputStream = withContext(ioDispatcher) {
        resolver.openInputStream(uri) ?: throw FileNotFoundException("Cannot open $uri")
    }

    override suspend fun openFileDescriptor(mode: String): ParcelFileDescriptor? = withContext(ioDispatcher) {
        resolver.openFileDescriptor(uri, mode)
    }

    private fun queryCursor(): Cursor? {
        return resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )
    }

    private fun queryString(column: String): String? {
        return queryCursor()?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(column)
            if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
        }
    }

    private fun queryLong(column: String): Long? {
        return queryCursor()?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(column)
            if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
        }
    }
}
