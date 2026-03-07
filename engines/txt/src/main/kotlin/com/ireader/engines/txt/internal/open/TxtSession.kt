package com.ireader.engines.txt.internal.open

import com.ireader.engines.common.android.session.BaseReaderSession
import com.ireader.engines.txt.internal.provider.TxtOutlineProvider
import com.ireader.engines.txt.internal.provider.TxtSearchProviderPro
import com.ireader.engines.txt.internal.provider.TxtSelectionManager
import com.ireader.engines.txt.internal.provider.TxtTextProvider
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
    blockIndex: TxtBlockIndex,
    store: Utf16TextStore,
    ioDispatcher: CoroutineDispatcher,
    persistOutline: Boolean,
    annotationsProvider: AnnotationProvider?,
    selectionManager: TxtSelectionManager = TxtSelectionManager(
        store = store,
        ioDispatcher = ioDispatcher
    )
) : BaseReaderSession(
    id = SessionId(UUID.randomUUID().toString()),
    controller = controller,
    outline = TxtOutlineProvider(
        files = files,
        meta = meta,
        blockIndex = blockIndex,
        store = store,
        ioDispatcher = ioDispatcher,
        persistOutline = persistOutline
    ),
    search = TxtSearchProviderPro(
        files = files,
        store = store,
        meta = meta,
        blockIndex = blockIndex,
        ioDispatcher = ioDispatcher
    ),
    text = TxtTextProvider(
        store = store,
        ioDispatcher = ioDispatcher
    ),
    annotations = annotationsProvider,
    selection = selectionManager,
    selectionController = selectionManager
)
