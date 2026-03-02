# Import Boundary Implementation Context

Generated: 2026-03-02 11:33:11

Total files: 35

## File: core/work/src/main/kotlin/com/ireader/core/work/ImportWorker.kt
```kotlin
package com.ireader.core.work

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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
import com.ireader.core.work.enrich.EnrichWorker
import com.ireader.core.work.enrich.EnrichWorkerInput
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.BookFormat
import com.ireader.reader.runtime.format.BookFormatDetector
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
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

            val enrichConstraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()
            val enrichWork = OneTimeWorkRequestBuilder<EnrichWorker>()
                .setInputData(EnrichWorkerInput.data(jobId))
                .addTag(WorkNames.tagEnrichForJob(jobId))
//                .setConstraints(enrichConstraints)
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
```

## File: core/work/src/main/kotlin/com/ireader/core/work/enrich/EnrichWorker.kt
```kotlin
package com.ireader.core.work.enrich

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.importing.ImportItemRepo
import com.ireader.core.database.book.BookEntity
import com.ireader.core.files.source.FileDocumentSource
import com.ireader.core.files.storage.BookStorage
import com.ireader.core.work.notification.ImportForeground
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.runtime.ReaderRuntime
import com.ireader.core.work.enrich.epub.EpubZipEnricher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@HiltWorker
class EnrichWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val importItemRepo: ImportItemRepo,
    private val bookRepo: BookRepo,
    private val storage: BookStorage,
    private val runtime: ReaderRuntime
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobId = EnrichWorkerInput.jobId(inputData) ?: return@withContext Result.failure()
        val bookIds = importItemRepo.listSucceededBookIds(jobId)
        if (bookIds.isEmpty()) {
            return@withContext Result.success()
        }

        val metrics = applicationContext.resources.displayMetrics
        val thumbWidth = minOf(720, metrics.widthPixels.coerceAtLeast(480))
        val thumbHeight = (thumbWidth * 4 / 3).coerceAtLeast(720)

        var done = 0
        val total = bookIds.size
        val throttle = ProgressThrottle(minIntervalMs = 500L)

        try {
            updateProgress(done, total, "Enriching鈥?)

            for (bookId in bookIds) {
                currentCoroutineContext().ensureActive()

                val book = bookRepo.getById(bookId) ?: run {
                    done += 1
                    if (throttle.shouldUpdate()) {
                        updateProgress(done, total, "Enriching鈥?)
                    }
                    continue
                }

                val needMeta = book.title.isNullOrBlank() ||
                    book.author.isNullOrBlank() ||
                    book.language.isNullOrBlank() ||
                    book.identifier.isNullOrBlank()
                val needCover = book.coverPath?.let { path ->
                    path.isBlank() || !File(path).exists()
                } ?: true

                if (!needMeta && !needCover) {
                    done += 1
                    if (throttle.shouldUpdate()) {
                        updateProgress(done, total, book.title ?: "Enriching鈥?)
                    }
                    continue
                }

                val file = File(book.canonicalPath)
                if (!file.exists()) {
                    done += 1
                    if (throttle.shouldUpdate()) {
                        updateProgress(done, total, book.title ?: "Enriching鈥?)
                    }
                    continue
                }

                runCatching {
                    when (book.format) {
                        BookFormat.EPUB -> enrichEpub(
                            book = book,
                            file = file,
                            needMeta = needMeta,
                            needCover = needCover,
                            thumbWidth = thumbWidth,
                            thumbHeight = thumbHeight
                        )

                        BookFormat.TXT -> enrichTxt(
                            book = book,
                            file = file,
                            needMeta = needMeta,
                            needCover = needCover,
                            thumbWidth = thumbWidth,
                            thumbHeight = thumbHeight
                        )

                        BookFormat.PDF -> enrichPdf(
                            book = book,
                            file = file,
                            needMeta = needMeta
                        )
                    }
                }

                done += 1
                if (throttle.shouldUpdate()) {
                    updateProgress(done, total, book.title ?: "Enriching鈥?)
                }
            }

            throttle.force()
            updateProgress(done, total, "Enrich complete")
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Enrich is best-effort; do not block import main flow.
            Result.success()
        }
    }

    private suspend fun enrichEpub(
        book: BookEntity,
        file: File,
        needMeta: Boolean,
        needCover: Boolean,
        thumbWidth: Int,
        thumbHeight: Int
    ) {
        val parsed = EpubZipEnricher.parse(file)

        var newTitle: String? = book.title
        var newAuthor: String? = book.author
        var newLanguage: String? = book.language
        var newIdentifier: String? = book.identifier
        var coverPath: String? = book.coverPath

        if (needMeta && parsed != null) {
            val metadata = parsed.metadata
            if (newTitle.isNullOrBlank()) {
                newTitle = metadata.title
            }
            if (newAuthor.isNullOrBlank()) {
                newAuthor = metadata.author
            }
            if (newLanguage.isNullOrBlank()) {
                newLanguage = metadata.language
            }
            if (newIdentifier.isNullOrBlank()) {
                newIdentifier = metadata.identifier
            }
        }

        if (needCover) {
            val coverFile = storage.coverFile(book.id)
            val titleForCover = newTitle ?: book.title ?: book.displayName ?: "Untitled"

            val extracted = parsed?.coverPathInZip?.let { coverPathInZip ->
                EpubZipEnricher.tryExtractCoverToPng(
                    file = file,
                    coverPathInZip = coverPathInZip,
                    outFile = coverFile,
                    reqWidth = thumbWidth,
                    reqHeight = thumbHeight
                )
            } ?: false

            if (!extracted) {
                val placeholder = CoverRenderer.placeholderBitmap(thumbWidth, thumbHeight, titleForCover)
                BitmapIO.savePng(coverFile, placeholder)
            }
            coverPath = coverFile.absolutePath
        }

        upsertIfChanged(
            book = book,
            title = newTitle,
            author = newAuthor,
            language = newLanguage,
            identifier = newIdentifier,
            coverPath = coverPath
        )
    }

    private suspend fun enrichTxt(
        book: BookEntity,
        file: File,
        needMeta: Boolean,
        needCover: Boolean,
        thumbWidth: Int,
        thumbHeight: Int
    ) {
        var newTitle: String? = book.title
        var newAuthor: String? = book.author
        var newLanguage: String? = book.language
        var newIdentifier: String? = book.identifier
        var coverPath: String? = book.coverPath

        if (needMeta) {
            val metadata = readMetadataFromRuntime(book = book, file = file)
            if (metadata != null) {
                if (newTitle.isNullOrBlank()) {
                    newTitle = metadata.title
                }
                if (newAuthor.isNullOrBlank()) {
                    newAuthor = metadata.author
                }
                if (newLanguage.isNullOrBlank()) {
                    newLanguage = metadata.language
                }
                if (newIdentifier.isNullOrBlank()) {
                    newIdentifier = metadata.identifier
                }
            }
        }

        if (needCover) {
            val coverFile = storage.coverFile(book.id)
            val titleForCover = newTitle ?: book.title ?: book.displayName ?: "Untitled"
            val placeholder = CoverRenderer.placeholderBitmap(thumbWidth, thumbHeight, titleForCover)
            BitmapIO.savePng(coverFile, placeholder)
            coverPath = coverFile.absolutePath
        }

        upsertIfChanged(
            book = book,
            title = newTitle,
            author = newAuthor,
            language = newLanguage,
            identifier = newIdentifier,
            coverPath = coverPath
        )
    }

    private suspend fun enrichPdf(
        book: BookEntity,
        file: File,
        needMeta: Boolean
    ) {
        if (!needMeta) {
            return
        }

        var newTitle: String? = book.title
        var newAuthor: String? = book.author
        var newLanguage: String? = book.language
        var newIdentifier: String? = book.identifier

        val metadata = readMetadataFromRuntime(book = book, file = file)
        if (metadata != null) {
            if (newTitle.isNullOrBlank()) {
                newTitle = metadata.title
            }
            if (newAuthor.isNullOrBlank()) {
                newAuthor = metadata.author
            }
            if (newLanguage.isNullOrBlank()) {
                newLanguage = metadata.language
            }
            if (newIdentifier.isNullOrBlank()) {
                newIdentifier = metadata.identifier
            }
        }

        upsertIfChanged(
            book = book,
            title = newTitle,
            author = newAuthor,
            language = newLanguage,
            identifier = newIdentifier,
            coverPath = book.coverPath
        )
    }

    private suspend fun readMetadataFromRuntime(
        book: BookEntity,
        file: File
    ): DocumentMetadata? {
        val source = FileDocumentSource(file, displayName = book.displayName ?: file.name)
        val documentResult = runtime.openDocument(
            source = source,
            options = OpenOptions(hintFormat = book.format)
        )
        if (documentResult !is ReaderResult.Ok) {
            return null
        }

        return documentResult.value.use { document ->
            when (val metadataResult = document.metadata()) {
                is ReaderResult.Ok -> metadataResult.value
                is ReaderResult.Err -> null
            }
        }
    }

    private suspend fun upsertIfChanged(
        book: BookEntity,
        title: String?,
        author: String?,
        language: String?,
        identifier: String?,
        coverPath: String?
    ) {
        val changed = title != book.title ||
            author != book.author ||
            language != book.language ||
            identifier != book.identifier ||
            coverPath != book.coverPath

        if (!changed) {
            return
        }

        bookRepo.upsert(
            book.copy(
                title = title,
                author = author,
                language = language,
                identifier = identifier,
                coverPath = coverPath,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    private suspend fun updateProgress(done: Int, total: Int, title: String?) {
        runCatching {
            setProgress(EnrichProgress.data(done, total, title))
        }
        runCatching {
            setForeground(ImportForeground.info(applicationContext, done, total, title))
        }
    }
}
```

