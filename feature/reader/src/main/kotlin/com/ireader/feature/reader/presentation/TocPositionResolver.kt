package com.ireader.feature.reader.presentation

import com.ireader.reader.model.Locator

internal const val TOC_EXTRA_HREF: String = "href"
internal const val TOC_EXTRA_POSITION: String = "position"
internal const val TOC_EXTRA_TOTAL_PROGRESSION: String = "totalProgression"
internal const val TOC_EXTRA_PROGRESSION: String = "progression"

internal fun resolveActiveTocIndex(
    items: List<TocItem>,
    currentLocator: Locator?
): Int? {
    if (items.isEmpty() || currentLocator == null) return null

    val currentHref = normalizeHref(currentLocator.extras[TOC_EXTRA_HREF])
    val currentPosition = currentLocator.extras[TOC_EXTRA_POSITION]?.toIntOrNull()
    val currentProgression = currentLocator.extras[TOC_EXTRA_TOTAL_PROGRESSION]?.toDoubleOrNull()
        ?: currentLocator.extras[TOC_EXTRA_PROGRESSION]?.toDoubleOrNull()

    if (currentHref != null) {
        val candidates = items.withIndex()
            .filter { normalizeHref(it.value.href) == currentHref }
        if (candidates.isNotEmpty()) {
            if (currentPosition != null) {
                candidates.lastOrNull { candidate ->
                    val position = candidate.value.position
                    position != null && position <= currentPosition
                }?.let { return it.index }
            }
            if (currentProgression != null) {
                candidates.lastOrNull { candidate ->
                    val progression = candidate.value.progression
                    progression != null && progression <= currentProgression
                }?.let { return it.index }
            }
            return candidates.first().index
        }
    }

    if (currentPosition != null) {
        items.withIndex()
            .lastOrNull { candidate ->
                val position = candidate.value.position
                position != null && position <= currentPosition
            }
            ?.let { return it.index }
    }

    if (currentProgression != null) {
        items.withIndex()
            .lastOrNull { candidate ->
                val progression = candidate.value.progression
                progression != null && progression <= currentProgression
            }
            ?.let { return it.index }
    }

    val locatorValue = currentLocator.value
    if (locatorValue.isBlank()) return null
    val index = items.indexOfFirst { it.locatorValue == locatorValue }
    return index.takeIf { it >= 0 }
}

private fun normalizeHref(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return value.substringBefore('#')
}
