package com.ireader.core.work

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.importing.ImportItemRepo
import com.ireader.core.data.importing.ImportJobRepo
import com.ireader.core.database.book.BookEntity
import com.ireader.core.database.book.BookSourceType
import com.ireader.core.database.book.IndexState
import com.ireader.core.database.book.ReadingStatus
import com.ireader.core.database.importing.ImportItemEntity
import com.ireader.core.database.importing.ImportItemStatus
import com.ireader.core.database.importing.ImportStatus
import com.ireader.core.files.hash.Fingerprint
import com.ireader.core.files.importing.DuplicateStrategy
import com.ireader.core.files.scan.TreeScanner
import com.ireader.core.files.source.ContentUriDocumentSource
import com.ireader.core.files.source.FileDocumentSource
import com.ireader.core.files.storage.BookStorage
import com.ireader.core.work.enrich.EnrichWorker
import com.ireader.core.work.enrich.EnrichWorkerInput
import com.ireader.core.work.notification.ImportForeground
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.BookFormat
import com.ireader.reader.runtime.format.BookFormatDetector
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
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

            val enrichWork = OneTimeWorkRequestBuilder<EnrichWorker>()
                .setInputData(EnrichWorkerInput.data(jobId))
                .addTag(WorkNames.tagEnrichForJob(jobId))
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.MINUTES
                )
                .build()
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                WorkNames.uniqueEnrichForJob(jobId),
                ExistingWorkPolicy.KEEP,
                enrichWork
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
        data class Ok(val bookId: Long, val fingerprint: String) : ImportOneResult
        data class Skipped(val bookId: Long?, val fingerprint: String) : ImportOneResult
        data class Fail(val code: String, val message: String) : ImportOneResult
    }

    private suspend fun importOne(
        source: ContentUriDocumentSource,
        duplicateStrategy: DuplicateStrategy
    ): ImportOneResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val fileName = source.displayName ?: "unknown"
        val extension = guessExtension(fileName, source.mimeType)
        val tempFile = storage.importTempFile()
        val digest = Fingerprint.newSha256()

        try {
            val copiedBytes = copyWithDigest(source, tempFile, digest)
            val fingerprint = Fingerprint.sha256Hex(digest.digest())
            val existing = bookRepo.findByFingerprint(fingerprint)

            if (existing != null && duplicateStrategy == DuplicateStrategy.SKIP) {
                runCatching { tempFile.delete() }
                return@withContext ImportOneResult.Skipped(existing.bookId, fingerprint)
            }

            val detectedFormat = detectFormatFromFile(tempFile, fileName)

            if (existing != null && duplicateStrategy == DuplicateStrategy.REPLACE) {
                storage.deleteCanonical(existing.bookId)
                val finalFile = storage.canonicalFile(existing.bookId, extension)
                storage.atomicMove(tempFile, finalFile)

                val updated = existing.copy(
                    sourceUri = source.uri.toString(),
                    sourceType = BookSourceType.IMPORTED_COPY,
                    format = detectedFormat,
                    fileName = fileName,
                    mimeType = source.mimeType,
                    fileSizeBytes = copiedBytes,
                    lastModifiedEpochMs = finalFile.lastModified(),
                    canonicalPath = finalFile.absolutePath,
                    fingerprintSha256 = fingerprint,
                    coverPath = null,
                    indexState = IndexState.PENDING,
                    indexError = null,
                    updatedAtEpochMs = now
                )
                bookRepo.upsert(updated)
                return@withContext ImportOneResult.Ok(existing.bookId, fingerprint)
            }

            val title = fileName.substringBeforeLast('.', fileName).ifBlank { "Untitled" }
            val placeholder = BookEntity(
                documentId = null,
                sourceUri = source.uri.toString(),
                sourceType = BookSourceType.IMPORTED_COPY,
                format = detectedFormat,
                fileName = fileName,
                mimeType = source.mimeType,
                fileSizeBytes = copiedBytes,
                lastModifiedEpochMs = null,
                canonicalPath = "",
                fingerprintSha256 = fingerprint,
                title = title,
                author = null,
                language = null,
                identifier = null,
                series = null,
                description = null,
                coverPath = null,
                favorite = false,
                readingStatus = ReadingStatus.UNREAD,
                indexState = IndexState.PENDING,
                indexError = null,
                capabilitiesJson = null,
                addedAtEpochMs = now,
                updatedAtEpochMs = now,
                lastOpenedAtEpochMs = null
            )
            val insertResult = bookRepo.upsert(placeholder)
            val insertedId = resolveInsertedBookId(insertResult, fingerprint)
                ?: throw IllegalStateException("Cannot resolve inserted book id")

            val finalFile = storage.canonicalFile(insertedId, extension)
            storage.atomicMove(tempFile, finalFile)

            val inserted = placeholder.copy(
                bookId = insertedId,
                canonicalPath = finalFile.absolutePath,
                lastModifiedEpochMs = finalFile.lastModified()
            )
            bookRepo.upsert(inserted)

            return@withContext ImportOneResult.Ok(insertedId, fingerprint)
        } catch (throwable: Throwable) {
            runCatching { tempFile.delete() }
            val (code, message) = throwable.toImportError()
            ImportOneResult.Fail(code, message)
        }
    }

    private suspend fun resolveInsertedBookId(insertResult: Long, fingerprint: String): Long? {
        if (insertResult > 0L) {
            return insertResult
        }
        return bookRepo.findByFingerprint(fingerprint)?.bookId
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

    private suspend fun detectFormatFromFile(file: File, displayName: String): BookFormat {
        val source = FileDocumentSource(file, displayName = displayName)
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
