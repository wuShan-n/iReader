# pdf 模块 .kt 代码（不含测试）

## engines/pdf\src\main\kotlin\com\ireader\engines\pdf\di\PdfEngineModule.kt

```kotlin
package com.ireader.engines.pdf.di

import android.content.Context
import com.ireader.engines.pdf.PdfEngine
import com.ireader.engines.pdf.PdfEngineConfig
import com.ireader.engines.pdf.internal.provider.StoredPdfAnnotationProvider
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.provider.AnnotationStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PdfEngineModule {

    @Provides
    @IntoSet
    @Singleton
    fun providePdfEngine(
        @ApplicationContext context: Context,
        annotationStore: AnnotationStore
    ): ReaderEngine {
        return PdfEngine(
            context = context,
            config = PdfEngineConfig(
                annotationProviderFactory = { documentId ->
                    StoredPdfAnnotationProvider(
                        documentId = documentId,
                        store = annotationStore
                    )
                }
            )
        )
    }
}
```

## engines/pdf\src\main\kotlin\com\ireader\engines\pdf\internal\backend\BackendFactory.kt

```kotlin
package com.ireader.engines.pdf.internal.backend

import android.os.ParcelFileDescriptor
import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.pdf.PdfBackendStrategy
import com.ireader.engines.pdf.PdfEngineConfig
import com.ireader.engines.pdf.internal.backend.pdfium.PdfiumBackend
import com.ireader.engines.pdf.internal.backend.platform.PlatformPdfBackend
import com.ireader.engines.pdf.internal.open.OpenedPdf
import com.ireader.engines.pdf.internal.open.PdfOpener
import com.ireader.engines.common.io.closeQuietly
import com.ireader.reader.api.error.ReaderResult
import java.io.Closeable

internal class BackendFactory(
    private val opener: PdfOpener,
    private val config: PdfEngineConfig
) : PdfBackendProvider {
    override suspend fun open(source: DocumentSource, password: String?): ReaderResult<OpenedPdf> {
        val opened = when (val result = opener.open(source)) {
            is ReaderResult.Err -> return result
            is ReaderResult.Ok -> result.value
        }

        val primaryPfd = opened.descriptor
        val tempFile = opened.tempFile

        return try {
            val selected = when (config.backendStrategy) {
                PdfBackendStrategy.PDFIUM_ONLY -> SelectedBackend(
                    backend = openPdfium(primaryPfd, password),
                    degraded = false
                )

                PdfBackendStrategy.PLATFORM_ONLY -> SelectedBackend(
                    backend = openPlatform(primaryPfd),
                    degraded = true
                )

                PdfBackendStrategy.AUTO -> openWithPdfiumFallback(primaryPfd, password)
            }

            val cleanup = Closeable {
                selected.backend.closeQuietly()
                tempFile?.let { file -> runCatching { file.delete() } }
            }
            ReaderResult.Ok(
                OpenedPdf(
                    backend = selected.backend,
                    cleanup = cleanup,
                    degradedBackend = selected.degraded
                )
            )
        } catch (t: Throwable) {
            ReaderResult.Err(
                t.toReaderError(invalidPasswordKeywords = setOf("password", "encrypted"))
            )
        } finally {
            primaryPfd.closeQuietly()
        }
    }

    private suspend fun openWithPdfiumFallback(
        sourceDescriptor: ParcelFileDescriptor,
        password: String?
    ): SelectedBackend {
        val pdfiumAttempt = runCatching { openPdfium(sourceDescriptor, password) }
        if (pdfiumAttempt.isSuccess) {
            return SelectedBackend(
                backend = pdfiumAttempt.getOrThrow(),
                degraded = false
            )
        }

        val platformAttempt = runCatching { openPlatform(sourceDescriptor) }
        if (platformAttempt.isSuccess) {
            return SelectedBackend(
                backend = platformAttempt.getOrThrow(),
                degraded = true
            )
        }

        val pdfiumError = pdfiumAttempt.exceptionOrNull()
        val platformError = platformAttempt.exceptionOrNull()
        if (pdfiumError != null && platformError != null) {
            pdfiumError.addSuppressed(platformError)
        }
        throw pdfiumError ?: platformError ?: IllegalStateException("Unknown PDF backend open error")
    }

    private suspend fun openPdfium(
        sourceDescriptor: ParcelFileDescriptor,
        password: String?
    ): PdfBackend {
        val pfd = ParcelFileDescriptor.dup(sourceDescriptor.fileDescriptor)
        return PdfiumBackend.open(
            descriptor = pfd,
            password = password,
            ioDispatcher = config.ioDispatcher
        )
    }

    private fun openPlatform(sourceDescriptor: ParcelFileDescriptor): PdfBackend {
        val pfd = ParcelFileDescriptor.dup(sourceDescriptor.fileDescriptor)
        return PlatformPdfBackend(
            descriptor = pfd,
            ioDispatcher = config.ioDispatcher
        )
    }

    private data class SelectedBackend(
        val backend: PdfBackend,
        val degraded: Boolean
    )
}
```

## engines/pdf\src\main\kotlin\com\ireader\engines\pdf\internal\backend\PdfBackend.kt

```kotlin
package com.ireader.engines.pdf.internal.backend

import android.graphics.Bitmap
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.OutlineNode
import java.io.Closeable

internal data class PdfPageSize(
    val widthPt: Int,
    val heightPt: Int
)

internal data class PdfBackendCapabilities(
    val outline: Boolean,
    val links: Boolean,
    val textExtraction: Boolean,
    val search: Boolean,
    val preciseRegionRendering: Boolean = true
) {
    fun supportsFullReaderFeatures(): Boolean =
        outline && links && textExtraction && search
}

internal interface PdfBackend : Closeable {
    val capabilities: PdfBackendCapabilities

    suspend fun pageCount(): Int

    suspend fun metadata(): DocumentMetadata

    suspend fun pageSize(pageIndex: Int): PdfPageSize

    suspend fun renderRegion(
        pageIndex: Int,
        bitmap: Bitmap,
        regionLeftPx: Int,
        regionTopPx: Int,
        regionWidthPx: Int,
        regionHeightPx: Int,
        quality: RenderPolicy.Quality
    )

    suspend fun pageLinks(pageIndex: Int): List<DocumentLink>

    suspend fun outline(): List<OutlineNode>

    suspend fun pageText(pageIndex: Int): String?
}
```

## engines/pdf\src\main\kotlin\com\ireader\engines\pdf\internal\backend\PdfBackendProvider.kt

```kotlin
package com.ireader.engines.pdf.internal.backend

import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.pdf.internal.open.OpenedPdf
import com.ireader.reader.api.error.ReaderResult

internal interface PdfBackendProvider {
    suspend fun open(source: DocumentSource, password: String?): ReaderResult<OpenedPdf>
}

```

## engines/pdf\src\main\kotlin\com\ireader\engines\pdf\internal\backend\pdfium\PdfiumBackend.kt

