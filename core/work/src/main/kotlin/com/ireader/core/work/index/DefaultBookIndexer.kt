package com.ireader.core.work.index

import com.ireader.core.data.book.BookIndexer
import com.ireader.core.data.book.IndexState
import com.ireader.core.data.book.BookRepo
import com.ireader.core.database.book.BookEntity
import com.ireader.core.files.source.FileDocumentSource
import com.ireader.core.files.storage.BookStorage
import com.ireader.core.work.enrich.BitmapIO
import com.ireader.core.work.enrich.CoverRenderer
import com.ireader.core.work.enrich.epub.EpubZipEnricher
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.runtime.BookProbeResult
import com.ireader.reader.runtime.ReaderRuntime
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File
import org.json.JSONObject

@Singleton
class DefaultBookIndexer @Inject constructor(
    private val bookRepo: BookRepo,
    private val storage: BookStorage,
    private val runtime: ReaderRuntime
) : BookIndexer {

    override suspend fun index(bookId: Long): Result<Unit> = indexInternal(bookId = bookId, force = false)

    override suspend fun reindex(bookId: Long): Result<Unit> = indexInternal(bookId = bookId, force = true)

    private suspend fun indexInternal(bookId: Long, force: Boolean): Result<Unit> {
        val book = bookRepo.getById(bookId)
            ?: return Result.failure(IllegalStateException("book not found: $bookId"))

        val file = File(book.canonicalPath)
        if (!file.exists()) {
            bookRepo.setIndexState(bookId = bookId, state = IndexState.MISSING, error = "file missing")
            return Result.failure(IllegalStateException("book file missing"))
        }

        val source = FileDocumentSource(
            file = file,
            displayName = book.fileName,
            mimeType = book.mimeType
        )
        val probeResult = runtime.probe(
            source = source,
            options = OpenOptions(hintFormat = book.format)
        )
        if (probeResult !is ReaderResult.Ok) {
            val message = when (probeResult) {
                is ReaderResult.Err -> probeResult.error.toString()
                is ReaderResult.Ok -> null
            }
            bookRepo.setIndexState(bookId = bookId, state = IndexState.ERROR, error = message)
            return Result.failure(IllegalStateException(message ?: "probe failed"))
        }

        val probe = probeResult.value
        val metadata = probe.metadata
        val resolvedTitle = metadataField(
            newValue = metadata?.title,
            oldValue = book.title,
            fallback = fallbackTitle(book.fileName)
        )
        val finalCoverPath = resolveCoverPath(
            book = book,
            probe = probe,
            file = file,
            force = force,
            titleHint = resolvedTitle
        )

        bookRepo.updateMetadata(
            bookId = book.bookId,
            documentId = probe.documentId ?: book.documentId,
            format = probe.format,
            title = resolvedTitle,
            author = metadataField(metadata?.author, book.author, fallback = null),
            language = metadataField(metadata?.language, book.language, fallback = null),
            identifier = metadataField(metadata?.identifier, book.identifier, fallback = null),
            series = metadataField(metadata?.extra?.get("series"), book.series, fallback = null),
            description = metadataField(metadata?.extra?.get("description"), book.description, fallback = null),
            coverPath = finalCoverPath,
            capabilitiesJson = encodeCapabilities(probe.capabilities),
            indexState = IndexState.INDEXED,
            indexError = null
        )
        return Result.success(Unit)
    }

    private fun fallbackTitle(fileName: String): String {
        return fileName.substringBeforeLast('.', fileName).ifBlank { "Untitled" }
    }

    private fun metadataField(newValue: String?, oldValue: String?, fallback: String?): String? {
        val normalizedNew = newValue?.trim().orEmpty()
        if (normalizedNew.isNotEmpty()) {
            return normalizedNew
        }
        val normalizedOld = oldValue?.trim().orEmpty()
        if (normalizedOld.isNotEmpty()) {
            return normalizedOld
        }
        return fallback
    }

    private fun encodeCapabilities(capabilities: DocumentCapabilities?): String? {
        capabilities ?: return null
        return JSONObject()
            .put("reflowable", capabilities.reflowable)
            .put("fixedLayout", capabilities.fixedLayout)
            .put("outline", capabilities.outline)
            .put("search", capabilities.search)
            .put("textExtraction", capabilities.textExtraction)
            .put("annotations", capabilities.annotations)
            .put("links", capabilities.links)
            .toString()
    }

    private fun resolveCoverPath(
        book: BookEntity,
        probe: BookProbeResult,
        file: File,
        force: Boolean,
        titleHint: String?
    ): String? {
        val existing = book.coverPath?.takeIf { path -> path.isNotBlank() && File(path).exists() }
        if (existing != null && !force) {
            return existing
        }

        val coverFile = storage.coverFile(book.bookId)
        val title = titleHint ?: book.title ?: fallbackTitle(book.fileName)

        val extracted = when (probe.format) {
            BookFormat.EPUB -> {
                val coverPathInZip = probe.metadata?.extra?.get("coverPath")
                coverPathInZip?.let { pathInZip ->
                    EpubZipEnricher.tryExtractCoverToPng(
                        file = file,
                        coverPathInZip = pathInZip,
                        outFile = coverFile,
                        reqWidth = 720,
                        reqHeight = 960
                    )
                } ?: false
            }

            BookFormat.PDF,
            BookFormat.TXT -> false
        }

        if (!extracted) {
            val placeholder = CoverRenderer.placeholderBitmap(720, 960, title)
            BitmapIO.savePng(coverFile, placeholder)
        }

        return coverFile.absolutePath
    }
}
