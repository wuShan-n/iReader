package com.ireader.engines.pdf.internal.util

import java.security.MessageDigest

internal fun sha1Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
    return buildString(digest.size * 2) {
        for (byte in digest) {
            append(((byte.toInt() ushr 4) and 0x0F).toString(16))
            append((byte.toInt() and 0x0F).toString(16))
        }
    }
}