```kotlin
@file:Suppress("TooManyFunctions")

package com.ireader.engines.pdf.internal.backend.pdfium

import android.graphics.Bitmap
import android.graphics.RectF
import android.os.ParcelFileDescriptor
import com.ireader.engines.pdf.internal.backend.PdfBackend
import com.ireader.engines.pdf.internal.backend.PdfBackendCapabilities
import com.ireader.engines.pdf.internal.backend.PdfPageSize
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.LinkTarget
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.NormalizedRect
import com.ireader.reader.model.OutlineNode
import io.legere.pdfiumandroid.api.Bookmark
import io.legere.pdfiumandroid.api.Config
import io.legere.pdfiumandroid.api.Link
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import io.legere.pdfiumandroid.suspend.PdfPageKt
import io.legere.pdfiumandroid.suspend.PdfTextPageKt
import io.legere.pdfiumandroid.suspend.PdfiumCoreKt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.min

internal class PdfiumBackend private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val document: PdfDocumentKt,
    private val ioDispatcher: CoroutineDispatcher
) : PdfBackend {

    override val capabilities: PdfBackendCapabilities = PdfBackendCapabilities(
        outline = true,
        links = true,
        textExtraction = true,
        search = true
    )

    private val pageSizeCache = mutableMapOf<Int, PdfPageSize>()
    private val pageSizeMutex = Mutex()

    private val textCacheMutex = Mutex()
    private val textCache = object : LinkedHashMap<Int, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>): Boolean {
            return size > 16
        }
    }

    override suspend fun pageCount(): Int = document.getPageCount()

    override suspend fun metadata(): DocumentMetadata {
        val meta = document.getDocumentMeta()
        val extra = buildMap {
            meta.subject?.let { put("subject", it) }
            meta.keywords?.let { put("keywords", it) }
            meta.creator?.let { put("creator", it) }
            meta.producer?.let { put("producer", it) }
            meta.creationDate?.let { put("creationDate", it) }
            meta.modDate?.let { put("modDate", it) }
            put("backend", "pdfium")
        }
        return DocumentMetadata(
            title = meta.title,
            author = meta.author,
            language = null,
            identifier = null,
            extra = extra
        )
    }

    override suspend fun pageSize(pageIndex: Int): PdfPageSize {
        val cached = pageSizeMutex.withLock { pageSizeCache[pageIndex] }
        if (cached != null) return cached

        val measured = withPage(pageIndex) { page ->
            PdfPageSize(
                widthPt = page.getPageWidthPoint().coerceAtLeast(1),
                heightPt = page.getPageHeightPoint().coerceAtLeast(1)
            )
        }

        pageSizeMutex.withLock {
            pageSizeCache[pageIndex] = measured
        }
        return measured
    }

    override suspend fun renderRegion(
        pageIndex: Int,
        bitmap: Bitmap,
        regionLeftPx: Int,
        regionTopPx: Int,
        regionWidthPx: Int,
        regionHeightPx: Int,
        quality: RenderPolicy.Quality
    ) {
        withPage(pageIndex) { page ->
            page.renderPageBitmap(
                bitmap = bitmap,
                startX = regionLeftPx,
                startY = regionTopPx,
                drawSizeX = max(1, regionWidthPx),
                drawSizeY = max(1, regionHeightPx),
                renderAnnot = true,
                textMask = false
            )
        }
    }

    override suspend fun pageLinks(pageIndex: Int): List<DocumentLink> {
        val pageSize = pageSize(pageIndex)
        return withPage(pageIndex) { page ->
            page.getPageLinks().mapNotNull { link ->
                link.toDocumentLink(pageSize)
            }
        }
    }

    override suspend fun outline(): List<OutlineNode> {
        return document.getTableOfContents().mapNotNull(::mapBookmark)
    }

    override suspend fun pageText(pageIndex: Int): String? {
        val cached = textCacheMutex.withLock { textCache[pageIndex] }
        if (cached != null) return cached

        val extracted = withPage(pageIndex) { page ->
            withTextPage(page) { textPage ->
                val charCount = textPage.textPageCountChars()
                if (charCount <= 0) {
                    ""
                } else {
                    textPage.textPageGetText(0, charCount).orEmpty().replace("\u0000", "")
                }
            }
        }

        textCacheMutex.withLock {
            textCache[pageIndex] = extracted
        }
        return extracted
    }

    override fun close() {
        runCatching { document.close() }
        runCatching { descriptor.close() }
    }

    private suspend fun <T> withPage(pageIndex: Int, block: suspend (PdfPageKt) -> T): T {
        val page = document.openPage(pageIndex)
            ?: error("Cannot open PDF page: $pageIndex")
        return try {
            block(page)
        } finally {
            runCatching { page.close() }
        }
    }

    private suspend fun <T> withTextPage(page: PdfPageKt, block: suspend (PdfTextPageKt) -> T): T {
        val textPage = page.openTextPage()
        return try {
            block(textPage)
        } finally {
            runCatching { textPage.close() }
        }
    }

    private fun Link.toDocumentLink(size: PdfPageSize): DocumentLink? {
        val uriValue = uri
        val destinationPage = destPageIdx
        val target = when {
            !uriValue.isNullOrBlank() -> LinkTarget.External(uriValue)
            destinationPage != null && destinationPage >= 0 -> LinkTarget.Internal(
                locator = Locator(
                    scheme = LocatorSchemes.PDF_PAGE,
                    value = destinationPage.toString()
                )
            )
            else -> null
        } ?: return null

        val rect = bounds.toNormalized(size.widthPt, size.heightPt)
        return DocumentLink(
            target = target,
            title = null,
            bounds = listOf(rect)
        )
    }

    private fun RectF.toNormalized(pageWidth: Int, pageHeight: Int): NormalizedRect {
        val width = pageWidth.coerceAtLeast(1).toFloat()
        val height = pageHeight.coerceAtLeast(1).toFloat()

        val x0 = (left / width).coerceIn(0f, 1f)
        val x1 = (right / width).coerceIn(0f, 1f)

        val lowY = min(top, bottom)
        val highY = max(top, bottom)
        val yTop = (1f - (highY / height)).coerceIn(0f, 1f)
        val yBottom = (1f - (lowY / height)).coerceIn(0f, 1f)

        return NormalizedRect(
            left = min(x0, x1),
            top = min(yTop, yBottom),
            right = max(x0, x1),
            bottom = max(yTop, yBottom)
        )
    }

    private fun mapBookmark(bookmark: Bookmark): OutlineNode? {
        val title = bookmark.title?.takeIf { it.isNotBlank() } ?: return null
        val page = bookmark.pageIdx.toInt().coerceAtLeast(0)
        return OutlineNode(
            title = title,
            locator = Locator(
                scheme = LocatorSchemes.PDF_PAGE,
                value = page.toString()
            ),
            children = bookmark.children.mapNotNull(::mapBookmark)
        )
    }

    companion object {
        suspend fun open(
            descriptor: ParcelFileDescriptor,
            password: String?,
            ioDispatcher: CoroutineDispatcher
        ): PdfiumBackend {
            val core = PdfiumCoreKt(
                dispatcher = ioDispatcher,
                config = Config()
            )
            val document = core.newDocument(descriptor, password)
            return PdfiumBackend(
                descriptor = descriptor,
                document = document,
                ioDispatcher = ioDispatcher
            )
        }
    }
}
```

## engines/pdf\src\main\kotlin\com\ireader\engines\pdf\internal\backend\platform\PlatformPdfBackend.kt

```kotlin
package com.ireader.engines.pdf.internal.backend.platform

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.ireader.engines.pdf.internal.backend.PdfBackend
import com.ireader.engines.pdf.internal.backend.PdfBackendCapabilities
import com.ireader.engines.pdf.internal.backend.PdfPageSize
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.OutlineNode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PlatformPdfBackend(
    private val descriptor: ParcelFileDescriptor,
    private val ioDispatcher: CoroutineDispatcher
) : PdfBackend {

    override val capabilities: PdfBackendCapabilities = PdfBackendCapabilities(
        outline = false,
        links = false,
        textExtraction = false,
        search = false,
        preciseRegionRendering = false
    )

    private val renderer = PdfRenderer(descriptor)
    private val pageSizeMutex = Mutex()
    private val pageSizeCache = mutableMapOf<Int, PdfPageSize>()

    override suspend fun pageCount(): Int = withContext(ioDispatcher) {
        renderer.pageCount
    }

    override suspend fun metadata(): DocumentMetadata = withContext(ioDispatcher) {
        // PdfRenderer does not expose rich metadata.
        DocumentMetadata(
            title = null,
            author = null,
            language = null,
            identifier = null,
            extra = mapOf("backend" to "platform")
        )
    }

    override suspend fun pageSize(pageIndex: Int): PdfPageSize = withContext(ioDispatcher) {
        val cached = pageSizeMutex.withLock { pageSizeCache[pageIndex] }
        if (cached != null) return@withContext cached

        renderer.openPage(pageIndex).use { page ->
            val measured = PdfPageSize(widthPt = page.width, heightPt = page.height)
            pageSizeMutex.withLock {
                pageSizeCache[pageIndex] = measured
            }
            measured
        }
    }

    override suspend fun renderRegion(
        pageIndex: Int,
        bitmap: Bitmap,
        regionLeftPx: Int,
        regionTopPx: Int,
        regionWidthPx: Int,
        regionHeightPx: Int,
        quality: RenderPolicy.Quality
    ) = withContext(ioDispatcher) {
        renderer.openPage(pageIndex).use { page ->
            // Platform backend is a compatibility fallback; region rendering is approximated.
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
    }

    override suspend fun pageLinks(pageIndex: Int): List<DocumentLink> = emptyList()

    override suspend fun outline(): List<OutlineNode> = emptyList()

    override suspend fun pageText(pageIndex: Int): String? = null

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }
}
```