## File: core/work/src/main/kotlin/com/ireader/core/work/enrich/epub/EpubZipEnricher.kt
```kotlin
package com.ireader.core.work.enrich.epub

import android.graphics.Bitmap
import android.util.Xml
import com.ireader.core.work.enrich.BitmapDecode
import com.ireader.core.work.enrich.BitmapIO
import com.ireader.reader.model.DocumentMetadata
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.ArrayDeque
import java.util.zip.ZipFile
import org.xmlpull.v1.XmlPullParser

object EpubZipEnricher {

    data class Parsed(
        val metadata: DocumentMetadata,
        val coverPathInZip: String?
    )

    fun parse(file: File): Parsed? {
        return runCatching {
            ZipFile(file).use { zip ->
                val opfPath = findOpfPath(zip) ?: return null
                val opfEntry = zip.getEntry(opfPath) ?: return null
                zip.getInputStream(opfEntry).use { input ->
                    parseOpf(opfPath = opfPath, opfStream = input)
                }
            }
        }.getOrNull()
    }

    fun tryExtractCoverToPng(
        file: File,
        coverPathInZip: String,
        outFile: File,
        reqWidth: Int,
        reqHeight: Int
    ): Boolean {
        return runCatching {
            ZipFile(file).use { zip ->
                val normalizedPath = normalizeZipPath(coverPathInZip)
                val coverEntry = zip.getEntry(normalizedPath) ?: return false
                val bytes = zip.getInputStream(coverEntry).use { stream ->
                    stream.readAllBytesCapped(limitBytes = 12 * 1024 * 1024)
                } ?: return false

                val decoded = BitmapDecode.decodeSampled(bytes, reqWidth, reqHeight) ?: return false
                val outputBitmap = if (decoded.width == reqWidth && decoded.height == reqHeight) {
                    decoded
                } else {
                    Bitmap.createScaledBitmap(decoded, reqWidth, reqHeight, true)
                }
                BitmapIO.savePng(outFile, outputBitmap)
                true
            }
        }.getOrDefault(false)
    }

    private fun findOpfPath(zip: ZipFile): String? {
        val container = zip.getEntry(CONTAINER_XML_PATH) ?: return null
        zip.getInputStream(container).use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, null)

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name.equals("rootfile", ignoreCase = true)) {
                    val fullPath = attr(parser, "full-path")
                    if (!fullPath.isNullOrBlank()) {
                        return normalizeZipPath(fullPath)
                    }
                }
                event = parser.next()
            }
        }
        return null
    }

    private fun parseOpf(opfPath: String, opfStream: InputStream): Parsed {
        val baseDir = opfPath.substringBeforeLast('/', "")
        val parser = Xml.newPullParser()
        parser.setInput(opfStream, null)

        var title: String? = null
        var creator: String? = null
        var language: String? = null
        var identifier: String? = null

        var coverIdFromMeta: String? = null
        var coverHrefFromProperties: String? = null
        val manifestIdToHref = HashMap<String, String>(64)

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val name = parser.name
                when {
                    name.endsWith("title", ignoreCase = true) && title == null && isDcTag(parser) -> {
                        title = parser.nextTextOrNull()?.trim().takeUnless { it.isNullOrBlank() }
                    }

                    name.endsWith("creator", ignoreCase = true) && creator == null && isDcTag(parser) -> {
                        creator = parser.nextTextOrNull()?.trim().takeUnless { it.isNullOrBlank() }
                    }

                    name.endsWith("language", ignoreCase = true) && language == null && isDcTag(parser) -> {
                        language = parser.nextTextOrNull()?.trim().takeUnless { it.isNullOrBlank() }
                    }

                    name.endsWith("identifier", ignoreCase = true) && identifier == null && isDcTag(parser) -> {
                        identifier = parser.nextTextOrNull()?.trim().takeUnless { it.isNullOrBlank() }
                    }

                    name.equals("meta", ignoreCase = true) -> {
                        val metaName = attr(parser, "name")
                        val metaContent = attr(parser, "content")
                        if (metaName.equals("cover", ignoreCase = true) && !metaContent.isNullOrBlank()) {
                            coverIdFromMeta = metaContent.trim()
                        }
                    }

                    name.equals("item", ignoreCase = true) -> {
                        val id = attr(parser, "id").orEmpty()
                        val href = attr(parser, "href")?.trim()
                        val properties = attr(parser, "properties").orEmpty()

                        if (id.isNotBlank() && !href.isNullOrBlank()) {
                            manifestIdToHref[id] = href
                        }

                        if (coverHrefFromProperties == null &&
                            properties.split(' ').any { it.equals("cover-image", ignoreCase = true) }
                        ) {
                            coverHrefFromProperties = href
                        }
                    }
                }
            }
            event = parser.next()
        }

        val coverHref = when {
            !coverHrefFromProperties.isNullOrBlank() -> coverHrefFromProperties
            !coverIdFromMeta.isNullOrBlank() -> manifestIdToHref[coverIdFromMeta]
            else -> null
        }

        val coverPath = coverHref
            ?.let { stripQueryAndFragment(it) }
            ?.let { resolveZipPath(baseDir, it) }
            ?.let { normalizeZipPath(it) }
            ?.takeIf { it.isNotBlank() }

        return Parsed(
            metadata = DocumentMetadata(
                title = title,
                author = creator,
                language = language,
                identifier = identifier,
                extra = buildMap {
                    if (!coverPath.isNullOrBlank()) {
                        put("coverPath", coverPath)
                    }
                }
            ),
            coverPathInZip = coverPath
        )
    }

    private fun attr(parser: XmlPullParser, name: String): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i).equals(name, ignoreCase = true)) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    private fun isDcTag(parser: XmlPullParser): Boolean {
        val prefix = parser.prefix.orEmpty()
        val namespace = parser.namespace.orEmpty()
        return prefix.equals("dc", ignoreCase = true) || namespace.contains("purl.org/dc", ignoreCase = true)
    }

    private fun XmlPullParser.nextTextOrNull(): String? {
        return runCatching { nextText() }.getOrNull()
    }

    private fun stripQueryAndFragment(path: String): String {
        return path.substringBefore('#').substringBefore('?')
    }

    private fun resolveZipPath(baseDir: String, href: String): String {
        if (baseDir.isBlank()) {
            return href
        }
        return "$baseDir/$href"
    }

    private fun normalizeZipPath(path: String): String {
        val parts = path
            .replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() }

        val stack = ArrayDeque<String>(parts.size)
        for (part in parts) {
            when (part) {
                "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(part)
            }
        }
        return stack.joinToString("/")
    }

    private fun InputStream.readAllBytesCapped(limitBytes: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read <= 0) {
                break
            }
            total += read
            if (total > limitBytes) {
                return null
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private const val CONTAINER_XML_PATH = "META-INF/container.xml"
}
```

