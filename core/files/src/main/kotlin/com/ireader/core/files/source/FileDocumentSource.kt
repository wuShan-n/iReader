package com.ireader.core.files.source

import android.net.Uri
import android.os.ParcelFileDescriptor
import com.ireader.reader.api.open.DocumentSource
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileDocumentSource(
    private val file: File,
    override val displayName: String? = file.name,
    override val mimeType: String? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DocumentSource {
    override val uri: Uri = Uri.fromFile(file)
    override val sizeBytes: Long? = file.length()

    override suspend fun openInputStream(): InputStream = withContext(ioDispatcher) {
        FileInputStream(file)
    }

    override suspend fun openFileDescriptor(mode: String): ParcelFileDescriptor? = withContext(ioDispatcher) {
        val pfdMode = when (mode) {
            "rw" -> ParcelFileDescriptor.MODE_READ_WRITE
            else -> ParcelFileDescriptor.MODE_READ_ONLY
        }
        ParcelFileDescriptor.open(file, pfdMode)
    }
}