## engines/pdf\src\main\kotlin\com\ireader\engines\pdf\internal\cache\TileCache.kt

```kotlin
package com.ireader.engines.pdf.internal.cache

import android.graphics.Bitmap
import com.ireader.reader.api.render.RenderPolicy
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class TileCacheKey(
    val pageIndex: Int,
    val leftPx: Int,
    val topPx: Int,
    val widthPx: Int,
    val heightPx: Int,
    val scaleMilli: Int,
    val quality: RenderPolicy.Quality,
    val rotationDegrees: Int,
    val zoomBucketMilli: Int
)

internal class TileCache(
    private val maxBytes: Int
) {
    private data class Entry(
        val bitmap: Bitmap,
        val bytes: Int
    )

    private val mutex = Mutex()
    private var sizeBytes: Int = 0
    private val map = LinkedHashMap<TileCacheKey, Entry>(32, 0.75f, true)

    suspend fun get(key: TileCacheKey): Bitmap? = mutex.withLock {
        map[key]?.bitmap
    }

    suspend fun put(key: TileCacheKey, bitmap: Bitmap) = mutex.withLock {
        val bytes = bitmap.byteCount.coerceAtLeast(0)
        map.put(key, Entry(bitmap, bytes))?.let { prev ->
            sizeBytes -= prev.bytes
            if (prev.bitmap != bitmap && !prev.bitmap.isRecycled) {
                prev.bitmap.recycle()
            }
        }
        sizeBytes += bytes
        trimLocked()
    }

    suspend fun clear() = mutex.withLock {
        map.values.forEach { entry ->
            if (!entry.bitmap.isRecycled) entry.bitmap.recycle()
        }
        map.clear()
        sizeBytes = 0
    }

    private fun trimLocked() {
        while (sizeBytes > maxBytes && map.isNotEmpty()) {
            val iterator = map.entries.iterator()
            if (!iterator.hasNext()) break
            val eldest = iterator.next()
            iterator.remove()
            sizeBytes -= eldest.value.bytes
            if (!eldest.value.bitmap.isRecycled) {
                eldest.value.bitmap.recycle()
            }
        }
    }
}
```

## engines/pdf\src\main\kotlin\com\ireader\engines\pdf\internal\cache\TileInflight.kt

```kotlin
package com.ireader.engines.pdf.internal.cache

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class TileInflight(
    private val scope: CoroutineScope
) {
    private val mutex = Mutex()
    private val inflight = mutableMapOf<TileCacheKey, Deferred<Bitmap>>()

    suspend fun getOrAwait(key: TileCacheKey, block: suspend () -> Bitmap): Bitmap {
        val existing = mutex.withLock { inflight[key] }
        if (existing != null) return existing.await()

        val created = scope.async { block() }
        val actual = mutex.withLock {
            val already = inflight[key]
            if (already != null) {
                created.cancel()
                already
            } else {
                inflight[key] = created
                created
            }
        }

        return try {
            actual.await()
        } finally {
            mutex.withLock {
                if (inflight[key] == actual) {
                    inflight.remove(key)
                }
            }
        }
    }

    suspend fun clear() {
        mutex.withLock {
            inflight.values.forEach { deferred -> deferred.cancel() }
            inflight.clear()
        }
    }
}

```

## engines/pdf\src\main\kotlin\com\ireader\engines\pdf\internal\open\OpenedPdf.kt

```kotlin
package com.ireader.engines.pdf.internal.open

import com.ireader.engines.pdf.internal.backend.PdfBackend
import java.io.Closeable

internal data class OpenedPdf(
    val backend: PdfBackend,
    val cleanup: Closeable,
    val degradedBackend: Boolean
)
```

## engines/pdf\src\main\kotlin\com\ireader\engines\pdf\internal\open\PdfCacheFileStore.kt

```kotlin
package com.ireader.engines.pdf.internal.open

import android.content.Context
import android.net.Uri
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal class PdfCacheFileStore(
    context: Context
) {
    private val rootDir = File(context.cacheDir, "pdf-engine").apply { mkdirs() }

    fun fileFor(uri: Uri): File {
        val name = sha256Hex(uri.toString()).take(24)
        return File(rootDir, "$name.pdf")
    }

    private fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        return buildString(bytes.size * 2) {
            bytes.forEach { value ->
                append("%02x".format(value))
            }
        }
    }
}
```

## engines/pdf\src\main\kotlin\com\ireader\engines\pdf\internal\open\PdfDocument.kt

```kotlin
package com.ireader.engines.pdf.internal.open

import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.pdf.PdfEngineConfig
import com.ireader.engines.pdf.internal.provider.InMemoryPdfAnnotationProvider
import com.ireader.engines.pdf.internal.provider.PdfOutlineProvider
import com.ireader.engines.pdf.internal.provider.PdfSelectionManager
import com.ireader.engines.pdf.internal.provider.PdfSearchProvider
import com.ireader.engines.pdf.internal.provider.PdfTextProvider
import com.ireader.engines.pdf.internal.render.PdfController
import com.ireader.engines.pdf.internal.session.PdfSession
import com.ireader.engines.pdf.internal.util.toPdfPageIndexOrNull
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.sanitized
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.Locator
import com.ireader.reader.model.SessionId
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.withContext

internal class PdfDocument(
    override val id: DocumentId,
    private val source: DocumentSource,
    private val openedPdf: OpenedPdf,
    private val pageCount: Int,
    private val engineConfig: PdfEngineConfig,
    override val openOptions: OpenOptions
) : ReaderDocument {

    override val format: BookFormat = BookFormat.PDF

    override val capabilities: DocumentCapabilities = DocumentCapabilities(
        reflowable = false,
        fixedLayout = true,
        outline = openedPdf.backend.capabilities.outline,
        search = openedPdf.backend.capabilities.search,
        textExtraction = openedPdf.backend.capabilities.textExtraction,
        annotations = true,
        selection = openedPdf.backend.capabilities.textExtraction,
        links = openedPdf.backend.capabilities.links
    )

    private val closed = AtomicBoolean(false)

    override suspend fun metadata(): ReaderResult<DocumentMetadata> = withContext(engineConfig.ioDispatcher) {
        runCatching {
            val backendMetadata = openedPdf.backend.metadata()
            val defaultTitle = source.displayName?.substringBeforeLast('.')
            val extra = backendMetadata.extra + mapOf(
                "degradedBackend" to openedPdf.degradedBackend.toString()
            )
            backendMetadata.copy(
                title = backendMetadata.title ?: defaultTitle,
                extra = extra
            )
        }.fold(
            onSuccess = { ReaderResult.Ok(it) },
            onFailure = { ReaderResult.Err(it.toReaderError()) }
        )
    }

    override suspend fun createSession(
        initialLocator: Locator?,
        initialConfig: RenderConfig
    ): ReaderResult<ReaderSession> = withContext(engineConfig.ioDispatcher) {
        if (closed.get()) {
            return@withContext ReaderResult.Err(ReaderError.Internal("PDF document already closed"))
        }

        val fixedConfig = (initialConfig as? RenderConfig.FixedPage ?: RenderConfig.FixedPage()).sanitized()
        val initialPage = initialLocator?.toPdfPageIndexOrNull(pageCount) ?: 0

        val annotationProvider = engineConfig.annotationProviderFactory?.invoke(id)
            ?: InMemoryPdfAnnotationProvider(documentId = id)

        val textProvider = if (capabilities.textExtraction) {
            PdfTextProvider(
                backend = openedPdf.backend,
                pageCount = pageCount
            )
        } else {
            null
        }
        val selectionManager = if (capabilities.selection && textProvider != null) {
            PdfSelectionManager(
                pageCount = pageCount,
                textProvider = textProvider
            )
        } else {
            null
        }

        val controller = PdfController(
            backend = openedPdf.backend,
            pageCount = pageCount,
            initialPageIndex = initialPage,
            initialConfig = fixedConfig,
            annotationProvider = annotationProvider,
            engineConfig = engineConfig
        )

        ReaderResult.Ok(
            PdfSession(
                id = SessionId(UUID.randomUUID().toString()),
                controller = controller,
                outline = if (capabilities.outline) PdfOutlineProvider(openedPdf.backend) else null,
                search = if (capabilities.search && textProvider != null) {
                    PdfSearchProvider(pageCount = pageCount, textProvider = textProvider)
                } else {
                    null
                },
                text = textProvider,
                annotations = annotationProvider,
                selection = selectionManager,
                selectionController = selectionManager
            )
        )
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { openedPdf.cleanup.close() }
    }
}
```

