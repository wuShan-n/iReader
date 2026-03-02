package com.ireader.engines.txt.internal.util

import java.security.MessageDigest

internal object Hashing {
    fun sha256Hex(input: String): String = sha256Hex(input.toByteArray(Charsets.UTF_8))

    fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(bytes)
        return md.digest().toHex()
    }

    private fun ByteArray.toHex(): String {
        val out = CharArray(size * 2)
        var i = 0
        for (b in this) {
            val v = b.toInt() and 0xFF
            out[i++] = HEX[v ushr 4]
            out[i++] = HEX[v and 0x0F]
        }
        return String(out)
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
