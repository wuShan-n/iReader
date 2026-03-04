package com.ireader.engines.pdf.internal.open

import com.ireader.engines.pdf.internal.backend.PdfBackend
import java.io.Closeable

internal data class OpenedPdf(
    val backend: PdfBackend,
    val cleanup: Closeable,
    val degradedBackend: Boolean
)