## engines/pdf\src\main\kotlin\com\ireader\engines\pdf\internal\open\PdfOpener.kt

```kotlin
package com.ireader.engines.pdf.internal.open

import android.content.Context
import android.os.ParcelFileDescriptor
import com.ireader.core.files.source.DocumentSource
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class PdfOpener(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher
) {
    private val cacheStore = PdfCacheFileStore(context)

    suspend fun open(source: DocumentSource): ReaderResult<OpenedPdfSource> = withContext(ioDispatcher) {
        runCatching {
            val directPfd = source.openFileDescriptor("r")
            if (directPfd != null) {
                return@runCatching OpenedPdfSource(
                    descriptor = directPfd,
                    tempFile = null
                )
            }

            val cacheFile = copyToCache(source)
            val pfd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
            OpenedPdfSource(
                descriptor = pfd,
                tempFile = cacheFile
            )
        }.fold(
            onSuccess = { ReaderResult.Ok(it) },
            onFailure = { throwable ->
                ReaderResult.Err(
                    when (throwable) {
                        is SecurityException -> ReaderError.PermissionDenied(cause = throwable)
                        is IOException -> ReaderError.Io(cause = throwable)
                        else -> ReaderError.Internal(cause = throwable)
                    }
                )
            }
        )
    }

    private suspend fun copyToCache(source: DocumentSource): File {
        val target = cacheStore.fileFor(source.uri)
        val parent = target.parentFile ?: throw IOException("Invalid cache path: ${target.absolutePath}")
        val temp = File(parent, "${target.name}.tmp-${UUID.randomUUID()}")
        try {
            source.openInputStream().use { input ->
                FileOutputStream(temp).use { output ->
                    input.copyTo(output)
                    output.flush()
                    output.fd.sync()
                }
            }
            if (target.exists() && !target.delete()) {
                throw IOException("Failed to replace cached PDF: ${target.absolutePath}")
            }
            if (!temp.renameTo(target)) {
                throw IOException("Failed to finalize cached PDF: ${target.absolutePath}")
            }
            return target
        } catch (t: Throwable) {
            runCatching { temp.delete() }
            throw t
        }
    }
}

internal data class OpenedPdfSource(
    val descriptor: ParcelFileDescriptor,
    val tempFile: File?
)
```

## engines/pdf\src\main\kotlin\com\ireader\engines\pdf\internal\provider\PdfAnnotationProvider.kt

```kotlin
package com.ireader.engines.pdf.internal.provider

import com.ireader.reader.api.annotation.Decoration
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.AnnotationQuery
import com.ireader.reader.api.provider.AnnotationStore
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.annotation.Annotation
import com.ireader.reader.model.annotation.AnnotationAnchor
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class InMemoryPdfAnnotationProvider(
    private val documentId: DocumentId
) : AnnotationProvider {

    private val state = MutableStateFlow<List<Annotation>>(emptyList())
    private var pageIndex: Map<String, List<Annotation>> = emptyMap()

    override fun observeAll(): Flow<List<Annotation>> = state.asStateFlow()

    override suspend fun listAll(): ReaderResult<List<Annotation>> = ReaderResult.Ok(state.value)

    override suspend fun query(query: AnnotationQuery): ReaderResult<List<Annotation>> {
        val all = state.value
        val pageQuery = query.page
        val rangeQuery = query.range
        val filtered = when {
            pageQuery != null -> pageIndex[pageQuery.pageKey()].orEmpty()
            rangeQuery != null -> all.filter { ann -> ann.matchesRange(rangeQuery.start, rangeQuery.end) }
            else -> all
        }
        return ReaderResult.Ok(filtered)
    }

    override suspend fun create(draft: AnnotationDraft): ReaderResult<Annotation> {
        val now = System.currentTimeMillis()
        val created = Annotation(
            id = AnnotationId("${documentId.value}:${UUID.randomUUID()}"),
            type = draft.type,
            anchor = draft.anchor,
            content = draft.content,
            style = draft.style,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            extra = draft.extra
        )
        updateState(state.value + created)
        return ReaderResult.Ok(created)
    }

    override suspend fun update(annotation: Annotation): ReaderResult<Unit> {
        updateState(state.value.map {
            if (it.id == annotation.id) annotation.copy(updatedAtEpochMs = System.currentTimeMillis()) else it
        })
        return ReaderResult.Ok(Unit)
    }

    override suspend fun delete(id: AnnotationId): ReaderResult<Unit> {
        updateState(state.value.filterNot { it.id == id })
        return ReaderResult.Ok(Unit)
    }

    override suspend fun decorationsFor(query: AnnotationQuery): ReaderResult<List<Decoration>> {
        val items = when (val q = query(query)) {
            is ReaderResult.Ok -> q.value
            is ReaderResult.Err -> return ReaderResult.Err(q.error)
        }
        return ReaderResult.Ok(
            items.mapNotNull { ann ->
                when (val anchor = ann.anchor) {
                    is AnnotationAnchor.FixedRects -> Decoration.Fixed(
                        page = anchor.page,
                        rects = anchor.rects,
                        style = ann.style
                    )

                    is AnnotationAnchor.ReflowRange -> Decoration.Reflow(
                        range = anchor.range,
                        style = ann.style
                    )
                }
            }
        )
    }

    private fun updateState(next: List<Annotation>) {
        state.value = next
        pageIndex = next.groupBy { annotation ->
            when (val anchor = annotation.anchor) {
                is AnnotationAnchor.FixedRects -> anchor.page.pageKey()
                is AnnotationAnchor.ReflowRange -> anchor.range.start.pageKey()
            }
        }
    }

    private fun Annotation.matchesPage(page: Locator): Boolean {
        return when (val anchor = anchor) {
            is AnnotationAnchor.FixedRects -> anchor.page.samePdfPage(page)
            is AnnotationAnchor.ReflowRange -> anchor.range.start.samePdfPage(page)
        }
    }

    private fun Annotation.matchesRange(start: Locator, end: Locator): Boolean {
        return when (val anchor = anchor) {
            is AnnotationAnchor.FixedRects -> anchor.page.samePdfPage(start)
            is AnnotationAnchor.ReflowRange -> {
                anchor.range.start.samePdfPage(start) && anchor.range.end.samePdfPage(end)
            }
        }
    }

    private fun Locator.samePdfPage(other: Locator): Boolean {
        if (scheme != LocatorSchemes.PDF_PAGE || other.scheme != LocatorSchemes.PDF_PAGE) return false
        return value == other.value
    }

    private fun Locator.pageKey(): String = "$scheme:$value"
}

internal class StoredPdfAnnotationProvider(
    private val documentId: DocumentId,
    private val store: AnnotationStore
) : AnnotationProvider {

    override fun observeAll(): Flow<List<Annotation>> = store.observe(documentId)

    override suspend fun listAll(): ReaderResult<List<Annotation>> = store.list(documentId)

    override suspend fun query(query: AnnotationQuery): ReaderResult<List<Annotation>> =
        store.query(documentId, query)

    override suspend fun create(draft: AnnotationDraft): ReaderResult<Annotation> =
        store.create(documentId, draft)

    override suspend fun update(annotation: Annotation): ReaderResult<Unit> =
        store.update(documentId, annotation)

    override suspend fun delete(id: AnnotationId): ReaderResult<Unit> =
        store.delete(documentId, id)

    override suspend fun decorationsFor(query: AnnotationQuery): ReaderResult<List<Decoration>> {
        val items = when (val result = store.query(documentId, query)) {
            is ReaderResult.Err -> return result
            is ReaderResult.Ok -> result.value
        }
        return ReaderResult.Ok(
            items.map { ann ->
                when (val anchor = ann.anchor) {
                    is AnnotationAnchor.FixedRects -> Decoration.Fixed(
                        page = anchor.page,
                        rects = anchor.rects,
                        style = ann.style
                    )

                    is AnnotationAnchor.ReflowRange -> Decoration.Reflow(
                        range = anchor.range,
                        style = ann.style
                    )
                }
            }
        )
    }
}
```

## engines/pdf\src\main\kotlin\com\ireader\engines\pdf\internal\provider\PdfOutlineProvider.kt

