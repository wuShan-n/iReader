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
            updateProgress(done, total, "Enriching…")

            for (bookId in bookIds) {
                currentCoroutineContext().ensureActive()

                val book = bookRepo.getById(bookId) ?: run {
                    done += 1
                    if (throttle.shouldUpdate()) {
                        updateProgress(done, total, "Enriching…")
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
                        updateProgress(done, total, book.title ?: "Enriching…")
                    }
                    continue
                }

                val file = File(book.canonicalPath)
                if (!file.exists()) {
                    done += 1
                    if (throttle.shouldUpdate()) {
                        updateProgress(done, total, book.title ?: "Enriching…")
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
                    updateProgress(done, total, book.title ?: "Enriching…")
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
