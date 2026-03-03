# reader/api 和 runtime 的 Kotlin 源码汇总

> 来源：`core/reader/api` 与 `core/reader/runtime` 下的 `.kt` 文件（已排除测试文件）。

## core/reader/runtime\src\main\kotlin\com\ireader\reader\runtime\ReaderSessionHandle.kt

```kotlin
package com.ireader.reader.runtime

import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderSession
import java.io.Closeable

class ReaderSessionHandle(
    val document: ReaderDocument,
    val session: ReaderSession
) : Closeable {

    // 方便 feature 直接拿 controller/providers
    val controller = session.controller
    val outline = session.outline
    val search = session.search
    val text = session.text
    val annotations = session.annotations
    val resources = session.resources
    val selection = session.selection

    override fun close() {
        // 逆序关闭更安全
        runCatching { session.close() }
        runCatching { document.close() }
    }
}

```

## core/reader/runtime\src\main\kotlin\com\ireader\reader\runtime\ReaderRuntime.kt

```kotlin
package com.ireader.reader.runtime

import com.ireader.core.files.source.DocumentSource
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.Locator

data class BookProbeResult(
    val format: BookFormat,
    val documentId: String?,
    val metadata: DocumentMetadata?,
    val coverBytes: ByteArray?,
    val capabilities: DocumentCapabilities?
)

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

    suspend fun probe(
        source: DocumentSource,
        options: OpenOptions = OpenOptions()
    ): ReaderResult<BookProbeResult>
}
```

## core/reader/runtime\src\main\kotlin\com\ireader\reader\runtime\DefaultReaderRuntime.kt

```kotlin
package com.ireader.reader.runtime

import com.ireader.core.files.source.DocumentSource
import com.ireader.reader.api.engine.EngineRegistry
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.Locator
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
                        runCatching { document.close() }
                        ReaderResult.Err(sessionResult.error)
                    }
                }
            }
        }
    }

    override suspend fun probe(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<BookProbeResult> {
        val documentResult = openDocument(source = source, options = options)
        return when (documentResult) {
            is ReaderResult.Err -> documentResult
            is ReaderResult.Ok -> {
                val document = documentResult.value
                val metadata = readMetadataSafely(document)
                val result = BookProbeResult(
                    format = document.format,
                    documentId = document.id.value,
                    metadata = metadata,
                    coverBytes = null,
                    capabilities = document.capabilities
                )
                runCatching { document.close() }
                ReaderResult.Ok(result)
            }
        }
    }

    private suspend fun readMetadataSafely(document: ReaderDocument): DocumentMetadata? {
        return when (val metadataResult = catchingSuspend { document.metadata() }) {
            is ReaderResult.Ok -> metadataResult.value
            is ReaderResult.Err -> null
        }
    }

    private suspend fun openWithFormat(
        source: DocumentSource,
        options: OpenOptions,
        format: BookFormat
    ): ReaderResult<ReaderDocument> {
        val engine = engineRegistry.engineFor(format)
            ?: return ReaderResult.Err(ReaderError.UnsupportedFormat(detected = format.name))

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

## core/reader/runtime\src\main\kotlin\com\ireader\reader\runtime\render\RenderDefaults.kt

```kotlin
package com.ireader.reader.runtime.render

import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.api.render.RenderConfig

object RenderDefaults {

    fun configFor(capabilities: DocumentCapabilities): RenderConfig =
        when {
            capabilities.fixedLayout -> RenderConfig.FixedPage()
            else -> RenderConfig.ReflowText()
        }
}


```

## core/reader/runtime\src\main\kotlin\com\ireader\reader\runtime\error\ReaderErrorMapper.kt

```kotlin
package com.ireader.reader.runtime.error

import com.ireader.reader.api.error.ReaderError
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.ZipException
import kotlinx.coroutines.CancellationException

