package com.ireader.engines.common.io

import java.io.Closeable

fun Closeable?.closeQuietly() {
    runCatching { this?.close() }
}

fun closeAllQuietly(vararg closeables: Closeable?) {
    for (closeable in closeables) {
        closeable.closeQuietly()
    }
}
