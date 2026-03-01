package com.ireader.engines.txt.internal.open

import com.ireader.engines.txt.TxtEngineConfig
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.render.RenderConfig
import com.ireader.engines.txt.internal.paging.TxtLastPositionStore
import com.ireader.engines.txt.internal.paging.TxtPaginationStore
import com.ireader.engines.txt.internal.provider.TxtOutlineCache
import com.ireader.engines.txt.internal.session.TxtSession
import com.ireader.engines.txt.internal.storage.TxtTextStore
import com.ireader.engines.txt.internal.storage.TxtTextStoreFactory
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.source.DocumentSource
import java.nio.charset.Charset
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class TxtDocument(
    private val source: DocumentSource,
    override val openOptions: OpenOptions,
    private val charset: Charset,
    private val ioDispatcher: CoroutineDispatcher,
    private val config: TxtEngineConfig
) : ReaderDocument {

    override val id: DocumentId = DocumentId("txt:${stableId(source, charset)}")
    override val format: BookFormat = BookFormat.TXT

    override val capabilities: DocumentCapabilities = DocumentCapabilities(
        reflowable = true,
        fixedLayout = false,
        outline = true,
        search = true,
        textExtraction = true,
        annotations = true,
        links = false
    )

    private val storeMutex = Mutex()
    private var store: TxtTextStore? = null

    private val paginationStore = TxtPaginationStore(
        config = config,
        docNamespace = id.value,
        charsetName = charset.name()
    )

    private val outlineCache = TxtOutlineCache(
        config = config,
        docNamespace = id.value,
        charsetName = charset.name()
    )

    private val lastPositionStore = TxtLastPositionStore(
        config = config,
        docNamespace = id.value,
        charsetName = charset.name()
    )

    override suspend fun metadata(): ReaderResult<DocumentMetadata> = withContext(ioDispatcher) {
        val title = source.displayName ?: source.uri.lastPathSegment ?: "Untitled"
        val extra = buildMap {
            put("charset", charset.name())
            source.sizeBytes?.let { put("sizeBytes", it.toString()) }
            source.mimeType?.let { put("mimeType", it) }
            put("uri", source.uri.toString())
        }
        ReaderResult.Ok(
            DocumentMetadata(
                title = title,
                author = null,
                language = null,
                identifier = id.value,
                extra = extra
            )
        )
    }

    override suspend fun createSession(
        initialLocator: Locator?,
        initialConfig: RenderConfig
    ): ReaderResult<ReaderSession> = withContext(ioDispatcher) {
        val normalizedLocator = normalizeLocator(initialLocator)
        val normalizedConfig = normalizeConfig(initialConfig)
        val startChar = normalizedLocator.value.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val textStore = getStore()

        TxtSession.create(
            documentId = id,
            store = textStore,
            paginationStore = paginationStore,
            outlineCache = outlineCache,
            lastPositionStore = lastPositionStore,
            explicitInitial = (initialLocator != null),
            initialStartChar = startChar,
            initialConfig = normalizedConfig,
            ioDispatcher = ioDispatcher
        )
    }

    override fun close() {
        runCatching { store?.close() }
    }

    private suspend fun getStore(): TxtTextStore = storeMutex.withLock {
        store ?: TxtTextStoreFactory.create(
            source = source,
            charset = charset,
            ioDispatcher = ioDispatcher
        ).also { store = it }
    }

    private fun normalizeLocator(locator: Locator?): Locator {
        if (locator == null) return Locator(LocatorSchemes.TXT_OFFSET, "0")
        if (locator.scheme != LocatorSchemes.TXT_OFFSET) return Locator(LocatorSchemes.TXT_OFFSET, "0")
        val offset = locator.value.toIntOrNull()?.coerceAtLeast(0) ?: 0
        return Locator(LocatorSchemes.TXT_OFFSET, offset.toString(), locator.extras)
    }

    private fun normalizeConfig(config: RenderConfig): RenderConfig.ReflowText {
        return when (config) {
            is RenderConfig.ReflowText -> config
            else -> RenderConfig.ReflowText()
        }
    }

    private fun stableId(source: DocumentSource, charset: Charset): String {
        val seed = buildString {
            append(source.uri.toString())
            append('|')
            append(source.displayName.orEmpty())
            append('|')
            append(source.sizeBytes?.toString().orEmpty())
            append('|')
            append(charset.name())
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        return buildString(32) {
            for (i in 0 until 16) {
                val b = digest[i].toInt() and 0xFF
                append("0123456789abcdef"[b ushr 4])
                append("0123456789abcdef"[b and 0x0F])
            }
        }
    }
}
