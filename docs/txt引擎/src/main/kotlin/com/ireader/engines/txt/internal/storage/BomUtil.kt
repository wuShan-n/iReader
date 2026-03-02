package com.ireader.engines.txt.internal.storage

import java.nio.charset.Charset

internal object BomUtil {

    fun bomSkipBytes(header: ByteArray, charset: Charset): Int {
        // UTF-8 BOM: EF BB BF
        if (
            header.size >= 3 &&
            header[0] == 0xEF.toByte() &&
            header[1] == 0xBB.toByte() &&
            header[2] == 0xBF.toByte()
        ) {
            return 3
        }

        // UTF-16 LE/BE
        if (
            header.size >= 2 &&
            (
                (header[0] == 0xFF.toByte() && header[1] == 0xFE.toByte()) ||
                    (header[0] == 0xFE.toByte() && header[1] == 0xFF.toByte())
                )
        ) {
            return 2
        }

        return 0
    }
}