## File: core/reader/runtime/src/main/kotlin/com/ireader/reader/runtime/ReaderRuntime.kt
```kotlin
package com.ireader.reader.runtime

import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.Locator
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.render.RenderConfig
import com.ireader.core.files.source.DocumentSource

interface ReaderRuntime {

    suspend fun openDocument(
        source: DocumentSource,
        options: OpenOptions = OpenOptions()
    ): ReaderResult<ReaderDocument>

    suspend fun openSession(
        source: DocumentSource,
        options: OpenOptions = OpenOptions(),
        initialLocator: Locator? = null,
        initialConfig: RenderConfig? = null
    ): ReaderResult<ReaderSessionHandle>
}
```

## File: core/reader/runtime/src/main/kotlin/com/ireader/reader/runtime/DefaultReaderRuntime.kt
```kotlin
package com.ireader.reader.runtime

import com.ireader.reader.api.engine.EngineRegistry
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.Locator
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.render.RenderConfig
import com.ireader.core.files.source.DocumentSource
import com.ireader.reader.runtime.error.toReaderError
import com.ireader.reader.runtime.format.BookFormatDetector
import com.ireader.reader.runtime.format.DefaultBookFormatDetector
import com.ireader.reader.runtime.render.RenderDefaults

class DefaultReaderRuntime(
    private val engineRegistry: EngineRegistry,
    private val formatDetector: BookFormatDetector = DefaultBookFormatDetector()
) : ReaderRuntime {

    override suspend fun openDocument(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<ReaderDocument> {
        val formatResult = formatDetector.detect(source, options.hintFormat)
        return when (formatResult) {
            is ReaderResult.Ok -> openWithFormat(source, options, formatResult.value)
            is ReaderResult.Err -> formatResult
        }
    }

    override suspend fun openSession(
        source: DocumentSource,
        options: OpenOptions,
        initialLocator: Locator?,
        initialConfig: RenderConfig?
    ): ReaderResult<ReaderSessionHandle> {
        val docResult = openDocument(source, options)
        return when (docResult) {
            is ReaderResult.Err -> docResult
            is ReaderResult.Ok -> {
                val document = docResult.value
                val config = initialConfig ?: RenderDefaults.configFor(document.capabilities)

                val sessionResult = catchingSuspend { document.createSession(initialLocator, config) }
                when (sessionResult) {
                    is ReaderResult.Ok -> ReaderResult.Ok(ReaderSessionHandle(document, sessionResult.value))
                    is ReaderResult.Err -> {
                        // session 鍒涘缓澶辫触瑕佸叧 document锛岄伩鍏嶆硠闇?
                        runCatching { document.close() }
                        ReaderResult.Err(sessionResult.error)
                    }
                }
            }
        }
    }

    private suspend fun openWithFormat(
        source: DocumentSource,
        options: OpenOptions,
        format: BookFormat
    ): ReaderResult<ReaderDocument> {
        val engine = engineRegistry.engineFor(format)
            ?: return ReaderResult.Err(ReaderError.UnsupportedFormat(detected = format.name))

        // 闃插尽锛氬紩鎿庡疄鐜伴噷濡傛灉 throw锛岃繖閲岀粺涓€鏀舵暃鎴?ReaderError
        return catchingSuspend { engine.open(source, options) }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend inline fun <T> catchingSuspend(
        crossinline block: suspend () -> ReaderResult<T>
    ): ReaderResult<T> {
        return try {
            block()
        } catch (t: Throwable) {
            ReaderResult.Err(t.toReaderError())
        }
    }
}
```

