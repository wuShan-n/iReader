package com.ireader.engines.pdf.internal.util

import java.io.Closeable

internal fun Closeable?.closeQuietly() {
    runCatching { this?.close() }
}

