package com.ireader.reader.testkit.fake

import com.ireader.reader.api.engine.DocumentCapabilities

fun fixedCapabilities(
    outline: Boolean = false,
    search: Boolean = false,
    textExtraction: Boolean = false,
    annotations: Boolean = false,
    selection: Boolean = false,
    links: Boolean = false
): DocumentCapabilities {
    return DocumentCapabilities(
        reflowable = false,
        fixedLayout = true,
        outline = outline,
        search = search,
        textExtraction = textExtraction,
        annotations = annotations,
        selection = selection,
        links = links
    )
}

fun reflowCapabilities(
    outline: Boolean = false,
    search: Boolean = false,
    textExtraction: Boolean = false,
    annotations: Boolean = false,
    selection: Boolean = false,
    links: Boolean = false
): DocumentCapabilities {
    return fixedCapabilities(
        outline = outline,
        search = search,
        textExtraction = textExtraction,
        annotations = annotations,
        selection = selection,
        links = links
    ).copy(
        reflowable = true,
        fixedLayout = false
    )
}