## File: core/reader/api/src/main/kotlin/com/ireader/reader/api/engine/ReaderEngine.kt
```kotlin
package com.ireader.reader.api.engine

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.BookFormat
import com.ireader.reader.api.open.OpenOptions
import com.ireader.core.files.source.DocumentSource

interface ReaderEngine {
    val supportedFormats: Set<BookFormat>

    /**
     * 鎵撳紑鏂囨。锛氳В鏋愩€佽В鍘嬨€佸缓绔嬬储寮曠瓑
     * 澶辫触杩斿洖 ReaderResult.Err锛岄伩鍏嶅紓甯告暎钀?UI 灞傘€?
     */
    suspend fun open(
        source: DocumentSource,
        options: OpenOptions = OpenOptions()
    ): ReaderResult<ReaderDocument>
}
```

## File: core/reader/api/src/main/kotlin/com/ireader/reader/api/engine/ReaderDocument.kt
```kotlin
package com.ireader.reader.api.engine

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.Locator
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.render.RenderConfig
import java.io.Closeable

interface ReaderDocument : Closeable {
    val id: DocumentId
    val format: com.ireader.reader.model.BookFormat
    val capabilities: DocumentCapabilities
    val openOptions: OpenOptions

    suspend fun metadata(): ReaderResult<DocumentMetadata>

    /**
     * 浼氳瘽锛氬寘鍚綋鍓嶉槄璇讳綅缃€佹覆鏌撹缃€佺紦瀛樼瓑
     */
    suspend fun createSession(
        initialLocator: Locator? = null,
        initialConfig: RenderConfig = RenderConfig.Default
    ): ReaderResult<ReaderSession>
}
```

