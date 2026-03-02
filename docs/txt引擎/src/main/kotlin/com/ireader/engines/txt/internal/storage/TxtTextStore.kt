package com.ireader.engines.txt.internal.storage

import java.io.Closeable
import java.nio.charset.Charset

internal interface TxtTextStore : Closeable {
    val charset: Charset

    suspend fun totalChars(): Int

    suspend fun readRange(startChar: Int, endCharExclusive: Int): String

    suspend fun readChars(startChar: Int, maxChars: Int): String

    suspend fun readAround(charOffset: Int, maxChars: Int): String
}
