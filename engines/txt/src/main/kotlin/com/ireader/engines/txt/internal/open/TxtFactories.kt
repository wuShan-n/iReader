package com.ireader.engines.txt.internal.open

import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.txt.TxtEngineConfig
import com.ireader.engines.txt.internal.render.TxtController
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.Locator
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher

internal fun interface TxtOpenerFactory {
    fun create(cacheDir: File, ioDispatcher: CoroutineDispatcher): TxtOpener
}

internal object DefaultTxtOpenerFactory : TxtOpenerFactory {
    override fun create(cacheDir: File, ioDispatcher: CoroutineDispatcher): TxtOpener {
        return TxtOpener(cacheDir = cacheDir, ioDispatcher = ioDispatcher)
    }
}

internal interface TxtControllerFactory {
    fun create(
        documentKey: String,
        store: Utf16TextStore,
        meta: TxtMeta,
        initialLocator: Locator?,
        initialOffset: Long,
        initialConfig: RenderConfig.ReflowText,
        maxPageCache: Int,
        persistPagination: Boolean,
        files: TxtBookFiles,
        annotationProvider: AnnotationProvider?,
        ioDispatcher: CoroutineDispatcher,
        defaultDispatcher: CoroutineDispatcher
    ): ReaderController
}

internal object DefaultTxtControllerFactory : TxtControllerFactory {
    override fun create(
        documentKey: String,
        store: Utf16TextStore,
        meta: TxtMeta,
        initialLocator: Locator?,
        initialOffset: Long,
        initialConfig: RenderConfig.ReflowText,
        maxPageCache: Int,
        persistPagination: Boolean,
        files: TxtBookFiles,
        annotationProvider: AnnotationProvider?,
        ioDispatcher: CoroutineDispatcher,
        defaultDispatcher: CoroutineDispatcher
    ): ReaderController {
        return TxtController(
            documentKey = documentKey,
            store = store,
            meta = meta,
            initialLocator = initialLocator,
            initialOffset = initialOffset,
            initialConfig = initialConfig,
            maxPageCache = maxPageCache,
            persistPagination = persistPagination,
            files = files,
            annotationProvider = annotationProvider,
            ioDispatcher = ioDispatcher,
            defaultDispatcher = defaultDispatcher
        )
    }
}

internal interface TxtSessionFactory {
    fun create(
        controller: ReaderController,
        files: TxtBookFiles,
        meta: TxtMeta,
        store: Utf16TextStore,
        ioDispatcher: CoroutineDispatcher,
        persistOutline: Boolean,
        annotationsProvider: AnnotationProvider?
    ): ReaderSession
}

internal class DefaultTxtSessionFactory(
    private val providerFactory: TxtSessionProviderFactory = DefaultTxtSessionProviderFactory
) : TxtSessionFactory {
    override fun create(
        controller: ReaderController,
        files: TxtBookFiles,
        meta: TxtMeta,
        store: Utf16TextStore,
        ioDispatcher: CoroutineDispatcher,
        persistOutline: Boolean,
        annotationsProvider: AnnotationProvider?
    ): ReaderSession {
        return TxtSession(
            controller = controller,
            files = files,
            meta = meta,
            store = store,
            ioDispatcher = ioDispatcher,
            persistOutline = persistOutline,
            annotationsProvider = annotationsProvider,
            providerFactory = providerFactory
        )
    }
}

internal interface TxtDocumentFactory {
    fun create(
        id: DocumentId,
        source: DocumentSource,
        files: TxtBookFiles,
        meta: TxtMeta,
        openOptions: OpenOptions,
        config: TxtEngineConfig
    ): ReaderDocument
}

internal class DefaultTxtDocumentFactory(
    private val controllerFactory: TxtControllerFactory = DefaultTxtControllerFactory,
    private val sessionFactory: TxtSessionFactory = DefaultTxtSessionFactory()
) : TxtDocumentFactory {
    override fun create(
        id: DocumentId,
        source: DocumentSource,
        files: TxtBookFiles,
        meta: TxtMeta,
        openOptions: OpenOptions,
        config: TxtEngineConfig
    ): ReaderDocument {
        return TxtDocument(
            id = id,
            source = source,
            files = files,
            meta = meta,
            openOptions = openOptions,
            persistPagination = config.persistPagination,
            persistOutline = config.persistOutline,
            maxPageCache = config.maxPageCache,
            annotationProviderFactory = config.annotationProviderFactory,
            ioDispatcher = config.ioDispatcher,
            defaultDispatcher = config.defaultDispatcher,
            controllerFactory = controllerFactory,
            sessionFactory = sessionFactory
        )
    }
}