```kotlin
package com.ireader.engines.pdf.internal.provider

import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.pdf.internal.backend.PdfBackend
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.model.OutlineNode

internal class PdfOutlineProvider(
    private val backend: PdfBackend
) : OutlineProvider {
    @Volatile
    private var cached: List<OutlineNode>? = null

    override suspend fun getOutline(): ReaderResult<List<OutlineNode>> {
        cached?.let { return ReaderResult.Ok(it) }
        return runCatching { backend.outline() }
            .fold(
                onSuccess = {
                    cached = it
                    ReaderResult.Ok(it)
                },
                onFailure = { ReaderResult.Err(it.toReaderError()) }
            )
    }
}
```

## engines/pdf\src\main\kotlin\com\ireader\engines\pdf\internal\provider\PdfSearchProvider.kt

```kotlin
package com.ireader.engines.pdf.internal.provider

import com.ireader.reader.api.provider.SearchHit
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.model.LocatorRange
import com.ireader.engines.pdf.internal.util.endCharLocator
import com.ireader.engines.pdf.internal.util.startCharLocator
import com.ireader.engines.pdf.internal.util.toPdfPageIndexOrNull
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class PdfSearchProvider(
    private val pageCount: Int,
    private val textProvider: PdfTextProvider
) : SearchProvider {

    override fun search(query: String, options: SearchOptions): Flow<SearchHit> = flow {
        val q = query.trim()
        if (q.isEmpty()) return@flow

        val ctx = currentCoroutineContext()
        val startPage = options.startFrom?.toPdfPageIndexOrNull(pageCount) ?: 0
        val needle = if (options.caseSensitive) q else q.lowercase(Locale.ROOT)
        var emitted = 0

        for (pageIndex in startPage until pageCount) {
            ctx.ensureActive()
            val pageText = textProvider.pageText(pageIndex).orEmpty()
            if (pageText.isEmpty()) continue

            val haystack = if (options.caseSensitive) pageText else pageText.lowercase(Locale.ROOT)
            var fromIndex = 0

            while (true) {
                ctx.ensureActive()
                val idx = haystack.indexOf(needle, startIndex = fromIndex)
                if (idx < 0) break
                val end = idx + needle.length
                if (options.wholeWord && !isWholeWord(haystack, idx, end)) {
                    fromIndex = end
                    continue
                }

                val excerpt = excerpt(pageText, idx, end)
                val startLocator = startCharLocator(
                    pageIndex = pageIndex,
                    pageCount = pageCount,
                    charStart = idx
                )
                val endLocator = endCharLocator(
                    pageIndex = pageIndex,
                    pageCount = pageCount,
                    charEnd = end
                )

                emit(
                    SearchHit(
                        range = LocatorRange(start = startLocator, end = endLocator),
                        excerpt = excerpt
                    )
                )
                emitted++
                if (emitted >= options.maxHits) return@flow
                fromIndex = end
            }
        }
    }

    private fun isWholeWord(text: String, start: Int, end: Int): Boolean {
        fun isWord(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
        val leftOk = start == 0 || !isWord(text[start - 1])
        val rightOk = end >= text.length || !isWord(text[end])
        return leftOk && rightOk
    }

    private fun excerpt(text: String, start: Int, end: Int): String {
        val left = (start - 40).coerceAtLeast(0)
        val right = (end + 40).coerceAtMost(text.length)
        return text.substring(left, right)
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
    }
}
```

## engines/pdf\src\main\kotlin\com\ireader\engines\pdf\internal\provider\PdfSelectionManager.kt

```kotlin
@file:Suppress("TooGenericExceptionCaught")

package com.ireader.engines.pdf.internal.provider

import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.pdf.internal.util.pageLocator
import com.ireader.engines.pdf.internal.util.toPdfPageIndexOrNull
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.SelectionController
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.model.NormalizedRect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PdfSelectionManager(
    private val pageCount: Int,
    private val textProvider: PdfTextProvider?
) : SelectionProvider, SelectionController {

    private val mutex = Mutex()
    private var current: SelectionProvider.Selection? = null

    override suspend fun currentSelection(): ReaderResult<SelectionProvider.Selection?> {
        return ReaderResult.Ok(mutex.withLock { current })
    }

    override suspend fun clearSelection(): ReaderResult<Unit> = clear()

    override suspend fun start(locator: com.ireader.reader.model.Locator): ReaderResult<Unit> {
        return update(locator)
    }

    override suspend fun update(locator: com.ireader.reader.model.Locator): ReaderResult<Unit> {
        val pageIndex = locator.toPdfPageIndexOrNull(pageCount)
            ?: return ReaderResult.Err(ReaderError.Internal("Invalid PDF locator: $locator"))
        return runCatching {
            val pageLocator = pageLocator(pageIndex = pageIndex, pageCount = pageCount)
            val selectedText = textProvider?.pageText(pageIndex)?.takeIf { it.isNotBlank() }
            val fullPageRect = NormalizedRect(0f, 0f, 1f, 1f)
            val next = SelectionProvider.Selection(
                locator = pageLocator,
                bounds = fullPageRect,
                selectedText = selectedText,
                rects = listOf(fullPageRect),
                extras = locator.extras
            )
            mutex.withLock {
                current = next
            }
            ReaderResult.Ok(Unit)
        }.getOrElse {
            ReaderResult.Err(it.toReaderError())
        }
    }

    override suspend fun finish(): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun clear(): ReaderResult<Unit> {
        return mutex.withLock {
            current = null
            ReaderResult.Ok(Unit)
        }
    }
}
```

## engines/pdf\src\main\kotlin\com\ireader\engines\pdf\internal\provider\PdfTextProvider.kt

```kotlin
package com.ireader.engines.pdf.internal.provider

import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.pdf.internal.backend.PdfBackend
import com.ireader.engines.pdf.internal.util.charEndOrDefault
import com.ireader.engines.pdf.internal.util.charIndexOrNull
import com.ireader.engines.pdf.internal.util.charStartOrDefault
import com.ireader.engines.pdf.internal.util.toPdfPageIndexOrNull
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import kotlin.math.max
import kotlin.math.min

internal class PdfTextProvider(
    private val backend: PdfBackend,
    private val pageCount: Int
    ) : TextProvider {

    override suspend fun getText(range: LocatorRange): ReaderResult<String> {
        val startPage = range.start.toPdfPageIndexOrNull(pageCount)
            ?: return ReaderResult.Err(ReaderError.Internal("Invalid PDF start locator"))
        val endPage = range.end.toPdfPageIndexOrNull(pageCount)
            ?: return ReaderResult.Err(ReaderError.Internal("Invalid PDF end locator"))

        val from = min(startPage, endPage)
        val to = max(startPage, endPage)

        return runCatching {
            val parts = buildList {
                for (page in from..to) {
                    val text = backend.pageText(page).orEmpty()
                    val sliced = when {
                        page == startPage && page == endPage -> {
                            val start = range.start.charStartOrDefault(defaultValue = 0)
                            val end = range.end.charEndOrDefault(defaultValue = text.length)
                            safeSubstring(text, start, end)
                        }

                        page == startPage -> {
                            val start = range.start.charStartOrDefault(defaultValue = 0)
                            safeSubstring(text, start, text.length)
                        }

                        page == endPage -> {
                            val end = range.end.charEndOrDefault(defaultValue = text.length)
                            safeSubstring(text, 0, end)
                        }

                        else -> text
                    }
                    add(sliced)
                }
            }
            parts.joinToString(separator = "\n")
        }.fold(
            onSuccess = { ReaderResult.Ok(it) },
            onFailure = { ReaderResult.Err(it.toReaderError()) }
        )
    }

    override suspend fun getTextAround(locator: Locator, maxChars: Int): ReaderResult<String> {
        val pageIndex = locator.toPdfPageIndexOrNull(pageCount)
            ?: return ReaderResult.Err(ReaderError.Internal("Invalid PDF locator"))
        return runCatching {
            val text = backend.pageText(pageIndex).orEmpty()
            if (text.isEmpty()) return@runCatching ""

            val center = locator.charIndexOrNull()
                ?: locator.charStartOrDefault(defaultValue = -1).takeIf { it >= 0 }
                ?: (text.length / 2)

            val half = (maxChars.coerceAtLeast(1) / 2).coerceAtLeast(1)
            val start = (center - half).coerceAtLeast(0)
            val end = (center + half).coerceAtMost(text.length)
            safeSubstring(text, start, end)
        }.fold(
            onSuccess = { ReaderResult.Ok(it) },
            onFailure = { ReaderResult.Err(it.toReaderError()) }
        )
    }

    suspend fun pageText(pageIndex: Int): String? = backend.pageText(pageIndex)

    private fun safeSubstring(text: String, startInclusive: Int, endExclusive: Int): String {
        if (text.isEmpty()) return ""
        val safeStart = startInclusive.coerceIn(0, text.length)
        val safeEnd = endExclusive.coerceIn(safeStart, text.length)
        return text.substring(safeStart, safeEnd)
    }
}
```

