package com.ireader.engines.txt.testing

import android.net.Uri
import android.os.ParcelFileDescriptor
import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.Utf16LeFileWriter
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

internal class InMemoryDocumentSource(
    override val uri: Uri,
    private val payload: ByteArray,
    override val displayName: String? = "test.txt",
    override val mimeType: String? = "text/plain"
) : DocumentSource {
    override val sizeBytes: Long? = payload.size.toLong()

    override suspend fun openInputStream(): InputStream {
        return ByteArrayInputStream(payload)
    }

    override suspend fun openFileDescriptor(mode: String): ParcelFileDescriptor? = null
}

internal fun createBookFiles(root: File): TxtBookFiles {
    val paginationDir = File(root, "pagination").apply { mkdirs() }
    return TxtBookFiles(
        bookDir = root,
        lockFile = File(root, "book.lock"),
        contentU16 = File(root, "content.u16"),
        metaJson = File(root, "meta.json"),
        outlineJson = File(root, "outline.json"),
        paginationDir = paginationDir,
        softBreakIdx = File(root, "softbreak.idx"),
        softBreakLock = File(root, "softbreak.lock"),
        bloomIdx = File(root, "bloom.idx"),
        bloomLock = File(root, "bloom.lock")
    )
}

internal fun writeUtf16Text(target: File, text: String) {
    Utf16LeFileWriter(target).use { writer ->
        text.forEach(writer::writeChar)
    }
}
