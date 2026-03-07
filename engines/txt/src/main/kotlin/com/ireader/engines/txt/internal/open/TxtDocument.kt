@file:Suppress("LongParameterList", "TooGenericExceptionCaught")

package com.ireader.engines.txt.internal.open

import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.common.android.reflow.SoftBreakRuleConfig
import com.ireader.engines.common.android.reflow.SoftBreakTuningProfile
import com.ireader.engines.txt.internal.locator.TxtAnchorLocatorCodec
import com.ireader.engines.txt.internal.runtime.BlockStore
import com.ireader.engines.txt.internal.runtime.BreakResolver
import com.ireader.engines.txt.internal.render.TxtController
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndex
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.engine.DocumentCapabilities
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.sanitized
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.Locator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class TxtDocument(
    override val id: DocumentId,
    private val source: DocumentSource,
    private val files: TxtBookFiles,
    private val meta: TxtMeta,
    override val openOptions: OpenOptions,
    private val persistPagination: Boolean,
    private val persistOutline: Boolean,
    private val maxPageCache: Int,
    private val annotationProviderFactory: ((DocumentId) -> AnnotationProvider?)?,
    private val ioDispatcher: CoroutineDispatcher,
    private val paginationDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher
) : ReaderDocument {

    override val format: BookFormat = BookFormat.TXT

    override val capabilities: DocumentCapabilities
        get() = DocumentCapabilities(
            reflowable = true,
            fixedLayout = false,
            outline = true,
            search = true,
            textExtraction = true,
            annotations = annotationProviderFactory != null,
            selection = true,
            links = true
        )

    private val store: Utf16TextStore by lazy {
        Utf16TextStore(files.textStore)
    }

    private val blockIndex: TxtBlockIndex by lazy {
        TxtBlockIndex.openIfValid(files.blockIdx, meta)
            ?: TxtBlockIndex.minimal(meta)
    }

    override suspend fun metadata(): ReaderResult<DocumentMetadata> {
        return ReaderResult.Ok(
            DocumentMetadata(
                title = source.displayName?.substringBeforeLast('.'),
                extra = mapOf(
                    "charset" to meta.originalCharset,
                    "lengthCodeUnits" to meta.lengthCodeUnits.toString()
                )
            )
        )
    }

    override suspend fun createSession(
        initialLocator: Locator?,
        initialConfig: RenderConfig
    ): ReaderResult<ReaderSession> {
        return withContext(defaultDispatcher) {
            try {
                val config = initialConfig as? RenderConfig.ReflowText
                    ?: return@withContext ReaderResult.Err(
                        ReaderError.Internal("TXT engine requires RenderConfig.ReflowText")
                    )
                val effectiveConfig = config.sanitized()
                val initialOffset = when {
                    initialLocator == null -> 0L
                    else -> TxtAnchorLocatorCodec.parseOffset(
                        locator = initialLocator,
                        blockIndex = blockIndex,
                        expectedRevision = meta.contentRevision,
                        maxOffset = store.lengthChars
                    ) ?: 0L
                }.coerceIn(0L, store.lengthChars)
                val annotationProvider = annotationProviderFactory?.invoke(id)
                val breakIndex = SoftBreakIndex.openIfValid(
                    file = files.breakMap,
                    meta = meta,
                    profile = SoftBreakTuningProfile.BALANCED,
                    rulesVersion = SoftBreakRuleConfig.forProfile(SoftBreakTuningProfile.BALANCED).rulesVersion
                )
                val breakResolver = BreakResolver(
                    store = store,
                    files = files,
                    meta = meta,
                    breakIndex = breakIndex
                )
                val blockStore = BlockStore(
                    store = store,
                    blockIndex = blockIndex,
                    revision = meta.contentRevision,
                    breakResolver = breakResolver
                )

                val controller = TxtController(
                    documentKey = id.value,
                    store = store,
                    meta = meta,
                    blockIndex = blockIndex,
                    breakResolver = breakResolver,
                    blockStore = blockStore,
                    initialLocator = initialLocator,
                    initialOffset = initialOffset,
                    initialConfig = effectiveConfig,
                    maxPageCache = maxPageCache,
                    persistPagination = persistPagination,
                    files = files,
                    annotationProvider = annotationProvider,
                    ioDispatcher = ioDispatcher,
                    paginationDispatcher = paginationDispatcher,
                    defaultDispatcher = defaultDispatcher
                )
                ReaderResult.Ok(
                    TxtSession.create(
                        controller = controller,
                        files = files,
                        meta = meta,
                        blockIndex = blockIndex,
                        breakResolver = breakResolver,
                        blockStore = blockStore,
                        ioDispatcher = ioDispatcher,
                        persistOutline = persistOutline,
                        annotationsProvider = annotationProvider
                    )
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                ReaderResult.Err(e.toReaderError())
            }
        }
    }

    override fun close() {
        runCatching { store.close() }
    }
}
