package com.ireader.engines.common.hash

import java.security.MessageDigest

object Hashing {

    fun sha1Hex(input: String): String = digestHex("SHA-1", input.toByteArray(Charsets.UTF_8))

    fun sha256Hex(input: String): String = digestHex("SHA-256", input.toByteArray(Charsets.UTF_8))

    fun sha256Hex(bytes: ByteArray): String = digestHex("SHA-256", bytes)

    fun toHexLower(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        var i = 0
        for (b in bytes) {
            val value = b.toInt() and 0xFF
            out[i++] = HEX[value ushr 4]
            out[i++] = HEX[value and 0x0F]
        }
        return String(out)
    }

    private fun digestHex(algorithm: String, bytes: ByteArray): String {
        val digest = MessageDigest.getInstance(algorithm)
        digest.update(bytes)
        return toHexLower(digest.digest())
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
