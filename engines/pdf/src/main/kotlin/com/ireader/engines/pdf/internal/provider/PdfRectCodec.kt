package com.ireader.engines.pdf.internal.provider

import com.ireader.reader.model.NormalizedRect
import java.util.Locale

internal object PdfRectCodec {
    const val EXTRA_RECTS = "pdf.rects"

    fun encode(rects: List<NormalizedRect>): String {
        return rects.joinToString("|") { r ->
            String.format(
                Locale.US,
                "%.4f,%.4f,%.4f,%.4f",
                r.left,
                r.top,
                r.right,
                r.bottom
            )
        }
    }
}
