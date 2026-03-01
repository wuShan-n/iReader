package com.ireader.engines.epub.internal.locator

import com.ireader.engines.epub.internal.open.EpubContainer
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes

internal object EpubLocator {

    fun spineIndexOf(container: EpubContainer, locator: Locator?): Int? {
        if (locator == null) return null

        return when (locator.scheme) {
            LocatorSchemes.EPUB_CFI -> spineIndexFromEpubValue(container, locator.value)
            LocatorSchemes.REFLOW_PAGE -> parseReflowPage(locator.value)?.first
            else -> null
        }
    }

    fun parseReflowPage(value: String): Pair<Int, Int>? {
        val parts = value.split(':')
        if (parts.size < 2) return null

        val spine = parts[0].toIntOrNull() ?: return null
        val page = parts[1].toIntOrNull() ?: return null
        return spine to page
    }

    fun anchorFromEpubValue(value: String): String? =
        value.substringAfter('#', "").takeIf { it.isNotBlank() }

    fun spineIndexFromEpubValue(container: EpubContainer, value: String): Int? {
        return when {
            value.startsWith("spine:") -> value.removePrefix("spine:").toIntOrNull()
            value.startsWith("href:") -> {
                val href = value.removePrefix("href:").substringBefore('#')
                container.spineIndexOfHref(href)
            }

            else -> null
        }
    }
}