internal fun Throwable.toReaderError(): ReaderError =
    when (this) {
        is ReaderError -> this
        is CancellationException -> ReaderError.Cancelled(cause = this)
        is FileNotFoundException -> ReaderError.NotFound(cause = this)
        is SecurityException -> ReaderError.PermissionDenied(cause = this)
        is ZipException -> ReaderError.CorruptOrInvalid(cause = this)
        is IOException -> ReaderError.Io(cause = this)
        else -> ReaderError.Internal(cause = this)
    }

```

## core/reader/runtime\src\main\kotlin\com\ireader\reader\runtime\flow\ReaderFlowExt.kt

```kotlin
package com.ireader.reader.runtime.flow

import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.runtime.error.toReaderError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

fun <T> Flow<T>.asReaderResult(
    mapper: (Throwable) -> ReaderError = { it.toReaderError() }
): Flow<ReaderResult<T>> =
    this
        .map<T, ReaderResult<T>> { ReaderResult.Ok(it) }
        .catch { emit(ReaderResult.Err(mapper(it))) }

```

## core/reader/runtime\src\main\kotlin\com\ireader\reader\runtime\registry\EngineRegistryImpl.kt

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

## core/reader/runtime\src\main\kotlin\com\ireader\reader\runtime\format\DefaultBookFormatDetector.kt

```kotlin
package com.ireader.reader.runtime.format

import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.BookFormat
import com.ireader.core.files.source.DocumentSource
import com.ireader.reader.runtime.error.toReaderError
import java.io.BufferedInputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Suppress("MagicNumber", "NestedBlockDepth", "ReturnCount", "TooGenericExceptionCaught")
class DefaultBookFormatDetector(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BookFormatDetector {

    override suspend fun detect(
        source: DocumentSource,
        hint: BookFormat?
    ): ReaderResult<BookFormat> = withContext(ioDispatcher) {
        try {
            // 1) hint
            if (hint != null) return@withContext ReaderResult.Ok(hint)

            // 2) mime
            detectFromMime(source.mimeType)?.let { return@withContext ReaderResult.Ok(it) }

            // 3) extension
            detectFromName(source.displayName)?.let { return@withContext ReaderResult.Ok(it) }

            // 4) magic sniff
            sniffFromContent(source)
        } catch (t: Throwable) {
            ReaderResult.Err(t.toReaderError())
        }
    }

    private fun detectFromMime(mimeType: String?): BookFormat? {
        val mime = mimeType?.lowercase(Locale.US)?.trim() ?: return null
        return when {
            mime == "application/pdf" -> BookFormat.PDF
            mime == "application/epub+zip" -> BookFormat.EPUB
            mime == "text/plain" -> BookFormat.TXT
            // 有些文件管理器会给 text/*，可按需要放宽
            mime.startsWith("text/") -> BookFormat.TXT
            else -> null
        }
    }

    private fun detectFromName(displayName: String?): BookFormat? {
        val name = displayName?.lowercase(Locale.US)?.trim() ?: return null
        return when {
            name.endsWith(".pdf") -> BookFormat.PDF
            name.endsWith(".epub") -> BookFormat.EPUB
            name.endsWith(".txt") -> BookFormat.TXT
            else -> null
        }
    }

    private suspend fun sniffFromContent(source: DocumentSource): ReaderResult<BookFormat> {
        val header = readHeader(source, bytes = 8)

        if (looksLikePdf(header)) return ReaderResult.Ok(BookFormat.PDF)

        if (looksLikeZip(header)) {
            val epub = looksLikeEpubZip(source)
            return if (epub) {
                ReaderResult.Ok(BookFormat.EPUB)
            } else {
                ReaderResult.Err(ReaderError.UnsupportedFormat(detected = "zip"))
            }
        }

        // 兜底策略：本地阅读器很多“未知类型”其实就是 txt（尤其是 mime 缺失时）
        return ReaderResult.Ok(BookFormat.TXT)
    }

    private suspend fun readHeader(source: DocumentSource, bytes: Int): ByteArray {
        return source.openInputStream().use { input ->
            val buf = ByteArray(bytes)
            val read = input.read(buf)
            if (read <= 0) ByteArray(0) else buf.copyOf(read)
        }
    }

    private fun looksLikePdf(header: ByteArray): Boolean {
        // "%PDF"
        return header.size >= 4 &&
            header[0] == 0x25.toByte() &&
            header[1] == 0x50.toByte() &&
            header[2] == 0x44.toByte() &&
            header[3] == 0x46.toByte()
    }

    private fun looksLikeZip(header: ByteArray): Boolean {
        // "PK.."
        return header.size >= 2 &&
            header[0] == 0x50.toByte() &&
            header[1] == 0x4B.toByte()
    }

    private suspend fun looksLikeEpubZip(source: DocumentSource): Boolean {
        // 重新开流：zip 探测需要从头读
        source.openInputStream().use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zis ->
                var hasContainerXml = false

                while (true) {
                    coroutineContext.ensureActive()

                    val entry = zis.nextEntry ?: break
                    val name = entry.name

                    if (name == "mimetype") {
                        // epub 标准：mimetype 文件内容为 "application/epub+zip"
                        val content = readSmallAscii(zis, limitBytes = 64).trim()
                        if (content == "application/epub+zip") return true
                    }

                    if (name.equals("META-INF/container.xml", ignoreCase = true)) {
                        hasContainerXml = true
                        // 有 container.xml 基本可以视为 epub
                        return true
                    }
                }

                return hasContainerXml
            }
        }
    }

    private fun readSmallAscii(zis: ZipInputStream, limitBytes: Int): String {
        val buf = ByteArray(limitBytes)
        val read = zis.read(buf)
        return if (read <= 0) "" else String(buf, 0, read, Charsets.US_ASCII)
    }
}