## engines/pdf\src\main\kotlin\com\ireader\engines\pdf\internal\render\FitTransform.kt

```kotlin
package com.ireader.engines.pdf.internal.render

import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import kotlin.math.min
import kotlin.math.roundToInt

internal data class PageTransform(
    val pageWidthPx: Int,
    val pageHeightPx: Int,
    val scale: Float
)

internal fun computePageTransform(
    pageWidthPt: Int,
    pageHeightPt: Int,
    config: RenderConfig.FixedPage,
    constraints: LayoutConstraints
): PageTransform {
    val rotated = normalizeByRotation(
        pageWidth = pageWidthPt.coerceAtLeast(1),
        pageHeight = pageHeightPt.coerceAtLeast(1),
        rotation = config.rotationDegrees
    )

    val baseScale = when (config.fitMode) {
        RenderConfig.FitMode.FIT_WIDTH -> {
            constraints.viewportWidthPx.toFloat() / rotated.first.toFloat()
        }

        RenderConfig.FitMode.FIT_HEIGHT -> {
            constraints.viewportHeightPx.toFloat() / rotated.second.toFloat()
        }

        RenderConfig.FitMode.FIT_PAGE -> {
            min(
                constraints.viewportWidthPx.toFloat() / rotated.first.toFloat(),
                constraints.viewportHeightPx.toFloat() / rotated.second.toFloat()
            )
        }

        RenderConfig.FitMode.FREE -> 1f
    }.coerceAtLeast(0.01f)

    val finalScale = (baseScale * config.zoom.coerceAtLeast(0.1f)).coerceAtLeast(0.01f)
    return PageTransform(
        pageWidthPx = (rotated.first * finalScale).roundToInt().coerceAtLeast(1),
        pageHeightPx = (rotated.second * finalScale).roundToInt().coerceAtLeast(1),
        scale = finalScale
    )
}

internal fun zoomBucketMilli(zoom: Float): Int {
    val clamped = zoom.coerceIn(0.1f, 8f)
    return (clamped * 1000f).roundToInt()
}

private fun normalizeByRotation(pageWidth: Int, pageHeight: Int, rotation: Int): Pair<Int, Int> {
    val normalized = ((rotation % 360) + 360) % 360
    return if (normalized == 90 || normalized == 270) {
        pageHeight to pageWidth
    } else {
        pageWidth to pageHeight
    }
}

```

## engines/pdf\src\main\kotlin\com\ireader\engines\pdf\internal\render\PdfController.kt