## File: core/reader/api/src/main/kotlin/com/ireader/reader/api/engine/EngineRegistry.kt
```kotlin
package com.ireader.reader.api.engine

import com.ireader.reader.model.BookFormat

interface EngineRegistry {
    fun engineFor(format: BookFormat): ReaderEngine?
}
```

## File: core/reader/runtime/src/main/kotlin/com/ireader/reader/runtime/registry/EngineRegistryImpl.kt
```kotlin
package com.ireader.reader.runtime.registry

import com.ireader.reader.api.engine.EngineRegistry
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.model.BookFormat

class EngineRegistryImpl(
    engines: Set<ReaderEngine>
) : EngineRegistry {

    private val byFormat: Map<BookFormat, ReaderEngine> = buildFormatMap(engines)

    override fun engineFor(format: BookFormat): ReaderEngine? = byFormat[format]

    private fun buildFormatMap(engines: Set<ReaderEngine>): Map<BookFormat, ReaderEngine> {
        val map = mutableMapOf<BookFormat, ReaderEngine>()
        val duplicates = mutableMapOf<BookFormat, MutableList<String>>()

        for (engine in engines) {
            for (format in engine.supportedFormats) {
                val existing = map[format]
                if (existing == null) {
                    map[format] = engine
                } else {
                    duplicates.getOrPut(format) { mutableListOf() }
                        .add(existing::class.qualifiedName.orEmpty())
                    duplicates.getOrPut(format) { mutableListOf() }
                        .add(engine::class.qualifiedName.orEmpty())
                }
            }
        }

        require(duplicates.isEmpty()) {
            "Multiple engines registered for same format: $duplicates"
        }

        return map.toMap()
    }
}
```

## File: engines/epub/src/main/kotlin/com/ireader/engines/epub/EpubEngine.kt
```kotlin
package com.ireader.engines.epub

import android.content.Context
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.engines.epub.internal.open.EpubOpener
import com.ireader.reader.model.BookFormat
import com.ireader.core.files.source.DocumentSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class EpubEngine(
    context: Context,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ReaderEngine {
    private val opener = EpubOpener(
        context = context.applicationContext,
        ioDispatcher = ioDispatcher
    )

    override val supportedFormats: Set<BookFormat> = setOf(BookFormat.EPUB)

    override suspend fun open(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<ReaderDocument> {
        return opener.open(source, options)
    }
}
```

