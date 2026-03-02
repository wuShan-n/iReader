package com.ireader.engines.pdf.internal.util

import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes

internal fun pdfPageLocator(pageIndex: Int): Locator {
    return Locator(
        scheme = LocatorSchemes.PDF_PAGE,
        value = pageIndex.toString()
    )
}

internal fun Locator.toPdfPageIndexOrNull(): Int? {
    if (scheme != LocatorSchemes.PDF_PAGE) return null
    return value.toIntOrNull()
}
