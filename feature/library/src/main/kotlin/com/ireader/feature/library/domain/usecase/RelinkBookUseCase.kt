package com.ireader.feature.library.domain.usecase

import android.content.Context
import android.net.Uri
import com.ireader.core.data.book.BookMaintenanceScheduler
import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.book.IndexState
import com.ireader.core.files.permission.UriPermissionStore
import com.ireader.core.files.source.ContentUriDocumentSource
import com.ireader.core.files.storage.BookStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RelinkBookUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionStore: UriPermissionStore,
    private val bookRepo: BookRepo,
    private val storage: BookStorage,
    private val scheduler: BookMaintenanceScheduler
) {
    suspend operator fun invoke(bookId: Long, uri: Uri) = withContext(Dispatchers.IO) {
        if (bookId <= 0L) return@withContext
        val book = bookRepo.getRecordById(bookId) ?: return@withContext

        val permission = permissionStore.takePersistableRead(uri)
        if (!permission.granted) {
            throw SecurityException(
                "Cannot persist read permission for $uri: ${permission.message ?: permission.code.orEmpty()}"
            )
        }

        val source = ContentUriDocumentSource(context, uri)
        val tempFile = storage.importTempFile()
        runCatching {
            copySourceToTemp(source, tempFile)

            val extension = guessExtension(
                displayName = source.displayName ?: book.fileName,
                mimeType = source.mimeType
            )

            val finalFile = storage.canonicalFile(bookId, extension)
            storage.atomicMove(tempFile, finalFile)
            storage.deleteCanonicalExcept(bookId, finalFile.absolutePath)

            bookRepo.updateBookSource(
                bookId = bookId,
                sourceUri = uri.toString(),
                canonicalPath = finalFile.absolutePath,
                lastModifiedEpochMs = finalFile.lastModified()
            )
            bookRepo.setIndexState(bookId = bookId, state = IndexState.PENDING, error = null)
            scheduler.enqueueReindex(listOf(bookId))
        }.onFailure {
            runCatching { tempFile.delete() }
            throw it
        }
    }

    private suspend fun copySourceToTemp(source: ContentUriDocumentSource, tempFile: File) {
        source.openInputStream().use { rawInput ->
            BufferedInputStream(rawInput).use { input ->
                tempFile.outputStream().use { rawOutput ->
                    BufferedOutputStream(rawOutput).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }
                }
            }
        }
    }

    private fun guessExtension(displayName: String, mimeType: String?): String {
        val lowerName = displayName.lowercase()
        return when {
            lowerName.endsWith(".epub") -> "epub"
            lowerName.endsWith(".pdf") -> "pdf"
            lowerName.endsWith(".txt") -> "txt"
            mimeType == "application/epub+zip" -> "epub"
            mimeType == "application/pdf" -> "pdf"
            else -> "txt"
        }
    }

    private companion object {
        private const val BUFFER_SIZE = 128 * 1024
    }
}
