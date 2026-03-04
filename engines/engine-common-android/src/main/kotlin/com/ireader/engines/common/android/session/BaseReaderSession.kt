package com.ireader.engines.common.android.session

import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.api.provider.SelectionController
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.model.SessionId
import java.util.concurrent.atomic.AtomicBoolean

open class BaseReaderSession(
    final override val id: SessionId,
    final override val controller: ReaderController,
    final override val outline: OutlineProvider? = null,
    final override val search: SearchProvider? = null,
    final override val text: TextProvider? = null,
    final override val annotations: AnnotationProvider? = null,
    final override val resources: ResourceProvider? = null,
    final override val selection: SelectionProvider? = null,
    final override val selectionController: SelectionController? = null
) : ReaderSession {

    private val closed = AtomicBoolean(false)

    protected open fun closeExtras() = Unit

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { closeExtras() }
        runCatching { controller.close() }
    }
}
