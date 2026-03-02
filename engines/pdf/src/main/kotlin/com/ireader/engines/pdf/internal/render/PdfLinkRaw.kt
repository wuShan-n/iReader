package com.ireader.engines.pdf.internal.render

import android.graphics.RectF
import com.ireader.reader.model.LinkTarget

internal data class PdfLinkRaw(
    val target: LinkTarget,
    val bounds: RectF
)