```kotlin
@file:Suppress("LongParameterList")

package com.ireader.engines.pdf.internal.render

import android.graphics.Bitmap
import com.ireader.engines.common.android.controller.BaseCoroutineReaderController
import com.ireader.engines.pdf.PdfEngineConfig
import com.ireader.engines.pdf.internal.backend.PdfBackend
import com.ireader.engines.pdf.internal.cache.TileCache
import com.ireader.engines.pdf.internal.cache.TileInflight
import com.ireader.engines.pdf.internal.util.pageLocator
import com.ireader.engines.pdf.internal.util.progressionForPage
import com.ireader.engines.pdf.internal.util.toPdfPageIndexOrNull
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.error.getOrNull
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.AnnotationQuery
import com.ireader.reader.api.render.InvalidateReason
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.NavigationAvailability
import com.ireader.reader.api.render.PageId
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderMetrics
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.api.render.RenderSurface
import com.ireader.reader.api.render.RenderState
import com.ireader.reader.api.render.TileRequest
import com.ireader.reader.api.render.sanitized
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.NormalizedRect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlin.math.min
import kotlin.math.roundToInt

internal class PdfController(
    private val backend: PdfBackend,
    private val pageCount: Int,
    initialPageIndex: Int,
    initialConfig: RenderConfig.FixedPage,
    private val annotationProvider: AnnotationProvider?,
    private val engineConfig: PdfEngineConfig
) : BaseCoroutineReaderController(
    initialState = RenderState(
        locator = pageLocator(
            initialPageIndex.coerceIn(0, pageCount.coerceAtLeast(1) - 1),
            pageCount
        ),
        progression = progressionForPage(
            initialPageIndex.coerceIn(0, pageCount.coerceAtLeast(1) - 1),
            pageCount
        ),
        nav = NavigationAvailability(
            canGoPrev = initialPageIndex.coerceIn(0, pageCount.coerceAtLeast(1) - 1) > 0,
            canGoNext = initialPageIndex.coerceIn(0, pageCount.coerceAtLeast(1) - 1) < pageCount - 1
        ),
        config = initialConfig
    ),
    dispatcher = engineConfig.renderDispatcher
) {
    private val tileCache = TileCache(engineConfig.tileCacheMaxBytes)
    private val tileInflight = TileInflight(scope)
    private var activeTileProvider: PdfTileProvider? = null
    private var prefetchJob: Job? = null

    private var currentPage = initialPageIndex.coerceIn(0, pageCount.coerceAtLeast(1) - 1)
    private var currentConfig = initialConfig
    private var constraints: LayoutConstraints? = null

    override suspend fun bindSurface(surface: RenderSurface): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun unbindSurface(): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit> {
        return mutex.withLock {
            this.constraints = constraints
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> {
        val fixed = config as? RenderConfig.FixedPage
            ?: return ReaderResult.Err(ReaderError.Internal("PDF requires RenderConfig.FixedPage"))
        return mutex.withLock {
            val sanitized = fixed.sanitized()
            currentConfig = sanitized
            stateMutable.value = stateMutable.value.copy(config = sanitized)
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> {
        val result = mutex.withLock { renderLocked(policy) }
        schedulePrefetch(policy.prefetchNeighbors)
        return result
    }

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> {
        return mutex.withLock {
            if (currentPage < pageCount - 1) currentPage++
            renderLocked(policy)
        }.also { schedulePrefetch(policy.prefetchNeighbors) }
    }

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> {
        return mutex.withLock {
            if (currentPage > 0) currentPage--
            renderLocked(policy)
        }.also { schedulePrefetch(policy.prefetchNeighbors) }
    }

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> {
        val page = locator.toPdfPageIndexOrNull(pageCount)
            ?: return ReaderResult.Err(ReaderError.Internal("Unsupported locator for PDF: ${locator.scheme}"))
        return mutex.withLock {
            currentPage = page
            renderLocked(policy)
        }.also { schedulePrefetch(policy.prefetchNeighbors) }
    }

    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> {
        val page = ((pageCount - 1).coerceAtLeast(0) * percent.coerceIn(0.0, 1.0))
            .toInt()
            .coerceIn(0, pageCount.coerceAtLeast(1) - 1)
        return mutex.withLock {
            currentPage = page
            renderLocked(policy)
        }.also { schedulePrefetch(policy.prefetchNeighbors) }
    }

    override suspend fun prefetchNeighbors(count: Int): ReaderResult<Unit> {
        if (count <= 0) return ReaderResult.Ok(Unit)
        return mutex.withLock {
            val layout = constraints ?: return@withLock ReaderResult.Ok(Unit)
            val start = (currentPage - count).coerceAtLeast(0)
            val end = (currentPage + count).coerceAtMost(pageCount - 1)
            for (page in start..end) {
                if (page == currentPage) continue
                runCatching { prewarmNeighborPageLocked(page, layout) }
            }
            ReaderResult.Ok(Unit)
        }
    }

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> {
        return mutex.withLock {
            runCatching { activeTileProvider?.close() }
            activeTileProvider = null
            runCatching { runBlocking { tileCache.clear() } }
            ReaderResult.Ok(Unit)
        }
    }

    override fun onClose() {
        prefetchJob?.cancel()
        runCatching { activeTileProvider?.close() }
        activeTileProvider = null
        runCatching { runBlocking { tileInflight.clear() } }
        runCatching { runBlocking { tileCache.clear() } }
    }

    private suspend fun renderLocked(policy: RenderPolicy): ReaderResult<RenderPage> {
        val layout = constraints
            ?: return ReaderResult.Err(ReaderError.Internal("LayoutConstraints not set"))

        val startTimeNs = System.nanoTime()

        val pageSize = runCatching { backend.pageSize(currentPage) }
            .getOrElse { return ReaderResult.Err(ReaderError.Internal("Failed to read page size", it)) }

        val transform = computePageTransform(
            pageWidthPt = pageSize.widthPt,
            pageHeightPt = pageSize.heightPt,
            config = currentConfig,
            constraints = layout
        )

        val pageLocator = pageLocator(currentPage, pageCount)
        val links = if (backend.capabilities.links) {
            runCatching { backend.pageLinks(currentPage) }
                .getOrDefault(emptyList())
                .rotateBy(currentConfig.rotationDegrees)
        } else {
            emptyList()
        }
        val decorations = annotationProvider
            ?.decorationsFor(AnnotationQuery(page = pageLocator))
            ?.getOrNull()
            ?: emptyList()

        runCatching { activeTileProvider?.close() }
        activeTileProvider = null

        val content = runCatching {
            if (backend.capabilities.preciseRegionRendering) {
                val tileProvider = PdfTileProvider(
                    pageIndex = currentPage,
                    renderConfig = currentConfig,
                    backend = backend,
                    cache = tileCache,
                    inflight = tileInflight,
                    config = engineConfig,
                    scope = CoroutineScope(SupervisorJob() + engineConfig.renderDispatcher)
                )
                activeTileProvider = tileProvider
                RenderContent.Tiles(
                    pageWidthPx = transform.pageWidthPx,
                    pageHeightPx = transform.pageHeightPx,
                    baseTileSizePx = engineConfig.tileBaseSizePx,
                    tileProvider = tileProvider
                )
            } else {
                RenderContent.BitmapPage(
                    bitmap = renderSingleBitmapPage(
                        pageIndex = currentPage,
                        widthPx = transform.pageWidthPx,
                        heightPx = transform.pageHeightPx,
                        quality = policy.quality
                    )
                )
            }
        }.getOrElse {
            return ReaderResult.Err(ReaderError.Internal("Failed to render PDF content", it))
        }
        val elapsedMs = ((System.nanoTime() - startTimeNs) / 1_000_000L).coerceAtLeast(0L)

        val page = RenderPage(
            id = PageId(
                buildString {
                    append("pdf:")
                    append(currentPage)
                    append(':')
                    append(transform.pageWidthPx)
                    append('x')
                    append(transform.pageHeightPx)
                    append(':')
                    append(currentConfig.zoom)
                    append(':')
                    append(currentConfig.rotationDegrees)
                }
            ),
            locator = pageLocator,
            content = content,
            links = links,
            decorations = decorations,
            metrics = RenderMetrics(
                renderTimeMs = elapsedMs,
                cacheHit = false
            )
        )

        updateStateLocked()
        eventsMutable.tryEmit(ReaderEvent.PageChanged(pageLocator))
        eventsMutable.tryEmit(ReaderEvent.Rendered(page.id, page.metrics))
        return ReaderResult.Ok(page)
    }

    private suspend fun renderSingleBitmapPage(
        pageIndex: Int,
        widthPx: Int,
        heightPx: Int,
        quality: RenderPolicy.Quality
    ): Bitmap {
        val safeWidth = widthPx.coerceAtLeast(1)
        val safeHeight = heightPx.coerceAtLeast(1)
        val maxEdge = 4096
        val maxSide = maxOf(safeWidth, safeHeight)
        val scale = if (maxSide <= maxEdge) 1f else maxEdge.toFloat() / maxSide.toFloat()
        val outputWidth = (safeWidth * scale).roundToInt().coerceAtLeast(1)
        val outputHeight = (safeHeight * scale).roundToInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        backend.renderRegion(
            pageIndex = pageIndex,
            bitmap = bitmap,
            regionLeftPx = 0,
            regionTopPx = 0,
            regionWidthPx = outputWidth,
            regionHeightPx = outputHeight,
            quality = quality
        )
        return bitmap
    }

    private fun updateStateLocked() {
        stateMutable.value = stateMutable.value.copy(
            locator = pageLocator(currentPage, pageCount),
            progression = progressionForPage(currentPage, pageCount),
            nav = NavigationAvailability(
                canGoPrev = currentPage > 0,
                canGoNext = currentPage < pageCount - 1
            ),
            config = currentConfig
        )
    }

    private fun schedulePrefetch(count: Int) {
        if (count <= 0) return
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            prefetchNeighbors(count)
        }
    }

    private suspend fun prewarmNeighborPageLocked(pageIndex: Int, layout: LayoutConstraints) {
        val pageSize = backend.pageSize(pageIndex)
        if (!backend.capabilities.preciseRegionRendering) {
            return
        }
        val transform = computePageTransform(
            pageWidthPt = pageSize.widthPt,
            pageHeightPt = pageSize.heightPt,
            config = currentConfig,
            constraints = layout
        )
        val tileProvider = PdfTileProvider(
            pageIndex = pageIndex,
            renderConfig = currentConfig,
            backend = backend,
            cache = tileCache,
            inflight = tileInflight,
            config = engineConfig,
            scope = CoroutineScope(SupervisorJob() + engineConfig.renderDispatcher)
        )
        try {
            val tileSize = engineConfig.tileBaseSizePx.coerceAtLeast(128)
            tileProvider.renderTile(
                TileRequest(
                    leftPx = 0,
                    topPx = 0,
                    widthPx = min(tileSize, transform.pageWidthPx).coerceAtLeast(1),
                    heightPx = min(tileSize, transform.pageHeightPx).coerceAtLeast(1),
                    scale = 1f,
                    quality = RenderPolicy.Quality.DRAFT
                )
            )
        } finally {
            tileProvider.close()
        }
    }

    private fun List<DocumentLink>.rotateBy(rotationDegrees: Int): List<DocumentLink> {
        val normalized = ((rotationDegrees % 360) + 360) % 360
        if (normalized == 0) return this
        return map { link ->
            val bounds = link.bounds
                ?.map { rect -> rotateRect(rect, normalized) }
                ?.takeIf { it.isNotEmpty() }
            link.copy(bounds = bounds)
        }
    }

    private fun rotateRect(rect: NormalizedRect, rotationDegrees: Int): NormalizedRect {
        val points = listOf(
            rect.left to rect.top,
            rect.right to rect.top,
            rect.right to rect.bottom,
            rect.left to rect.bottom
        ).map { (x, y) ->
            when (rotationDegrees) {
                90 -> (1f - y) to x
                180 -> (1f - x) to (1f - y)
                270 -> y to (1f - x)
                else -> x to y
            }
        }

        val minX = points.minOf { it.first }.coerceIn(0f, 1f)
        val maxX = points.maxOf { it.first }.coerceIn(0f, 1f)
        val minY = points.minOf { it.second }.coerceIn(0f, 1f)
        val maxY = points.maxOf { it.second }.coerceIn(0f, 1f)
        return NormalizedRect(
            left = minX,
            top = minY,
            right = maxX,
            bottom = maxY
        )
    }
}
```

## engines/pdf\src\main\kotlin\com\ireader\engines\pdf\internal\render\PdfTileProvider.kt

```kotlin
package com.ireader.engines.pdf.internal.render

import android.graphics.Bitmap
import com.ireader.engines.pdf.PdfEngineConfig
import com.ireader.engines.pdf.internal.backend.PdfBackend
import com.ireader.engines.pdf.internal.cache.TileCache
import com.ireader.engines.pdf.internal.cache.TileCacheKey
import com.ireader.engines.pdf.internal.cache.TileInflight
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.TileProvider
import com.ireader.reader.api.render.TileRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlin.math.roundToInt

internal class PdfTileProvider(
    private val pageIndex: Int,
    private val renderConfig: RenderConfig.FixedPage,
    private val backend: PdfBackend,
    private val cache: TileCache,
    private val inflight: TileInflight,
    private val config: PdfEngineConfig,
    private val scope: CoroutineScope
) : TileProvider {

    override suspend fun renderTile(request: TileRequest): Bitmap {
        val scale = request.scale.coerceAtLeast(0.5f)
        val scaledWidth = (request.widthPx * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (request.heightPx * scale).roundToInt().coerceAtLeast(1)
        val key = TileCacheKey(
            pageIndex = pageIndex,
            leftPx = request.leftPx,
            topPx = request.topPx,
            widthPx = request.widthPx,
            heightPx = request.heightPx,
            scaleMilli = (scale * 1000f).roundToInt(),
            quality = request.quality,
            rotationDegrees = renderConfig.rotationDegrees,
            zoomBucketMilli = zoomBucketMilli(renderConfig.zoom)
        )

        val cached = cache.get(key)
        if (cached != null && !cached.isRecycled) return cached

        val rendered = inflight.getOrAwait(key) {
            val bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
            val regionLeft = (request.leftPx * scale).roundToInt().coerceAtLeast(0)
            val regionTop = (request.topPx * scale).roundToInt().coerceAtLeast(0)
            backend.renderRegion(
                pageIndex = pageIndex,
                bitmap = bitmap,
                regionLeftPx = regionLeft,
                regionTopPx = regionTop,
                regionWidthPx = scaledWidth,
                regionHeightPx = scaledHeight,
                quality = request.quality
            )
            bitmap
        }

        cache.put(key, rendered)
        return rendered
    }

    override fun close() {
        scope.cancel()
    }
}

```

