package com.ireader.engines.txt.internal.link

import com.ireader.engines.txt.internal.locator.TxtBlockLocatorCodec
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.LinkTarget

internal object LinkDetector {

    private val urlRegex = Regex("""(?i)\b(https?://[^\s<>"']+|www\.[^\s<>"']+)""")
    private val emailRegex = Regex("""\b[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}\b""")

    private val trimTailChars = setOf('.', ',', '，', '。', '!', '?', '！', '？', ')', ']', '}', '>', '"', '\'')

    fun detect(
        text: CharSequence,
        pageStartOffset: Long,
        maxOffset: Long,
        max: Int = 20
    ): List<DocumentLink> {
        if (text.isEmpty()) {
            return emptyList()
        }
        val source = text.toString()
        val maybeUrl = source.indexOf("http", ignoreCase = true) >= 0 ||
            source.indexOf("www.", ignoreCase = true) >= 0
        val maybeEmail = source.indexOf('@') >= 0
        if (!maybeUrl && !maybeEmail) {
            return emptyList()
        }
        val out = ArrayList<DocumentLink>(8)
        val dedupe = HashSet<String>()

        fun append(startInclusive: Int, endExclusive: Int, isEmail: Boolean) {
            val clampedStart = startInclusive.coerceIn(0, source.length)
            var clampedEnd = endExclusive.coerceIn(clampedStart, source.length)
            while (clampedEnd > clampedStart && trimTailChars.contains(source[clampedEnd - 1])) {
                clampedEnd--
            }
            if (clampedEnd <= clampedStart) {
                return
            }

            val value = source.substring(clampedStart, clampedEnd)
            val normalized = if (isEmail) {
                "mailto:$value"
            } else {
                normalizeUrl(value)
            }

            val dedupeKey = "$normalized|$clampedStart|$clampedEnd"
            if (!dedupe.add(dedupeKey)) {
                return
            }

            val globalStart = (pageStartOffset + clampedStart.toLong()).coerceAtMost(maxOffset)
            val globalEnd = (pageStartOffset + clampedEnd.toLong()).coerceAtMost(maxOffset)
            if (globalEnd <= globalStart) {
                return
            }

            out.add(
                DocumentLink(
                    target = LinkTarget.External(normalized),
                    title = value,
                    range = TxtBlockLocatorCodec.rangeForOffsets(
                        startOffset = globalStart,
                        endOffset = globalEnd,
                        maxOffset = maxOffset
                    ),
                    bounds = null
                )
            )
        }

        if (maybeUrl) {
            for (match in urlRegex.findAll(source)) {
                if (out.size >= max) {
                    break
                }
                append(
                    startInclusive = match.range.first,
                    endExclusive = match.range.last + 1,
                    isEmail = false
                )
            }
        }
        if (maybeEmail) {
            for (match in emailRegex.findAll(source)) {
                if (out.size >= max) {
                    break
                }
                append(
                    startInclusive = match.range.first,
                    endExclusive = match.range.last + 1,
                    isEmail = true
                )
            }
        }

        return out
    }

    private fun normalizeUrl(value: String): String {
        return if (value.startsWith("www.", ignoreCase = true)) {
            "http://$value"
        } else {
            value
        }
    }
}
