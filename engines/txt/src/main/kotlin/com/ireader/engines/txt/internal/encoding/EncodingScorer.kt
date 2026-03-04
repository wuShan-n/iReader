package com.ireader.engines.txt.internal.encoding

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

internal object EncodingScorer {

    fun pickBest(samples: List<ByteArray>): Charset {
        val candidates = listOf(
            Charsets.UTF_8,
            Charset.forName("GB18030"),
            Charset.forName("GBK"),
            Charset.forName("Big5"),
            Charset.forName("Shift_JIS"),
            Charset.forName("windows-1252")
        )

        val nonEmpty = samples.filter(ByteArray::isNotEmpty)
        if (nonEmpty.isEmpty()) {
            return Charsets.UTF_8
        }

        return candidates.maxByOrNull { candidate ->
            nonEmpty.sumOf { bytes -> score(candidate, bytes) }
        } ?: Charsets.UTF_8
    }

    private fun score(charset: Charset, bytes: ByteArray): Int {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        val decoded = decoder.decode(ByteBuffer.wrap(bytes)).toString()

        if (decoded.isEmpty()) {
            return Int.MIN_VALUE
        }

        var replacement = 0
        var controls = 0
        var printable = 0
        var han = 0
        var hiragana = 0
        var katakana = 0
        var hangul = 0
        var latin = 0

        for (c in decoded) {
            when {
                c == '\uFFFD' -> replacement++

                c == '\n' || c == '\r' || c == '\t' -> printable++

                c < ' ' -> controls++

                else -> {
                    printable++
                    when (Character.UnicodeScript.of(c.code)) {
                        Character.UnicodeScript.HAN -> han++
                        Character.UnicodeScript.HIRAGANA -> hiragana++
                        Character.UnicodeScript.KATAKANA -> katakana++
                        Character.UnicodeScript.HANGUL -> hangul++
                        Character.UnicodeScript.LATIN -> latin++
                        else -> Unit
                    }
                }
            }
        }

        val total = decoded.length.coerceAtLeast(1)

        val replacementPenalty = replacement * 100_000 / total
        val controlPenalty = controls * 20_000 / total
        val hanBonus = han * 50_000 / total
        val kanaBonus = (hiragana + katakana) * 10_000 / total
        val hangulBonus = hangul * 50_000 / total
        val latinBonus = latin * 8_000 / total
        val printableBonus = printable * 2_000 / total

        return 1_000 +
            printableBonus +
            hanBonus +
            kanaBonus +
            hangulBonus +
            latinBonus -
            replacementPenalty -
            controlPenalty
    }
}
