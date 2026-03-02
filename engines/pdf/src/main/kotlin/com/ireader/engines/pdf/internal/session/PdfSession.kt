package com.ireader.engines.pdf.internal.session

import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.model.SessionId

internal class PdfSession(
    override val id: SessionId,
    override val controller: ReaderController,
    override val outline: OutlineProvider?,
    override val search: SearchProvider?,
    override val text: TextProvider?,
    override val annotations: AnnotationProvider?,
    override val selection: SelectionProvider?
) : ReaderSession {

    override val resources: ResourceProvider? = null

    override fun close() {
        controller.close()
    }
}

