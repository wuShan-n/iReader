package com.ireader.engines.txt.internal.paging

import java.security.MessageDigest

internal object KeyHash {
    fun stableName(key: RenderKey): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toString().toByteArray(Charsets.UTF_8))
        return buildString(20) {
            for (i in 0 until 10) {
                val b = digest[i].toInt() and 0xFF
                append("0123456789abcdef"[b ushr 4])
                append("0123456789abcdef"[b and 0x0F])
            }
        }
    }
}
