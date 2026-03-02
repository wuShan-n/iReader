package com.ireader.engines.pdf.internal.session

import com.ireader.engines.common.android.session.BaseReaderSession
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.model.SessionId

internal class PdfSession(
    id: SessionId,
    controller: ReaderController,
    outline: OutlineProvider?,
    search: SearchProvider?,
    text: TextProvider?,
    annotations: AnnotationProvider?,
    selection: SelectionProvider?
) : BaseReaderSession(
    id = id,
    controller = controller,
    outline = outline,
    search = search,
    text = text,
    annotations = annotations,
    selection = selection
)
