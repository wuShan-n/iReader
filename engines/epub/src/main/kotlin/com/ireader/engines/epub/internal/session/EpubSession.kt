package com.ireader.engines.epub.internal.session

import com.ireader.engines.common.android.session.BaseReaderSession
import com.ireader.engines.epub.internal.controller.EpubController
import com.ireader.engines.epub.internal.provider.EpubAnnotationProvider
import com.ireader.engines.epub.internal.provider.EpubOutlineProvider
import com.ireader.engines.epub.internal.provider.EpubResourceProvider
import com.ireader.engines.epub.internal.provider.EpubSearchProvider
import com.ireader.engines.epub.internal.provider.EpubSelectionProvider
import com.ireader.engines.epub.internal.provider.EpubTextProvider
import com.ireader.reader.api.provider.AnnotationStore
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.Locator
import com.ireader.reader.model.SessionId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.readium.r2.shared.publication.Publication

internal class EpubSession private constructor(
    id: SessionId,
    documentId: DocumentId,
    private val parts: SessionParts
) : BaseReaderSession(
    id = id,
    controller = parts.controller,
    outline = parts.outline,
    search = parts.search,
    text = parts.text,
    resources = parts.resources,
    selection = parts.selection,
    annotations = parts.annotations
) {

    override fun closeExtras() {
        parts.annotations?.closeInternal()
        parts.scope.cancel()
    }

    companion object {
        fun create(
            id: SessionId,
            documentId: DocumentId,
            publication: Publication,
            initialLocator: Locator?,
            initialConfig: RenderConfig,
            annotationStore: AnnotationStore?
        ): EpubSession {
            val parts = buildParts(
                sessionId = id,
                documentId = documentId,
                publication = publication,
                initialLocator = initialLocator,
                initialConfig = initialConfig,
                annotationStore = annotationStore
            )
            return EpubSession(
                id = id,
                documentId = documentId,
                parts = parts
            )
        }
    }
}

private data class SessionParts(
    val scope: CoroutineScope,
    val controller: EpubController,
    val outline: EpubOutlineProvider,
    val search: EpubSearchProvider,
    val text: EpubTextProvider,
    val resources: EpubResourceProvider,
    val selection: EpubSelectionProvider,
    val annotations: EpubAnnotationProvider?
)

private fun buildParts(
    sessionId: SessionId,
    documentId: DocumentId,
    publication: Publication,
    initialLocator: Locator?,
    initialConfig: RenderConfig,
    annotationStore: AnnotationStore?
): SessionParts {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val controller = EpubController(
        publication = publication,
        sessionTag = sessionId.value,
        initialLocator = initialLocator,
        initialConfig = initialConfig
    )

    val annotations = annotationStore?.let { store ->
        EpubAnnotationProvider(
            documentId = documentId,
            store = store,
            decorationsHost = controller.decorationsHost,
            scope = scope
        )
    }

    return SessionParts(
        scope = scope,
        controller = controller,
        outline = EpubOutlineProvider(publication),
        search = EpubSearchProvider(publication),
        text = EpubTextProvider(publication),
        resources = EpubResourceProvider(publication),
        selection = EpubSelectionProvider(
            navigatorProvider = { controller.navigatorOrNull() }
        ),
        annotations = annotations
    )
}
