package com.ireader.engines.epub.internal.session

import com.ireader.engines.epub.internal.controller.EpubController
import com.ireader.engines.epub.internal.open.EpubContainer
import com.ireader.engines.epub.internal.pagination.EpubPageMetricsStore
import com.ireader.engines.epub.internal.provider.EpubAnnotationProvider
import com.ireader.engines.epub.internal.provider.EpubOutlineProvider
import com.ireader.engines.epub.internal.provider.EpubResourceProvider
import com.ireader.engines.epub.internal.provider.EpubSearchProvider
import com.ireader.engines.epub.internal.provider.EpubTextProvider
import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.Locator
import com.ireader.reader.model.SessionId
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class EpubSession private constructor(
    override val id: SessionId,
    override val controller: ReaderController,
    override val outline: OutlineProvider?,
    override val search: SearchProvider?,
    override val text: TextProvider?,
    override val annotations: AnnotationProvider?,
    override val resources: ResourceProvider?,
    private val metricsStore: EpubPageMetricsStore
) : ReaderSession {

    override fun close() {
        runCatching { controller.close() }
        runCatching { metricsStore.flush() }
    }

    companion object {
        suspend fun create(
            container: EpubContainer,
            initialLocator: Locator?,
            initialConfig: RenderConfig.ReflowText,
            ioDispatcher: CoroutineDispatcher
        ): ReaderResult<ReaderSession> = withContext(ioDispatcher) {
            val annotationProvider = EpubAnnotationProvider()
            val textProvider = EpubTextProvider(container, ioDispatcher)
            val metricsStore = EpubPageMetricsStore(
                file = File(container.baseDir, "metrics.properties")
            )
            val controller = EpubController(
                container = container,
                initialLocator = initialLocator,
                initialConfig = initialConfig,
                annotations = annotationProvider,
                ioDispatcher = ioDispatcher,
                metricsStore = metricsStore
            )

            ReaderResult.Ok(
                EpubSession(
                    id = SessionId(UUID.randomUUID().toString()),
                    controller = controller,
                    outline = EpubOutlineProvider(container),
                    search = EpubSearchProvider(container, textProvider),
                    text = textProvider,
                    annotations = annotationProvider,
                    resources = EpubResourceProvider(container, ioDispatcher),
                    metricsStore = metricsStore
                )
            )
        }
    }
}