```

## core/reader/runtime\src\main\kotlin\com\ireader\reader\runtime\format\BookFormatDetector.kt

```kotlin
package com.ireader.reader.runtime.format

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.BookFormat
import com.ireader.core.files.source.DocumentSource

interface BookFormatDetector {
    suspend fun detect(
        source: DocumentSource,
        hint: BookFormat? = null
    ): ReaderResult<BookFormat>
}


```

## core/reader/api\src\main\kotlin\com\ireader\reader\api\engine\ReaderSession.kt

```kotlin
package com.ireader.reader.api.engine

import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.model.SessionId
import java.io.Closeable

interface ReaderSession : Closeable {
    val id: SessionId

    /**
     * 核心控制器：导航 + 渲染 + 状态流
     */
    val controller: ReaderController

    /**
     * 可选能力（按 capabilities 决定是否为 null）
     */
    val outline: OutlineProvider?
    val search: SearchProvider?
    val text: TextProvider?
    val annotations: AnnotationProvider?
    val resources: ResourceProvider? // EPUB 常用：给 WebView/资源加载器
    val selection: SelectionProvider?
}
```

## core/reader/api\src\main\kotlin\com\ireader\reader\api\engine\ReaderEngine.kt

```kotlin
package com.ireader.reader.api.engine

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.BookFormat
import com.ireader.reader.api.open.OpenOptions
import com.ireader.core.files.source.DocumentSource

interface ReaderEngine {
    val supportedFormats: Set<BookFormat>

    /**
     * 打开文档：解析、解压、建立索引等
     * 失败返回 ReaderResult.Err，避免异常散落 UI 层。
     */
    suspend fun open(
        source: DocumentSource,
        options: OpenOptions = OpenOptions()
    ): ReaderResult<ReaderDocument>
}


```

## core/reader/api\src\main\kotlin\com\ireader\reader\api\engine\ReaderDocument.kt

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
     * 会话：包含当前阅读位置、渲染设置、缓存等
     */
    suspend fun createSession(
        initialLocator: Locator? = null,
        initialConfig: RenderConfig = RenderConfig.Default
    ): ReaderResult<ReaderSession>
}


```

## core/reader/api\src\main\kotlin\com\ireader\reader\api\engine\EngineRegistry.kt

```kotlin
package com.ireader.reader.api.engine

import com.ireader.reader.model.BookFormat

interface EngineRegistry {
    fun engineFor(format: BookFormat): ReaderEngine?
}


```

