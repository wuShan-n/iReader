package com.ireader.engines.pdf.internal.util

import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.Progression
import kotlin.math.roundToInt

internal fun pageLocator(pageIndex: Int, pageCount: Int): Locator {
    val safePageCount = pageCount.coerceAtLeast(1)
    val safePageIndex = pageIndex.coerceIn(0, safePageCount - 1)
    return Locator(
        scheme = LocatorSchemes.PDF_PAGE,
        value = safePageIndex.toString(),
        extras = mapOf(
            "pageLabel" to "${safePageIndex + 1}/$safePageCount"
        )
    )
}

internal fun progressionForPage(pageIndex: Int, pageCount: Int): Progression {
    val safePageCount = pageCount.coerceAtLeast(1)
    val safePageIndex = pageIndex.coerceIn(0, safePageCount - 1)
    val denominator = (safePageCount - 1).coerceAtLeast(1)
    val percent = (safePageIndex.toDouble() / denominator.toDouble()).coerceIn(0.0, 1.0)
    return Progression(
        percent = percent,
        label = "${safePageIndex + 1}/$safePageCount",
        current = safePageIndex + 1,
        total = safePageCount
    )
}

internal fun Locator.toPdfPageIndexOrNull(pageCount: Int): Int? {
    if (scheme != LocatorSchemes.PDF_PAGE) return null
    val parsed = value.toIntOrNull() ?: return null
    return parsed.coerceIn(0, pageCount.coerceAtLeast(1) - 1)
}

internal fun progressionToPage(percent: Double, pageCount: Int): Int {
    val safePageCount = pageCount.coerceAtLeast(1)
    if (safePageCount <= 1) return 0
    return ((safePageCount - 1) * percent.coerceIn(0.0, 1.0)).roundToInt()
        .coerceIn(0, safePageCount - 1)
}

