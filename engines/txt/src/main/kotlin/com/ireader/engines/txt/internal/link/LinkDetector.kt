package com.ireader.engines.txt.internal.link

import com.ireader.engines.txt.internal.locator.TxtAnchorLocatorCodec
import com.ireader.engines.txt.internal.open.TxtBlockIndex
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.LinkTarget

internal object LinkDetector {

    private val urlRegex = Regex("""(?i)\b(https?://[^\s<>"']+|www\.[^\s<>"']+)""")
    private val emailRegex = Regex("""\b[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}\b""")

    private val trimTailChars = setOf('.', ',', '，', '。', '!', '?', '！', '？', ')', ']', '}', '>', '"', '\'')

    fun detect(
        text: CharSequence,
        pageStartOffset: Long,
        blockIndex: TxtBlockIndex,
        revision: Int,
        projectedBoundaryToRawOffsets: LongArray? = null,
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

            val globalStart = if (projectedBoundaryToRawOffsets == null) {
                (pageStartOffset + clampedStart.toLong()).coerceAtMost(blockIndex.lengthCodeUnits)
            } else {
                projectedBoundaryToRawOffsets
                    .getOrElse(clampedStart) { projectedBoundaryToRawOffsets.last() }
                    .coerceAtMost(blockIndex.lengthCodeUnits)
            }
            val globalEnd = if (projectedBoundaryToRawOffsets == null) {
                (pageStartOffset + clampedEnd.toLong()).coerceAtMost(blockIndex.lengthCodeUnits)
            } else {
                projectedBoundaryToRawOffsets
                    .getOrElse(clampedEnd) { projectedBoundaryToRawOffsets.last() }
                    .coerceAtMost(blockIndex.lengthCodeUnits)
            }
            if (globalEnd <= globalStart) {
                return
            }

            out.add(
                DocumentLink(
                    target = LinkTarget.External(normalized),
                    title = value,
                    range = TxtAnchorLocatorCodec.rangeForOffsets(
                        startOffset = globalStart,
                        endOffset = globalEnd,
                        blockIndex = blockIndex,
                        revision = revision
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
