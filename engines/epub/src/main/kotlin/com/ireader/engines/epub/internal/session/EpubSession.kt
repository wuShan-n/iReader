package com.ireader.engines.epub.internal.session

import com.ireader.engines.epub.internal.controller.EpubController
import com.ireader.engines.epub.internal.provider.EpubAnnotationProvider
import com.ireader.engines.epub.internal.provider.EpubOutlineProvider
import com.ireader.engines.epub.internal.provider.EpubResourceProvider
import com.ireader.engines.epub.internal.provider.EpubSearchProvider
import com.ireader.engines.epub.internal.provider.EpubSelectionProvider
import com.ireader.engines.epub.internal.provider.EpubTextProvider
import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.AnnotationStore
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.Locator
import com.ireader.reader.model.SessionId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.readium.r2.shared.publication.Publication

internal class EpubSession(
    override val id: SessionId,
    private val documentId: DocumentId,
    publication: Publication,
    initialLocator: Locator?,
    initialConfig: RenderConfig,
    annotationStore: AnnotationStore?
) : ReaderSession {

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val epubController = EpubController(
        publication = publication,
        sessionTag = id.value,
        initialLocator = initialLocator,
        initialConfig = initialConfig
    )

    override val controller: ReaderController = epubController
    override val outline: OutlineProvider? = EpubOutlineProvider(publication)
    override val search: SearchProvider? = EpubSearchProvider(publication)
    override val text: TextProvider? = EpubTextProvider(publication)
    override val resources: ResourceProvider? = EpubResourceProvider(publication)
    override val selection: SelectionProvider? = EpubSelectionProvider(
        navigatorProvider = { epubController.navigatorOrNull() }
    )

    private val annotationProviderInternal: EpubAnnotationProvider? = annotationStore?.let { store ->
        EpubAnnotationProvider(
            documentId = documentId,
            store = store,
            decorationsHost = epubController.decorationsHost,
            scope = sessionScope
        )
    }
    override val annotations: AnnotationProvider? = annotationProviderInternal

    override fun close() {
        annotationProviderInternal?.closeInternal()
        sessionScope.cancel()
        epubController.close()
    }
}
