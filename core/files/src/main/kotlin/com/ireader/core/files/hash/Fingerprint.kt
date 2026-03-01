package com.ireader.core.files.hash

import java.security.MessageDigest

object Fingerprint {
    fun newSha256(): MessageDigest = MessageDigest.getInstance("SHA-256")

    fun sha256Hex(bytes: ByteArray): String {
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