## core/reader/api\src\main\kotlin\com\ireader\reader\api\annotation\Decoration.kt

```kotlin
package com.ireader.reader.api.annotation

import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.NormalizedRect
import com.ireader.reader.model.annotation.AnnotationStyle

/**
 * Decoration 是“渲染叠加层”的通用表达。
 * - reflow：按 LocatorRange
 * - fixed：按 page + rects
 */
sealed interface Decoration {

    data class Reflow(
        val range: LocatorRange,
        val style: AnnotationStyle
    ) : Decoration

    data class Fixed(
        val page: Locator,
        val rects: List<NormalizedRect>,
        val style: AnnotationStyle
    ) : Decoration
}


```

## core/reader/api\src\main\kotlin\com\ireader\reader\api\error\ReaderError.kt

```kotlin
package com.ireader.reader.api.error

/**
 * api 层错误类型：保证 UI/业务可以稳定识别并做兜底。
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

## core/reader/api\src\main\kotlin\com\ireader\reader\api\error\ReaderResult.kt

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

## core/reader/api\src\main\kotlin\com\ireader\reader\api\error\ReaderResultExt.kt

```kotlin
package com.ireader.reader.api.error

inline fun <T, R> ReaderResult<T>.map(transform: (T) -> R): ReaderResult<R> =
    when (this) {
        is ReaderResult.Ok -> ReaderResult.Ok(transform(value))
        is ReaderResult.Err -> this
    }

inline fun <T, R> ReaderResult<T>.flatMap(transform: (T) -> ReaderResult<R>): ReaderResult<R> =
    when (this) {
        is ReaderResult.Ok -> transform(value)
        is ReaderResult.Err -> this
    }

inline fun <T> ReaderResult<T>.mapError(transform: (ReaderError) -> ReaderError): ReaderResult<T> =
    when (this) {
        is ReaderResult.Ok -> this
        is ReaderResult.Err -> ReaderResult.Err(transform(error))
    }

inline fun <T, R> ReaderResult<T>.fold(
    onOk: (T) -> R,
    onErr: (ReaderError) -> R
): R =
    when (this) {
        is ReaderResult.Ok -> onOk(value)
        is ReaderResult.Err -> onErr(error)
    }

inline fun <T> ReaderResult<T>.onOk(block: (T) -> Unit): ReaderResult<T> =
    also { if (this is ReaderResult.Ok) block(value) }

inline fun <T> ReaderResult<T>.onErr(block: (ReaderError) -> Unit): ReaderResult<T> =
    also { if (this is ReaderResult.Err) block(error) }

```

## core/reader/api\src\main\kotlin\com\ireader\reader\api\provider\AnnotationProvider.kt

```kotlin
package com.ireader.reader.api.provider

import com.ireader.reader.model.annotation.Annotation
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationId
import com.ireader.reader.api.annotation.Decoration
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import kotlinx.coroutines.flow.Flow

data class AnnotationQuery(
    val page: Locator? = null,            // fixed 查询（PDF）
    val range: LocatorRange? = null       // reflow 查询（TXT/EPUB）
)

interface AnnotationProvider {

    fun observeAll(): Flow<List<Annotation>>

    suspend fun listAll(): ReaderResult<List<Annotation>>

    suspend fun query(query: AnnotationQuery): ReaderResult<List<Annotation>>

    suspend fun create(draft: AnnotationDraft): ReaderResult<Annotation>

    suspend fun update(annotation: Annotation): ReaderResult<Unit>

    suspend fun delete(id: AnnotationId): ReaderResult<Unit>

    /**
     * 提供给渲染层的 Decoration（默认可以由 annotation->decoration 直接映射）
     * 引擎也可以做更复杂的映射（比如 EPUB CFI 对应到章节分页后的 charRange）。
     */
    suspend fun decorationsFor(query: AnnotationQuery): ReaderResult<List<Decoration>>
}


```

## core/reader/api\src\main\kotlin\com\ireader\reader\api\provider\AnnotationStore.kt

```kotlin
package com.ireader.reader.api.provider

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.annotation.Annotation
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationId
import kotlinx.coroutines.flow.Flow

