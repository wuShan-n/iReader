package com.ireader.engines.txt.internal.open

import com.ireader.engines.txt.internal.provider.TxtOutlineProvider
import com.ireader.engines.txt.internal.provider.TxtSearchProviderPro
import com.ireader.engines.txt.internal.provider.TxtTextProvider
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.model.SessionId
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher

internal class TxtSession(
    override val controller: ReaderController,
    files: TxtBookFiles,
    meta: TxtMeta,
    store: Utf16TextStore,
    ioDispatcher: CoroutineDispatcher,
    persistOutline: Boolean,
    private val annotationsProvider: AnnotationProvider?
) : ReaderSession {

    override val id: SessionId = SessionId(UUID.randomUUID().toString())

    override val outline: OutlineProvider? =
        TxtOutlineProvider(
            files = files,
            meta = meta,
            store = store,
            ioDispatcher = ioDispatcher,
            persistOutline = persistOutline
        )

    override val search: SearchProvider? =
        TxtSearchProviderPro(
            files = files,
            store = store,
            meta = meta,
            ioDispatcher = ioDispatcher
        )

    override val text: TextProvider? =
        TxtTextProvider(
            store = store,
            ioDispatcher = ioDispatcher
        )

    override val annotations: AnnotationProvider? = annotationsProvider

    override val resources: ResourceProvider? = null
    override val selection: SelectionProvider? = null

    override fun close() {
        controller.close()
    }
}
