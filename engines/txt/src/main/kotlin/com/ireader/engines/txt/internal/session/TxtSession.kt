package com.ireader.engines.txt.internal.session

import com.ireader.engines.txt.TxtEngineConfig
import com.ireader.engines.txt.internal.controller.TxtController
import com.ireader.engines.txt.internal.controller.TxtLocatorMapper
import com.ireader.engines.txt.internal.paging.TxtLastPositionStore
import com.ireader.engines.txt.internal.paging.TxtPager
import com.ireader.engines.txt.internal.paging.TxtPaginationStore
import com.ireader.engines.txt.internal.provider.TxtOutlineCache
import com.ireader.engines.txt.internal.provider.TxtOutlineProvider
import com.ireader.engines.txt.internal.provider.TxtSearchProvider
import com.ireader.engines.txt.internal.provider.TxtTextProvider
import com.ireader.engines.txt.internal.storage.TxtTextStore
import com.ireader.engines.txt.internal.util.toReaderError
import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.SessionId
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class TxtSession private constructor(
    override val id: SessionId,
    override val controller: ReaderController,
    override val outline: OutlineProvider?,
    override val search: SearchProvider?,
    override val text: TextProvider?,
    override val annotations: AnnotationProvider?,
    override val resources: ResourceProvider?
) : ReaderSession {

    override fun close() {
        runCatching { controller.close() }
    }

    companion object {
        suspend fun create(
            documentId: DocumentId,
            store: TxtTextStore,
            paginationStore: TxtPaginationStore,
            outlineCache: TxtOutlineCache,
            lastPositionStore: TxtLastPositionStore,
            explicitInitial: Boolean,
            initialStartChar: Int,
            initialConfig: RenderConfig.ReflowText,
            ioDispatcher: CoroutineDispatcher,
            engineConfig: TxtEngineConfig,
            locatorMapper: TxtLocatorMapper,
            annotationProvider: AnnotationProvider
        ): ReaderResult<ReaderSession> = withContext(ioDispatcher) {
            try {
                val pager = TxtPager(
                    store = store,
                    chunkSizeChars = engineConfig.chunkSizeChars
                )
                val controller = TxtController(
                    store = store,
                    pager = pager,
                    ioDispatcher = ioDispatcher,
                    annotations = annotationProvider,
                    paginationStore = paginationStore,
                    lastPositionStore = lastPositionStore,
                    explicitInitial = explicitInitial,
                    documentId = documentId,
                    initialStartChar = initialStartChar,
                    locatorMapper = locatorMapper,
                    engineConfig = engineConfig
                ).apply {
                    setConfig(initialConfig)
                }

                ReaderResult.Ok(
                    TxtSession(
                        id = SessionId(UUID.randomUUID().toString()),
                        controller = controller,
                        outline = TxtOutlineProvider(store, ioDispatcher, outlineCache),
                        search = TxtSearchProvider(
                            store = store,
                            ioDispatcher = ioDispatcher,
                            locatorMapper = locatorMapper,
                            defaultMaxHits = engineConfig.maxSearchHitsDefault
                        ),
                        text = TxtTextProvider(
                            store = store,
                            maxRangeChars = engineConfig.maxTextExtractChars
                        ),
                        annotations = annotationProvider,
                        resources = null
                    )
                )
            } catch (t: Throwable) {
                ReaderResult.Err(t.toReaderError())
            }
        }
    }
}
