package com.ireader.core.work.enrich

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.importing.ImportItemRepo
import com.ireader.core.files.source.FileDocumentSource
import com.ireader.core.files.storage.BookStorage
import com.ireader.core.work.notification.ImportForeground
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.runtime.ReaderRuntime
import com.ireader.reader.runtime.render.RenderDefaults
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
        val density = metrics.density
        val thumbWidth = minOf(720, metrics.widthPixels.coerceAtLeast(480))
        val thumbHeight = (thumbWidth * 4 / 3).coerceAtLeast(720)

        var done = 0
        val total = bookIds.size
        val throttle = ProgressThrottle(minIntervalMs = 500L)

        try {
            setForeground(ImportForeground.info(applicationContext, done, total, "Enriching…"))

            for (bookId in bookIds) {
                currentCoroutineContext().ensureActive()

                val book = bookRepo.getById(bookId) ?: run {
                    done += 1
                    if (throttle.shouldUpdate()) {
                        setForeground(ImportForeground.info(applicationContext, done, total, "Enriching…"))
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
                        setForeground(ImportForeground.info(applicationContext, done, total, book.title ?: "Enriching…"))
                    }
                    continue
                }

                val file = File(book.canonicalPath)
                if (!file.exists()) {
                    done += 1
                    if (throttle.shouldUpdate()) {
                        setForeground(ImportForeground.info(applicationContext, done, total, book.title ?: "Enriching…"))
                    }
                    continue
                }

                runCatching {
                    val source = FileDocumentSource(file, displayName = book.displayName ?: file.name)
                    val documentResult = runtime.openDocument(
                        source = source,
                        options = OpenOptions(hintFormat = book.format)
                    )
                    if (documentResult !is ReaderResult.Ok) {
                        return@runCatching
                    }

                    documentResult.value.use { document ->
                        var newTitle: String? = book.title
                        var newAuthor: String? = book.author
                        var newLanguage: String? = book.language
                        var newIdentifier: String? = book.identifier
                        var coverPath: String? = book.coverPath

                        if (needMeta) {
                            when (val metadataResult = document.metadata()) {
                                is ReaderResult.Ok -> {
                                    val metadata = metadataResult.value
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

                                is ReaderResult.Err -> Unit
                            }
                        }

                        if (needCover) {
                            val initialLocator: Locator? = if (book.format.name == "PDF") {
                                Locator(scheme = LocatorSchemes.PDF_PAGE, value = "0")
                            } else {
                                null
                            }

                            val sessionResult = document.createSession(
                                initialLocator = initialLocator,
                                initialConfig = RenderDefaults.configFor(document.capabilities)
                            )

                            if (sessionResult is ReaderResult.Ok) {
                                sessionResult.value.use { session ->
                                    val constraints = LayoutConstraints(
                                        viewportWidthPx = thumbWidth,
                                        viewportHeightPx = thumbHeight,
                                        density = density,
                                        fontScale = 1.0f
                                    )
                                    session.controller.setLayoutConstraints(constraints)

                                    val pageResult = session.controller.render(
                                        policy = RenderPolicy(
                                            quality = RenderPolicy.Quality.FINAL,
                                            allowCache = false,
                                            prefetchNeighbors = 0
                                        )
                                    )

                                    if (pageResult is ReaderResult.Ok) {
                                        val coverFile = storage.coverFile(book.id)
                                        val fallbackTitle = newTitle ?: book.title ?: book.displayName ?: "Untitled"
                                        val coverBitmap = CoverRenderer.renderCoverBitmap(
                                            page = pageResult.value,
                                            desiredWidth = thumbWidth,
                                            desiredHeight = thumbHeight,
                                            titleFallback = fallbackTitle
                                        )
                                        BitmapIO.savePng(coverFile, coverBitmap)
                                        coverPath = coverFile.absolutePath
                                    }
                                }
                            }
                        }

                        val changed = newTitle != book.title ||
                            newAuthor != book.author ||
                            newLanguage != book.language ||
                            newIdentifier != book.identifier ||
                            coverPath != book.coverPath

                        if (changed) {
                            bookRepo.upsert(
                                book.copy(
                                    title = newTitle,
                                    author = newAuthor,
                                    language = newLanguage,
                                    identifier = newIdentifier,
                                    coverPath = coverPath,
                                    updatedAtEpochMs = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }

                done += 1
                if (throttle.shouldUpdate()) {
                    setForeground(ImportForeground.info(applicationContext, done, total, book.title ?: "Enriching…"))
                }
            }

            throttle.force()
            setForeground(ImportForeground.info(applicationContext, done, total, "Enrich complete"))
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Enrich is best-effort; do not block import main flow.
            Result.success()
        }
    }
}