/**
 * App-level annotation persistence abstraction.
 *
 * Implemented in data layer (Room/sync). Engines consume this contract.
 */
interface AnnotationStore {
    fun observe(documentId: DocumentId): Flow<List<Annotation>>

    suspend fun list(documentId: DocumentId): ReaderResult<List<Annotation>>

    suspend fun query(documentId: DocumentId, query: AnnotationQuery): ReaderResult<List<Annotation>>

    suspend fun create(documentId: DocumentId, draft: AnnotationDraft): ReaderResult<Annotation>

    suspend fun update(documentId: DocumentId, annotation: Annotation): ReaderResult<Unit>

    suspend fun delete(documentId: DocumentId, id: AnnotationId): ReaderResult<Unit>
}
```

## core/reader/api\src\main\kotlin\com\ireader\reader\api\provider\OutlineProvider.kt

```kotlin
package com.ireader.reader.api.provider

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.OutlineNode

interface OutlineProvider {
    suspend fun getOutline(): ReaderResult<List<OutlineNode>>
}


```

## core/reader/api\src\main\kotlin\com\ireader\reader\api\provider\ResourceProvider.kt

```kotlin
package com.ireader.reader.api.provider

import com.ireader.reader.api.error.ReaderResult
import java.io.InputStream

/**
 * 资源访问：当 EPUB 不直接暴露 file:// 路径时，
 * 你可以用 WebViewAssetLoader / 自建 ContentProvider 走这层读取。
 */
interface ResourceProvider {
    suspend fun openResource(path: String): ReaderResult<InputStream>
    suspend fun getMimeType(path: String): ReaderResult<String?>
}

```

## core/reader/api\src\main\kotlin\com\ireader\reader\api\provider\SelectionProvider.kt

```kotlin
package com.ireader.reader.api.provider

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.Locator
import com.ireader.reader.model.NormalizedRect

/**
 * Unified current-selection API for reflow/fixed engines.
 */
interface SelectionProvider {

    data class Selection(
        val locator: Locator,
        val bounds: NormalizedRect? = null
    )

    suspend fun currentSelection(): ReaderResult<Selection?>

    suspend fun clearSelection(): ReaderResult<Unit>
}
```

## core/reader/api\src\main\kotlin\com\ireader\reader\api\provider\SearchProvider.kt

```kotlin
package com.ireader.reader.api.provider

import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import kotlinx.coroutines.flow.Flow

data class SearchOptions(
    val caseSensitive: Boolean = false,
    val wholeWord: Boolean = false,
    val maxHits: Int = 500,
    val startFrom: Locator? = null
)

data class SearchHit(
    val range: LocatorRange,
    val excerpt: String,
    val sectionTitle: String? = null
)

interface SearchProvider {
    fun search(query: String, options: SearchOptions = SearchOptions()): Flow<SearchHit>
}


```

## core/reader/api\src\main\kotlin\com\ireader\reader\api\provider\TextProvider.kt

```kotlin
package com.ireader.reader.api.provider

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange

interface TextProvider {
    suspend fun getText(range: LocatorRange): ReaderResult<String>
    suspend fun getTextAround(locator: Locator, maxChars: Int = 512): ReaderResult<String>
}


```

## core/reader/api\src\main\kotlin\com\ireader\reader\api\open\OpenOptions.kt

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

## core/reader/api\src\main\kotlin\com\ireader\reader\api\render\RenderPolicy.kt

```kotlin
package com.ireader.reader.api.render

/**
 * 渲染策略：用于优化体验
 * - PDF：先出 DRAFT（低清）再补 FINAL（高清）
 * - TXT/EPUB：通常忽略 quality，但可用于“快速分页->精分页”
 */
data class RenderPolicy(
    val quality: Quality = Quality.FINAL,
    val allowCache: Boolean = true,
    val prefetchNeighbors: Int = 1 // 渲染后预取前后页
) {
    enum class Quality { DRAFT, FINAL }

    companion object {
        val Default = RenderPolicy()
    }
}

