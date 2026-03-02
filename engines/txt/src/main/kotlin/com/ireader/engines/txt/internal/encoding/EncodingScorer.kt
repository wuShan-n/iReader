package com.ireader.engines.txt.internal.encoding

import com.ireader.core.files.source.DocumentSource
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

internal object EncodingScorer {

    suspend fun pickBest(
        source: DocumentSource,
        sampleBytes: Int = 256 * 1024
    ): Charset {
        val candidates = listOf(
            Charsets.UTF_8,
            Charset.forName("GB18030"),
            Charset.forName("GBK"),
            Charset.forName("Big5"),
            Charset.forName("Shift_JIS"),
            Charset.forName("windows-1252")
        )

        val bytes = source.openInputStream().use { input ->
            val buf = ByteArray(sampleBytes)
            val read = input.read(buf)
            if (read <= 0) ByteArray(0) else buf.copyOf(read)
        }
        if (bytes.isEmpty()) {
            return Charsets.UTF_8
        }

        return candidates.maxBy { candidate ->
            score(candidate, bytes)
        }
    }

    private fun score(charset: Charset, bytes: ByteArray): Int {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        val decoded = decoder.decode(ByteBuffer.wrap(bytes)).toString()

        var printable = 0
        var replacement = 0
        var controls = 0

        for (c in decoded) {
            when {
                c == '\uFFFD' -> replacement++
                c == '\n' || c == '\r' || c == '\t' -> printable++
                c < ' ' -> controls++
                else -> printable++
            }
        }
        return printable * 2 - replacement * 50 - controls * 10
    }
}
