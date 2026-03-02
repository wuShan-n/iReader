package com.ireader.engines.txt.internal.link

import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.LinkTarget

internal object LinkDetector {

    private val urlRegex = Regex("""(?i)\b(https?://[^\s<>"']+|www\.[^\s<>"']+)""")
    private val emailRegex = Regex("""\b[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}\b""")

    private val trimTailChars = setOf('.', ',', '，', '。', '!', '?', '！', '？', ')', ']', '}', '>', '"', '\'')

    fun detect(text: CharSequence, max: Int = 20): List<DocumentLink> {
        if (text.isEmpty()) {
            return emptyList()
        }
        val source = text.toString()
        val out = ArrayList<DocumentLink>(8)
        val dedupe = HashSet<String>()

        fun append(raw: String, isEmail: Boolean) {
            var value = raw
            while (value.isNotEmpty() && trimTailChars.contains(value.last())) {
                value = value.dropLast(1)
            }
            if (value.isBlank()) {
                return
            }
            val normalized = if (isEmail) {
                "mailto:$value"
            } else {
                normalizeUrl(value)
            }
            if (!dedupe.add(normalized)) {
                return
            }
            out.add(
                DocumentLink(
                    target = LinkTarget.External(normalized),
                    title = value,
                    bounds = null
                )
            )
        }

        for (match in urlRegex.findAll(source)) {
            if (out.size >= max) {
                break
            }
            append(match.value, isEmail = false)
        }
        for (match in emailRegex.findAll(source)) {
            if (out.size >= max) {
                break
            }
            append(match.value, isEmail = true)
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