```

## core/reader/api\src\main\kotlin\com\ireader\reader\api\render\RenderPage.kt

```kotlin
package com.ireader.reader.api.render

import android.graphics.Bitmap
import android.net.Uri
import com.ireader.reader.api.annotation.Decoration
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.Locator

@JvmInline
value class PageId(val value: String)

/**
 * 渲染产物统一封装：内容 + 可选链接 + 可选装饰(标注高亮等) + 性能指标
 */
data class RenderPage(
    val id: PageId,
    val locator: Locator,
    val content: RenderContent,
    val links: List<DocumentLink> = emptyList(),
    val decorations: List<Decoration> = emptyList(),
    val metrics: RenderMetrics? = null
)

sealed interface RenderContent {
    data class Text(
        val text: CharSequence,
        val mapping: TextMapping? = null
    ) : RenderContent

    /**
     * EPUB 常用：inline html 或者直接给一个可加载的 Uri
     * - Inline：适合引擎自己拼装 html
     * - Uri：适合引擎把章节写入/映射到本地资源系统
     */
    data class Html(
        val inlineHtml: String? = null,
        val contentUri: Uri? = null,
        val baseUri: Uri? = null
    ) : RenderContent

    data class BitmapPage(
        val bitmap: Bitmap
    ) : RenderContent

    /**
     * PDF 大图/高倍率缩放建议用 Tiles：避免一次性巨大 bitmap。
     */
    data class Tiles(
        val pageWidthPx: Int,
        val pageHeightPx: Int,
        val tileProvider: TileProvider
    ) : RenderContent

    /**
     * 页面由引擎直接在 bindSurface() 绑定的承载面中渲染。
     */
    data object Embedded : RenderContent
}

data class RenderMetrics(
    val renderTimeMs: Long,
    val cacheHit: Boolean
)


```

## core/reader/api\src\main\kotlin\com\ireader\reader\api\render\RenderConfig.kt

```kotlin
package com.ireader.reader.api.render

sealed interface RenderConfig {

    data class ReflowText(
        val fontSizeSp: Float = 18f,
        val lineHeightMult: Float = 1.5f,
        val paragraphSpacingDp: Float = 6f,
        val pagePaddingDp: Float = 16f,
        val fontFamilyName: String? = null,
        val hyphenation: Boolean = false,
        val extra: Map<String, String> = emptyMap()
    ) : RenderConfig

    data class FixedPage(
        val fitMode: FitMode = FitMode.FIT_WIDTH,
        val zoom: Float = 1.0f,
        val rotationDegrees: Int = 0,
        val extra: Map<String, String> = emptyMap()
    ) : RenderConfig

    enum class FitMode { FIT_WIDTH, FIT_HEIGHT, FIT_PAGE, FREE }

    companion object {
        val Default: RenderConfig = ReflowText()
    }
}

```

## core/reader/api\src\main\kotlin\com\ireader\reader\api\render\ReaderController.kt

```kotlin
package com.ireader.reader.api.render

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.Locator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable

sealed interface ReaderEvent {
    data class PageChanged(val locator: Locator) : ReaderEvent
    data class Rendered(val pageId: PageId, val metrics: RenderMetrics?) : ReaderEvent
    data class Error(val throwable: Throwable) : ReaderEvent
}

/**
 * Session 内的“唯一总控”：UI 层只需要跟它交互即可。
 * - 负责：布局约束、配置变更、跳转/翻页、产出 RenderPage
 * - 维护：状态流/事件流
 */
interface ReaderController : Closeable {

    val state: StateFlow<RenderState>
    val events: Flow<ReaderEvent>

    /**
     * 绑定渲染承载面（可选）。
     *
     * - 导航器型引擎（如 EPUB）会在该 surface 中渲染实际内容
     * - 非导航器型引擎可返回 Ok(Unit)
     */
    suspend fun bindSurface(surface: RenderSurface): ReaderResult<Unit>

    /**
     * 解除渲染承载面绑定（可选）。
     */
    suspend fun unbindSurface(): ReaderResult<Unit>

    suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit>

    suspend fun setConfig(config: RenderConfig): ReaderResult<Unit>

    /**
     * 渲染当前 locator
     */
    suspend fun render(policy: RenderPolicy = RenderPolicy.Default): ReaderResult<RenderPage>

    suspend fun next(policy: RenderPolicy = RenderPolicy.Default): ReaderResult<RenderPage>

    suspend fun prev(policy: RenderPolicy = RenderPolicy.Default): ReaderResult<RenderPage>

    suspend fun goTo(locator: Locator, policy: RenderPolicy = RenderPolicy.Default): ReaderResult<RenderPage>

    /**
     * 按进度跳转：percent 0..1
     * TXT/EPUB 可能是近似（取决于实现）。
     */
    suspend fun goToProgress(percent: Double, policy: RenderPolicy = RenderPolicy.Default): ReaderResult<RenderPage>

    /**
     * 预取（可 no-op），通常用于：翻页更顺滑 / PDF 下一页 tiles 预热
     */
    suspend fun prefetchNeighbors(count: Int = 1): ReaderResult<Unit>

    /**
     * 当标注/装饰变化时，通知引擎让下一次 render 使用最新 decorations（或内部 cache 失效）。
     */
    suspend fun invalidate(reason: InvalidateReason = InvalidateReason.CONTENT_CHANGED): ReaderResult<Unit>
}

enum class InvalidateReason {
    CONTENT_CHANGED,   // 标注/主题变化导致页面装饰变动
    CONFIG_CHANGED,    // 字体/缩放等
    LAYOUT_CHANGED     // 横竖屏/窗口变化
}


```

## core/reader/api\src\main\kotlin\com\ireader\reader\api\render\LayoutConstraints.kt

```kotlin
package com.ireader.reader.api.render

data class LayoutConstraints(
    val viewportWidthPx: Int,
    val viewportHeightPx: Int,
    val density: Float,
    val fontScale: Float
)

```

## core/reader/api\src\main\kotlin\com\ireader\reader\api\render\RenderState.kt

```kotlin
package com.ireader.reader.api.render

import com.ireader.reader.model.Locator
import com.ireader.reader.model.Progression

data class NavigationAvailability(
    val canGoPrev: Boolean,
    val canGoNext: Boolean
)

data class RenderState(
    val locator: Locator,
    val progression: Progression,
    val nav: NavigationAvailability,
    val titleInView: String? = null,
    val config: RenderConfig
)


```

## core/reader/api\src\main\kotlin\com\ireader\reader\api\render\TextMapping.kt

```kotlin
package com.ireader.reader.api.render

import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange

/**
 * reflow 文本映射：用于 “字符索引 <-> Locator/Range”
 * 支撑：复制、标注范围定位、搜索命中定位等。
 */
interface TextMapping {
    fun locatorAt(charIndex: Int): Locator
    fun rangeFor(startChar: Int, endChar: Int): LocatorRange
    fun charRangeFor(range: LocatorRange): IntRange?
}


```

## core/reader/api\src\main\kotlin\com\ireader\reader\api\render\TileProvider.kt

```kotlin
package com.ireader.reader.api.render

import android.graphics.Bitmap
import java.io.Closeable

data class TileRequest(
    val leftPx: Int,
    val topPx: Int,
    val widthPx: Int,
    val heightPx: Int,
    val scale: Float = 1.0f,          // 缩放倍率（例如 2x）
    val quality: RenderPolicy.Quality = RenderPolicy.Quality.FINAL
)

interface TileProvider : Closeable {
    suspend fun renderTile(request: TileRequest): Bitmap
}

```

## core/reader/api\src\main\kotlin\com\ireader\reader\api\render\RenderSurface.kt

```kotlin
package com.ireader.reader.api.render

/**
 * UI 提供给引擎的渲染承载面。
 *
 * EPUB 等导航器型引擎可以在 bindSurface() 时将内部渲染组件挂载到该承载面。
 */
interface RenderSurface
```