## File: engines/epub/src/main/kotlin/com/ireader/engines/epub/internal/open/EpubOpener.kt
```kotlin
package com.ireader.engines.epub.internal.open

import android.content.Context
import com.ireader.engines.epub.internal.parser.ContainerParser
import com.ireader.engines.epub.internal.parser.NavParser
import com.ireader.engines.epub.internal.parser.NcxParser
import com.ireader.engines.epub.internal.parser.OpfParser
import com.ireader.engines.epub.internal.content.EpubStore
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.DocumentId
import com.ireader.core.files.source.DocumentSource
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.zip.ZipException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class EpubOpener(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun open(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<ReaderDocument> = withContext(ioDispatcher) {
        try {
            val stableId = stableId(source)
            val docId = DocumentId("epub:$stableId")

            EpubLocks.withDocLock(docId.value) {
                val baseDir = File(context.cacheDir, "epub/$stableId")
                val extractedDir = File(baseDir, EXTRACTED_DIR)
                val markerFile = File(baseDir, COMPLETE_MARKER)

                if (!markerFile.exists()) {
                    if (extractedDir.exists()) {
                        extractedDir.deleteRecursively()
                    }
                    extractedDir.mkdirs()
                    ZipExtract.extractTo(source, extractedDir, ioDispatcher)
                    markerFile.parentFile?.mkdirs()
                    markerFile.writeText("ok")
                }

                val containerXml = File(extractedDir, CONTAINER_XML)
                if (!containerXml.exists()) {
                    return@withDocLock ReaderResult.Err(
                        ReaderError.CorruptOrInvalid("Missing $CONTAINER_XML")
                    )
                }

                val opfPath = ContainerParser.parseOpfPath(containerXml)
                    ?: return@withDocLock ReaderResult.Err(
                        ReaderError.CorruptOrInvalid("Invalid container.xml")
                    )

                val opfFile = File(extractedDir, opfPath)
                if (!opfFile.exists()) {
                    return@withDocLock ReaderResult.Err(
                        ReaderError.CorruptOrInvalid("Missing OPF: $opfPath")
                    )
                }

                val pkg = OpfParser.parse(opfFile = opfFile, opfPath = opfPath)
                if (pkg.spine.isEmpty()) {
                    return@withDocLock ReaderResult.Err(
                        ReaderError.CorruptOrInvalid("OPF spine is empty")
                    )
                }

                val toc = pkg.navPath
                    ?.let { navRelPath ->
                        val navFile = File(extractedDir, navRelPath)
                        if (navFile.exists()) {
                            NavParser.parse(navFile = navFile, navRelPath = navRelPath)
                        } else {
                            emptyList()
                        }
                    }
                    .orEmpty()
                    .ifEmpty {
                        pkg.ncxPath
                            ?.let { ncxRelPath ->
                                val ncxFile = File(extractedDir, ncxRelPath)
                                if (ncxFile.exists()) {
                                    NcxParser.parse(ncxFile = ncxFile, ncxRelPath = ncxRelPath)
                                } else {
                                    emptyList()
                                }
                            }
                            .orEmpty()
                    }

                val authority = "${context.packageName}.epub"
                val container = EpubContainer(
                    id = docId,
                    rootDir = extractedDir,
                    authority = authority,
                    opf = pkg,
                    outline = toc
                )

                EpubStore.register(docId.value, extractedDir)
                ReaderResult.Ok(
                    EpubDocument(
                        container = container,
                        openOptions = options,
                        ioDispatcher = ioDispatcher
                    )
                )
            }
        } catch (t: Throwable) {
            ReaderResult.Err(t.toReaderError())
        }
    }

    private fun stableId(source: DocumentSource): String {
        val seed = buildString {
            append(source.uri)
            append('|')
            append(source.displayName.orEmpty())
            append('|')
            append(source.sizeBytes ?: -1)
        }
        val bytes = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        return buildString(32) {
            for (i in 0 until 16) {
                val b = bytes[i].toInt() and 0xFF
                append("0123456789abcdef"[b ushr 4])
                append("0123456789abcdef"[b and 0x0F])
            }
        }
    }

    private fun Throwable.toReaderError(): ReaderError = when (this) {
        is ReaderError -> this
        is CancellationException -> ReaderError.Cancelled(cause = this)
        is FileNotFoundException -> ReaderError.NotFound(cause = this)
        is SecurityException -> ReaderError.PermissionDenied(cause = this)
        is ZipException -> ReaderError.CorruptOrInvalid(cause = this)
        is IOException -> ReaderError.Io(cause = this)
        else -> ReaderError.Internal(message = message, cause = this)
    }

    private companion object {
        private const val EXTRACTED_DIR = "extracted"
        private const val COMPLETE_MARKER = ".complete"
        private const val CONTAINER_XML = "META-INF/container.xml"
    }
}
```

## File: engines/epub/src/main/kotlin/com/ireader/engines/epub/internal/open/EpubDocument.kt
```kotlin
package com.ireader.engines.epub.internal.open

import com.ireader.engines.epub.internal.content.EpubStore
import com.ireader.engines.epub.internal.session.EpubSession
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.Locator
import kotlinx.coroutines.CoroutineDispatcher

internal class EpubDocument(
    private val container: EpubContainer,
    override val openOptions: OpenOptions,
    private val ioDispatcher: CoroutineDispatcher
) : ReaderDocument {

    override val id: DocumentId = container.id

    override val format: BookFormat = BookFormat.EPUB

    override val capabilities: DocumentCapabilities = DocumentCapabilities(
        reflowable = true,
        fixedLayout = false,
        outline = true,
        search = true,
        textExtraction = true,
        annotations = true,
        links = true
    )

    override suspend fun metadata(): ReaderResult<DocumentMetadata> {
        val extra = buildMap {
            put("uriAuthority", container.authority)
            put("spineCount", container.spineCount.toString())
            putAll(container.opf.metadata.extra)
        }
        return ReaderResult.Ok(container.opf.metadata.copy(extra = extra))
    }

    override suspend fun createSession(
        initialLocator: Locator?,
        initialConfig: RenderConfig
    ): ReaderResult<ReaderSession> {
        val config = (initialConfig as? RenderConfig.ReflowText) ?: RenderConfig.ReflowText()
        return EpubSession.create(
            container = container,
            initialLocator = initialLocator,
            initialConfig = config,
            ioDispatcher = ioDispatcher
        )
    }

    override fun close() {
        EpubStore.unregister(id.value)
    }
}
```

## File: engines/epub/src/main/kotlin/com/ireader/engines/epub/internal/parser/ContainerParser.kt
```kotlin
package com.ireader.engines.epub.internal.parser

import java.io.File

internal object ContainerParser {

    fun parseOpfPath(containerXml: File): String? {
        val document = XmlDom.parse(containerXml)
        val root = document.documentElement ?: return null

        val rootfile = XmlDom.descendants(root)
            .firstOrNull { XmlDom.localName(it).equals("rootfile", ignoreCase = true) }
            ?: return null

        val fullPath = XmlDom.attr(rootfile, "full-path")?.trim().orEmpty()
        if (fullPath.isBlank()) return null
        return PathResolver.normalizePath(fullPath)
    }
}
```

