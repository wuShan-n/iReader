package com.ireader.engines.pdf.internal.util

import java.io.Closeable

internal suspend inline fun <T : Closeable, R> T.useSafely(block: suspend (T) -> R): R {
    try {
        return block(this)
    } finally {
        runCatching { close() }
    }
}
