package com.ireader.core.work

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.importing.ImportItemRepo
import com.ireader.core.data.importing.ImportJobRepo
import com.ireader.core.database.book.BookEntity
import com.ireader.core.database.importing.ImportItemEntity
import com.ireader.core.database.importing.ImportItemStatus
import com.ireader.core.database.importing.ImportStatus
import com.ireader.core.files.hash.Fingerprint
import com.ireader.core.files.importing.DuplicateStrategy
import com.ireader.core.files.scan.TreeScanner
import com.ireader.core.files.source.ContentUriDocumentSource
import com.ireader.core.files.source.FileDocumentSource
import com.ireader.core.files.storage.BookStorage
import com.ireader.core.work.notification.ImportForeground
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.BookFormat
import com.ireader.reader.runtime.format.BookFormatDetector
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@HiltWorker
class ImportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val jobRepo: ImportJobRepo,
    private val itemRepo: ImportItemRepo,
    private val bookRepo: BookRepo,
    private val storage: BookStorage,
    private val treeScanner: TreeScanner,
    private val formatDetector: BookFormatDetector
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return@withContext Result.failure()
        val currentJob = jobRepo.get(jobId) ?: return@withContext Result.failure()

        if (currentJob.sourceTreeUri != null) {
            val existingItems = itemRepo.list(jobId)
            if (existingItems.isEmpty()) {
                val uris = treeScanner.scan(Uri.parse(currentJob.sourceTreeUri))
                val now = System.currentTimeMillis()
                val scannedItems = uris.map { uri ->
                    val source = ContentUriDocumentSource(applicationContext, uri)
                    ImportItemEntity(
                        jobId = jobId,
                        uri = uri.toString(),
                        displayName = source.displayName,
                        mimeType = source.mimeType,
                        sizeBytes = source.sizeBytes,
                        status = ImportItemStatus.PENDING,
                        bookId = null,
                        fingerprintSha256 = null,
                        errorCode = null,
                        errorMessage = null,
                        updatedAtEpochMs = now
                    )
                }
                itemRepo.upsertAll(scannedItems)
                jobRepo.updateProgress(
                    jobId = jobId,
                    status = ImportStatus.QUEUED,
                    total = scannedItems.size,
                    done = 0,
                    currentTitle = null,
                    errorMessage = null,
                    now = now
                )
            }
        }

        val duplicateStrategy = runCatching {
            DuplicateStrategy.valueOf(currentJob.duplicateStrategy)
        }.getOrDefault(DuplicateStrategy.SKIP)

        val initial = jobRepo.get(jobId) ?: currentJob
        var done = initial.done
        var total = initial.total

        jobRepo.updateProgress(
            jobId = jobId,
            status = ImportStatus.RUNNING,
            total = total,
            done = done,
            currentTitle = null,
            errorMessage = null,
            now = System.currentTimeMillis()
        )

        val pendingItems = itemRepo.listPendingOrFailed(jobId)
        if (total == 0) {
            total = pendingItems.size
        }

        return@withContext try {
            setForegroundSafe(done, total, null)

            for (item in pendingItems) {
                currentCoroutineContext().ensureActive()

                val title = item.displayName ?: item.uri
                val now = System.currentTimeMillis()

                itemRepo.update(
                    jobId = jobId,
                    uri = item.uri,
                    status = ImportItemStatus.RUNNING,
                    bookId = null,
                    fingerprint = null,
                    errorCode = null,
                    errorMessage = null,
                    now = now
                )
                jobRepo.updateProgress(
                    jobId = jobId,
                    status = ImportStatus.RUNNING,
                    total = total,
                    done = done,
                    currentTitle = title,
                    errorMessage = null,
                    now = now
                )
                setForegroundSafe(done, total, title)

                val source = ContentUriDocumentSource(applicationContext, Uri.parse(item.uri))
                val outcome = runCatching { importOne(source, duplicateStrategy) }
                    .getOrElse { throwable ->
                        val (code, message) = throwable.toImportError()
                        ImportOneResult.Fail(code, message)
                    }

                when (outcome) {
                    is ImportOneResult.Ok -> {
                        done += 1
                        itemRepo.update(
                            jobId = jobId,
                            uri = item.uri,
                            status = ImportItemStatus.SUCCEEDED,
                            bookId = outcome.bookId,
                            fingerprint = outcome.fingerprint,
                            errorCode = null,
                            errorMessage = null,
                            now = System.currentTimeMillis()
                        )
                    }

                    is ImportOneResult.Skipped -> {
                        done += 1
                        itemRepo.update(
                            jobId = jobId,
                            uri = item.uri,
                            status = ImportItemStatus.SKIPPED,
                            bookId = outcome.bookId,
                            fingerprint = outcome.fingerprint,
                            errorCode = null,
                            errorMessage = "duplicate",
                            now = System.currentTimeMillis()
                        )
                    }

                    is ImportOneResult.Fail -> {
                        done += 1
                        itemRepo.update(
                            jobId = jobId,
                            uri = item.uri,
                            status = ImportItemStatus.FAILED,
                            bookId = null,
                            fingerprint = null,
                            errorCode = outcome.code,
                            errorMessage = outcome.message,
                            now = System.currentTimeMillis()
                        )
                    }
                }

                jobRepo.updateProgress(
                    jobId = jobId,
                    status = ImportStatus.RUNNING,
                    total = total,
                    done = done,
                    currentTitle = null,
                    errorMessage = null,
                    now = System.currentTimeMillis()
                )
                setForegroundSafe(done, total, null)
            }

            jobRepo.updateProgress(
                jobId = jobId,
                status = ImportStatus.SUCCEEDED,
                total = total,
                done = done,
                currentTitle = null,
                errorMessage = null,
                now = System.currentTimeMillis()
            )
            Result.success()
        } catch (cancelled: CancellationException) {
            jobRepo.updateProgress(
                jobId = jobId,
                status = ImportStatus.CANCELLED,
                total = total,
                done = done,
                currentTitle = null,
                errorMessage = "Cancelled",
                now = System.currentTimeMillis()
            )
            throw cancelled
        } catch (throwable: Throwable) {
            val (_, message) = throwable.toImportError()
            jobRepo.updateProgress(
                jobId = jobId,
                status = ImportStatus.FAILED,
                total = total,
                done = done,
                currentTitle = null,
                errorMessage = message,
                now = System.currentTimeMillis()
            )
            Result.failure()
        }
    }

    private sealed interface ImportOneResult {
        data class Ok(val bookId: String, val fingerprint: String) : ImportOneResult
        data class Skipped(val bookId: String?, val fingerprint: String) : ImportOneResult
        data class Fail(val code: String, val message: String) : ImportOneResult
    }

    private suspend fun importOne(
        source: ContentUriDocumentSource,
        duplicateStrategy: DuplicateStrategy
    ): ImportOneResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val displayName = source.displayName ?: "unknown"
        val extension = guessExtension(displayName, source.mimeType)
        val tempFile = storage.importTempFile()
        val digest = Fingerprint.newSha256()

        try {
            val copiedBytes = copyWithDigest(source, tempFile, digest)
            val fingerprint = Fingerprint.sha256Hex(digest.digest())
            val existing = bookRepo.findByFingerprint(fingerprint)

            val targetBookId = when {
                existing == null -> UUID.randomUUID().toString()
                duplicateStrategy == DuplicateStrategy.SKIP -> {
                    runCatching { tempFile.delete() }
                    return@withContext ImportOneResult.Skipped(existing.id, fingerprint)
                }

                duplicateStrategy == DuplicateStrategy.REPLACE -> existing.id
                else -> UUID.randomUUID().toString()
            }

            if (existing != null && duplicateStrategy == DuplicateStrategy.REPLACE) {
                storage.deleteCanonical(targetBookId)
            }

            val finalFile = storage.canonicalFile(targetBookId, extension)
            storage.atomicMove(tempFile, finalFile)

            val detectedFormat = detectFormatFromFile(finalFile)
            val defaultTitle = displayName.substringBeforeLast('.', displayName)
            val title = if (existing != null && duplicateStrategy == DuplicateStrategy.REPLACE) {
                existing.title ?: defaultTitle
            } else {
                defaultTitle
            }

            val entity = if (existing != null && duplicateStrategy == DuplicateStrategy.REPLACE) {
                existing.copy(
                    format = detectedFormat,
                    title = title,
                    canonicalPath = finalFile.absolutePath,
                    originalUri = source.uri.toString(),
                    displayName = displayName,
                    mimeType = source.mimeType,
                    fingerprintSha256 = fingerprint,
                    sizeBytes = copiedBytes,
                    coverPath = null,
                    updatedAtEpochMs = now
                )
            } else {
                BookEntity(
                    id = targetBookId,
                    format = detectedFormat,
                    title = title,
                    author = null,
                    language = null,
                    identifier = null,
                    canonicalPath = finalFile.absolutePath,
                    originalUri = source.uri.toString(),
                    displayName = displayName,
                    mimeType = source.mimeType,
                    fingerprintSha256 = fingerprint,
                    sizeBytes = copiedBytes,
                    coverPath = null,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )
            }

            bookRepo.upsert(entity)
            ImportOneResult.Ok(targetBookId, fingerprint)
        } catch (throwable: Throwable) {
            runCatching { tempFile.delete() }
            val (code, message) = throwable.toImportError()
            ImportOneResult.Fail(code, message)
        }
    }

    private suspend fun copyWithDigest(
        source: ContentUriDocumentSource,
        outputFile: File,
        digest: java.security.MessageDigest
    ): Long {
        var total = 0L
        source.openInputStream().use { rawInput ->
            BufferedInputStream(rawInput).use { input ->
                outputFile.outputStream().use { fileOutput ->
                    BufferedOutputStream(fileOutput).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read <= 0) {
                                break
                            }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            total += read
                        }
                        output.flush()
                    }
                }
            }
        }
        return total
    }

    private suspend fun detectFormatFromFile(file: File): BookFormat {
        val source = FileDocumentSource(file, displayName = file.name)
        return when (val result = formatDetector.detect(source, hint = null)) {
            is ReaderResult.Ok -> result.value
            is ReaderResult.Err -> BookFormat.TXT
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

    private suspend fun setForegroundSafe(done: Int, total: Int, currentTitle: String?) {
        runCatching {
            setForeground(ImportForeground.info(applicationContext, done, total, currentTitle))
        }
    }

    companion object {
        private const val KEY_JOB_ID = "job_id"
        private const val BUFFER_SIZE = 128 * 1024

        fun input(jobId: String): Data {
            return Data.Builder()
                .putString(KEY_JOB_ID, jobId)
                .build()
        }
    }
}