## File: engines/epub/src/main/kotlin/com/ireader/engines/epub/internal/parser/OpfParser.kt
```kotlin
package com.ireader.engines.epub.internal.parser

import com.ireader.engines.epub.internal.parser.model.EpubManifestItem
import com.ireader.engines.epub.internal.parser.model.EpubPackage
import com.ireader.engines.epub.internal.parser.model.EpubSpineItem
import com.ireader.reader.model.DocumentMetadata
import java.io.File

internal object OpfParser {

    fun parse(opfFile: File, opfPath: String): EpubPackage {
        val document = XmlDom.parse(opfFile)
        val root = document.documentElement

        val manifest = linkedMapOf<String, EpubManifestItem>()
        val spineIdRefs = mutableListOf<String>()

        var title: String? = null
        var author: String? = null
        var language: String? = null
        var identifier: String? = null
        var spineTocId: String? = null

        XmlDom.descendants(root).forEach { element ->
            when (XmlDom.localName(element).lowercase()) {
                "title" -> if (title == null) {
                    title = XmlDom.textContentTrimmed(element)
                }

                "creator", "author" -> if (author == null) {
                    author = XmlDom.textContentTrimmed(element)
                }

                "language" -> if (language == null) {
                    language = XmlDom.textContentTrimmed(element)
                }

                "identifier" -> if (identifier == null) {
                    identifier = XmlDom.textContentTrimmed(element)
                }

                "spine" -> {
                    spineTocId = XmlDom.attr(element, "toc")?.trim()
                }

                "item" -> {
                    val id = XmlDom.attr(element, "id")?.trim().orEmpty()
                    val href = XmlDom.attr(element, "href")?.trim().orEmpty()
                    val mediaType = XmlDom.attr(element, "media-type")?.trim()
                    val properties = XmlDom.attr(element, "properties")?.trim()
                    if (id.isNotBlank() && href.isNotBlank()) {
                        manifest[id] = EpubManifestItem(
                            id = id,
                            href = href,
                            mediaType = mediaType,
                            properties = properties
                        )
                    }
                }

                "itemref" -> {
                    val idRef = XmlDom.attr(element, "idref")?.trim().orEmpty()
                    if (idRef.isNotBlank()) {
                        spineIdRefs += idRef
                    }
                }
            }
        }

        val spine = spineIdRefs.mapNotNull { idRef ->
            val manifestItem = manifest[idRef] ?: return@mapNotNull null
            EpubSpineItem(
                idRef = idRef,
                href = PathResolver.resolveFrom(opfPath, manifestItem.href),
                mediaType = manifestItem.mediaType
            )
        }

        val navPath = manifest.values
            .firstOrNull { item -> hasNavProperty(item.properties) }
            ?.href
            ?.let { href -> PathResolver.resolveFrom(opfPath, href) }

        val ncxPath = manifest.values
            .firstOrNull { item -> item.mediaType == NCX_MEDIA_TYPE }
            ?.href
            ?.let { href -> PathResolver.resolveFrom(opfPath, href) }
            ?: spineTocId
                ?.let { id -> manifest[id]?.href }
                ?.let { href -> PathResolver.resolveFrom(opfPath, href) }

        val metadata = DocumentMetadata(
            title = title,
            author = author,
            language = language,
            identifier = identifier
        )

        val mediaTypeByPath = buildMap {
            manifest.values.forEach { item ->
                val mediaType = item.mediaType ?: return@forEach
                val path = PathResolver.resolveFrom(opfPath, item.href)
                put(path, mediaType)
            }
        }

        return EpubPackage(
            metadata = metadata,
            manifest = manifest.toMap(),
            spine = spine,
            opfPath = PathResolver.normalizePath(opfPath),
            opfDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "").trim('/'),
            navPath = navPath,
            ncxPath = ncxPath,
            mediaTypeByPath = mediaTypeByPath
        )
    }

    private const val NCX_MEDIA_TYPE = "application/x-dtbncx+xml"

    private fun hasNavProperty(properties: String?): Boolean {
        if (properties.isNullOrBlank()) return false
        return properties
            .split(' ')
            .any { property -> property.equals("nav", ignoreCase = true) }
    }
}
```

## File: core/model/src/main/kotlin/com/ireader/reader/model/DocumentMetadata.kt
```kotlin
package com.ireader.reader.model

data class DocumentMetadata(
    val title: String? = null,
    val author: String? = null,
    val language: String? = null,
    val identifier: String? = null, // ISBN / OPF id / 鑷畾涔?    val extra: Map<String, String> = emptyMap(),
)
```

## File: core/database/src/main/kotlin/com/ireader/core/database/book/BookEntity.kt
```kotlin
package com.ireader.core.database.book

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ireader.reader.model.BookFormat

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val format: BookFormat,
    val title: String?,
    val author: String?,
    val language: String?,
    val identifier: String?,
    val canonicalPath: String,
    val originalUri: String?,
    val displayName: String?,
    val mimeType: String?,
    val fingerprintSha256: String,
    val sizeBytes: Long,
    val coverPath: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
```

## File: core/data/src/main/kotlin/com/ireader/core/data/book/BookRepo.kt
```kotlin
package com.ireader.core.data.book

import com.ireader.core.database.book.BookDao
import com.ireader.core.database.book.BookEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepo @Inject constructor(
    private val dao: BookDao
) {
    suspend fun upsert(entity: BookEntity) = dao.upsert(entity)

    suspend fun findByFingerprint(fingerprint: String) = dao.findByFingerprint(fingerprint)

    suspend fun getById(bookId: String) = dao.getById(bookId)

    suspend fun deleteById(bookId: String) = dao.deleteById(bookId)
}
```

