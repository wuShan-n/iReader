package com.ireader.engines.txt.internal.open

import com.ireader.engines.common.android.session.BaseReaderSession
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.model.SessionId
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher

internal class TxtSession(
    controller: ReaderController,
    files: TxtBookFiles,
    meta: TxtMeta,
    store: Utf16TextStore,
    ioDispatcher: CoroutineDispatcher,
    persistOutline: Boolean,
    annotationsProvider: AnnotationProvider?,
    providerFactory: TxtSessionProviderFactory = DefaultTxtSessionProviderFactory
) : BaseReaderSession(
    id = SessionId(UUID.randomUUID().toString()),
    controller = controller,
    outline = providerFactory.createOutlineProvider(
        files = files,
        meta = meta,
        store = store,
        ioDispatcher = ioDispatcher,
        persistOutline = persistOutline
    ),
    search = providerFactory.createSearchProvider(
        files = files,
        store = store,
        meta = meta,
        ioDispatcher = ioDispatcher
    ),
    text = providerFactory.createTextProvider(
        store = store,
        ioDispatcher = ioDispatcher
    ),
    annotations = annotationsProvider
)
