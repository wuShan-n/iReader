package com.ireader.engines.epub.internal.parser

import java.util.ArrayDeque

internal object PathResolver {

    fun resolveFrom(baseFileRel: String, href: String): String {
        val raw = href.substringBefore('#').trim()
        val baseDir = baseFileRel.substringBeforeLast('/', missingDelimiterValue = "").trim('/')

        val joined = when {
            raw.isBlank() -> baseFileRel
            baseDir.isBlank() -> raw
            else -> "$baseDir/$raw"
        }

        return normalizePath(joined)
    }

    fun fragmentOf(href: String): String = href.substringAfter('#', "")

    fun normalizePath(path: String): String {
        val segments = path
            .replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() }

        val stack = ArrayDeque<String>(segments.size)
        segments.forEach { seg ->
            when (seg) {
                "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(seg)
            }
        }
        return stack.joinToString("/")
    }
}