## File: app/src/main/java/com/ireader/di/ReaderRuntimeModule.kt
```kotlin
package com.ireader.di

import android.content.Context
import com.ireader.engines.epub.EpubEngine
import com.ireader.engines.pdf.PdfEngine
import com.ireader.engines.txt.TxtEngine
import com.ireader.engines.txt.TxtEngineConfig
import com.ireader.reader.api.engine.EngineRegistry
import com.ireader.reader.runtime.DefaultReaderRuntime
import com.ireader.reader.runtime.ReaderRuntime
import com.ireader.reader.runtime.registry.EngineRegistryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReaderRuntimeModule {

    @Provides
    @Singleton
    fun provideEngineRegistry(
        @ApplicationContext context: Context
    ): EngineRegistry {
        return EngineRegistryImpl(
            setOf(
                TxtEngine(
                    config = TxtEngineConfig(
                        cacheDir = context.cacheDir,
                        persistPagination = true,
                        persistOutline = false
                    )
                ),
                EpubEngine(context = context),
                PdfEngine()
            )
        )
    }

    @Provides
    @Singleton
    fun provideReaderRuntime(
        engineRegistry: EngineRegistry
    ): ReaderRuntime {
        return DefaultReaderRuntime(engineRegistry)
    }
}
```

## File: core/reader/api/src/main/kotlin/com/ireader/reader/api/open/OpenOptions.kt
```kotlin
package com.ireader.reader.api.open

import com.ireader.reader.model.BookFormat

data class OpenOptions(
    val hintFormat: BookFormat? = null,
    val password: String? = null,      // PDF
    val textEncoding: String? = null,  // TXT: "UTF-8"/"GBK"/...
    val extra: Map<String, String> = emptyMap()
)
```

## File: core/reader/api/src/main/kotlin/com/ireader/reader/api/error/ReaderResult.kt
```kotlin
package com.ireader.reader.api.error

sealed interface ReaderResult<out T> {
    data class Ok<T>(val value: T) : ReaderResult<T>
    data class Err(val error: ReaderError) : ReaderResult<Nothing>
}

inline fun <T> ReaderResult<T>.getOrNull(): T? =
    when (this) {
        is ReaderResult.Ok -> value
        is ReaderResult.Err -> null
    }

inline fun <T> ReaderResult<T>.getOrThrow(): T =
    when (this) {
        is ReaderResult.Ok -> value
        is ReaderResult.Err -> throw error
    }
```

## File: core/reader/api/src/main/kotlin/com/ireader/reader/api/error/ReaderError.kt
```kotlin
package com.ireader.reader.api.error

/**
 * api 灞傞敊璇被鍨嬶細淇濊瘉 UI/涓氬姟鍙互绋冲畾璇嗗埆骞跺仛鍏滃簳銆?
 */
sealed class ReaderError(
    message: String? = null,
    cause: Throwable? = null,
    val code: String
) : RuntimeException(message, cause) {

    class UnsupportedFormat(
        val detected: String? = null,
        message: String? = "Unsupported format",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "UNSUPPORTED_FORMAT")

    class NotFound(
        message: String? = "File not found",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "NOT_FOUND")

    class PermissionDenied(
        message: String? = "Permission denied",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "PERMISSION_DENIED")

    class InvalidPassword(
        message: String? = "Invalid password",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "INVALID_PASSWORD")

    class CorruptOrInvalid(
        message: String? = "Corrupt or invalid document",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "CORRUPT_OR_INVALID")

    class DrmRestricted(
        message: String? = "DRM restricted",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "DRM_RESTRICTED")

    class Io(
        message: String? = "I/O error",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "IO")

    class Cancelled(
        message: String? = "Cancelled",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "CANCELLED")

    class Internal(
        message: String? = "Internal error",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "INTERNAL")
}
```

## File: core/model/src/main/kotlin/com/ireader/reader/model/BookFormat.kt
```kotlin
package com.ireader.reader.model

enum class BookFormat { TXT, EPUB, PDF }
```

## File: core/model/src/main/kotlin/com/ireader/reader/model/DocumentCapabilities.kt
```kotlin
package com.ireader.reader.model

data class DocumentCapabilities(
    // TXT/EPUB = true
    val reflowable: Boolean,
    // PDF = true
    val fixedLayout: Boolean,
    val outline: Boolean,
    val search: Boolean,
    // 澶嶅埗/鍒嗕韩/瀵煎嚭
    val textExtraction: Boolean,
    // 鑳藉惁鎶婃爣娉ㄦ槧灏勫埌椤甸潰锛坮eflow/fixed锛?    val annotations: Boolean,
    // 鏄惁鑳芥彁渚涘彲鐐瑰嚮閾炬帴淇℃伅锛堝唴閮?澶栭儴锛?    val links: Boolean,
)
```

## File: core/files/src/main/kotlin/com/ireader/core/files/source/DocumentSource.kt
```kotlin
package com.ireader.core.files.source

import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.InputStream

interface DocumentSource {
    val uri: Uri
    val displayName: String?
    val mimeType: String?
    val sizeBytes: Long?

    suspend fun openInputStream(): InputStream
    suspend fun openFileDescriptor(mode: String = "r"): ParcelFileDescriptor?
}
```

## File: core/files/src/main/kotlin/com/ireader/core/files/source/FileDocumentSource.kt
```kotlin
package com.ireader.core.files.source

import android.net.Uri
import android.os.ParcelFileDescriptor
import com.ireader.core.files.source.DocumentSource
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
```