## engines/pdf\src\main\kotlin\com\ireader\engines\pdf\internal\session\PdfSession.kt

```kotlin
package com.ireader.engines.pdf.internal.session

import com.ireader.engines.common.android.session.BaseReaderSession
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.api.provider.SelectionController
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.model.SessionId

internal class PdfSession(
    id: SessionId,
    controller: ReaderController,
    outline: OutlineProvider?,
    search: SearchProvider?,
    text: TextProvider?,
    annotations: AnnotationProvider?,
    selection: SelectionProvider?,
    selectionController: SelectionController?
) : BaseReaderSession(
    id = id,
    controller = controller,
    outline = outline,
    search = search,
    text = text,
    annotations = annotations,
    selection = selection,
    selectionController = selectionController
)
```

## engines/pdf\src\main\kotlin\com\ireader\engines\pdf\internal\util\PdfLocators.kt

```kotlin
package com.ireader.engines.pdf.internal.util

import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.Progression
import kotlin.math.roundToInt

internal object PdfLocatorExtras {
    const val PageLabel: String = "pageLabel"
    const val CharIndex: String = "charIndex"
    const val CharStart: String = "charStart"
    const val CharEnd: String = "charEnd"
}

internal fun pageLocator(pageIndex: Int, pageCount: Int): Locator {
    val safePageCount = pageCount.coerceAtLeast(1)
    val safePageIndex = pageIndex.coerceIn(0, safePageCount - 1)
    return Locator(
        scheme = LocatorSchemes.PDF_PAGE,
        value = safePageIndex.toString(),
        extras = mapOf(
            PdfLocatorExtras.PageLabel to "${safePageIndex + 1}/$safePageCount"
        )
    )
}

internal fun progressionForPage(pageIndex: Int, pageCount: Int): Progression {
    val safePageCount = pageCount.coerceAtLeast(1)
    val safePageIndex = pageIndex.coerceIn(0, safePageCount - 1)
    val denominator = (safePageCount - 1).coerceAtLeast(1)
    val percent = (safePageIndex.toDouble() / denominator.toDouble()).coerceIn(0.0, 1.0)
    return Progression(
        percent = percent,
        label = "${safePageIndex + 1}/$safePageCount",
        current = safePageIndex + 1,
        total = safePageCount
    )
}

internal fun Locator.toPdfPageIndexOrNull(pageCount: Int): Int? {
    if (scheme != LocatorSchemes.PDF_PAGE) return null
    val parsed = value.toIntOrNull() ?: return null
    return parsed.coerceIn(0, pageCount.coerceAtLeast(1) - 1)
}

internal fun startCharLocator(
    pageIndex: Int,
    pageCount: Int,
    charStart: Int
): Locator {
    val base = pageLocator(pageIndex, pageCount)
    return base.copy(
        extras = base.extras + mapOf(
            PdfLocatorExtras.CharIndex to charStart.toString(),
            PdfLocatorExtras.CharStart to charStart.toString()
        )
    )
}

internal fun endCharLocator(
    pageIndex: Int,
    pageCount: Int,
    charEnd: Int
): Locator {
    val base = pageLocator(pageIndex, pageCount)
    return base.copy(
        extras = base.extras + mapOf(
            PdfLocatorExtras.CharIndex to charEnd.toString(),
            PdfLocatorExtras.CharEnd to charEnd.toString()
        )
    )
}

internal fun Locator.charIndexOrNull(): Int? {
    return extras[PdfLocatorExtras.CharIndex]?.toIntOrNull()
}

internal fun Locator.charStartOrDefault(defaultValue: Int): Int {
    return extras[PdfLocatorExtras.CharStart]?.toIntOrNull() ?: defaultValue
}

internal fun Locator.charEndOrDefault(defaultValue: Int): Int {
    return extras[PdfLocatorExtras.CharEnd]?.toIntOrNull() ?: defaultValue
}

internal fun progressionToPage(percent: Double, pageCount: Int): Int {
    val safePageCount = pageCount.coerceAtLeast(1)
    if (safePageCount <= 1) return 0
    return ((safePageCount - 1) * percent.coerceIn(0.0, 1.0)).roundToInt()
        .coerceIn(0, safePageCount - 1)
}
```

## engines/pdf\src\main\kotlin\com\ireader\engines\pdf\PdfEngine.kt

```kotlin
package com.ireader.engines.pdf

import android.content.Context
import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.common.android.id.SourceDocumentIds
import com.ireader.engines.pdf.internal.backend.BackendFactory
import com.ireader.engines.pdf.internal.backend.PdfBackendProvider
import com.ireader.engines.pdf.internal.open.PdfDocument
import com.ireader.engines.pdf.internal.open.PdfOpener
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentId
import kotlinx.coroutines.withContext

class PdfEngine internal constructor(
    private val config: PdfEngineConfig,
    private val backendProvider: PdfBackendProvider
) : ReaderEngine {

    constructor(
        context: Context,
        config: PdfEngineConfig = PdfEngineConfig()
    ) : this(
        config = config,
        backendProvider = BackendFactory(
            opener = PdfOpener(
                context = context.applicationContext,
                ioDispatcher = config.ioDispatcher
            ),
            config = config
        )
    )

    override val supportedFormats: Set<BookFormat> = setOf(BookFormat.PDF)

    override suspend fun open(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<ReaderDocument> = withContext(config.ioDispatcher) {
        val opened = when (val result = backendProvider.open(source, options.password)) {
            is ReaderResult.Err -> return@withContext result
            is ReaderResult.Ok -> result.value
        }
        val created = runCatching {
            val pageCount = opened.backend.pageCount()
            val documentId = buildDocumentId(source)

            ReaderResult.Ok(
                PdfDocument(
                    id = documentId,
                    source = source,
                    openedPdf = opened,
                    pageCount = pageCount,
                    engineConfig = config,
                    openOptions = options
                ) as ReaderDocument
            )
        }.fold(
            onSuccess = { it },
            onFailure = {
                ReaderResult.Err(
                    it.toReaderError(invalidPasswordKeywords = setOf("password", "encrypted"))
                )
            }
        )
        if (created is ReaderResult.Err) {
            runCatching { opened.cleanup.close() }
        }
        created
    }

    private fun buildDocumentId(source: DocumentSource): DocumentId {
        return SourceDocumentIds.fromSourceSha1(prefix = "pdf", source = source)
    }
}
```

## engines/pdf\src\main\kotlin\com\ireader\engines\pdf\PdfEngineConfig.kt

```kotlin
package com.ireader.engines.pdf

import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.model.DocumentId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.math.max
import kotlin.math.min

enum class PdfBackendStrategy {
    AUTO,
    PDFIUM_ONLY,
    PLATFORM_ONLY
}

data class PdfEngineConfig(
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val renderDispatcher: CoroutineDispatcher = Dispatchers.Default,
    val tileBaseSizePx: Int = 512,
    val tileCacheMaxBytes: Int = defaultTileCacheBytes(),
    val backendStrategy: PdfBackendStrategy = PdfBackendStrategy.AUTO,
    val annotationProviderFactory: ((DocumentId) -> AnnotationProvider?)? = null
) {
    companion object {
        private fun defaultTileCacheBytes(): Int {
            val maxMem = Runtime.getRuntime().maxMemory()
            val target = maxMem / 12L
            return max(min(target, 192L * 1024 * 1024), 24L * 1024 * 1024).toInt()
        }
    }
}
```

