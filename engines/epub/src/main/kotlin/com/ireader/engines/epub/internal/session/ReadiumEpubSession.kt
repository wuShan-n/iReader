package com.ireader.engines.epub.internal.session

import com.ireader.engines.epub.internal.provider.EpubAnnotationProvider
import com.ireader.engines.epub.internal.readium.ReadiumEpubController
import com.ireader.engines.epub.internal.readium.ReadiumLocatorMapper
import com.ireader.engines.epub.internal.readium.ReadiumNavigatorAdapter
import com.ireader.engines.epub.internal.readium.ReadiumOutlineProvider
import com.ireader.engines.epub.internal.readium.ReadiumPreferencesMapper
import com.ireader.engines.epub.internal.readium.ReadiumSearchProvider
import com.ireader.engines.epub.internal.readium.ReadiumTextProvider
import com.ireader.reader.api.engine.NavigatorCapableSession
import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.ReaderNavigatorAdapter
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.Locator
import com.ireader.reader.model.SessionId
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positionsByReadingOrder

internal class ReadiumEpubSession private constructor(
    override val id: SessionId,
    override val controller: ReaderController,
    override val navigatorAdapter: ReaderNavigatorAdapter,
    override val outline: OutlineProvider?,
    override val search: SearchProvider?,
    override val text: TextProvider?,
    override val annotations: AnnotationProvider?,
    override val resources: ResourceProvider?
) : NavigatorCapableSession {

    override fun close() {
        runCatching { controller.close() }
        runCatching { navigatorAdapter.close() }
    }

    companion object {
        suspend fun create(
            publication: Publication,
            initialLocator: Locator?,
            initialConfig: RenderConfig.ReflowText,
            ioDispatcher: CoroutineDispatcher
        ): ReaderResult<ReaderSession> = withContext(ioDispatcher) {
            val initialReadiumLocator = ReadiumLocatorMapper.toReadium(publication, initialLocator)
            val navigatorFactory = EpubNavigatorFactory(publication)
            val initialPreferences = ReadiumPreferencesMapper.fromRenderConfig(initialConfig)
            val navigatorAdapter = ReadiumNavigatorAdapter(
                publication = publication,
                navigatorFactory = navigatorFactory,
                initialLocator = initialReadiumLocator,
                initialPreferences = initialPreferences,
                ioDispatcher = ioDispatcher
            )

            val positions = runCatching {
                publication.positionsByReadingOrder()
                    .flatten()
                    .map(ReadiumLocatorMapper::toModel)
            }.getOrDefault(emptyList())

            val controller = ReadiumEpubController(
                navigatorAdapter = navigatorAdapter,
                ioDispatcher = ioDispatcher,
                initialConfig = initialConfig,
                initialLocator = initialLocator,
                positionLocators = positions
            )

            ReaderResult.Ok(
                ReadiumEpubSession(
                    id = SessionId(UUID.randomUUID().toString()),
                    controller = controller,
                    navigatorAdapter = navigatorAdapter,
                    outline = ReadiumOutlineProvider(publication),
                    search = ReadiumSearchProvider(publication),
                    text = ReadiumTextProvider(publication),
                    annotations = EpubAnnotationProvider(),
                    resources = null
                )
            )
        }
    }
}
