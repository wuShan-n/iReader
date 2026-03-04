package com.ireader.engines.epub.internal.open

import java.util.Locale
import org.readium.r2.shared.publication.Publication

internal fun Publication.isFixedLayoutPublication(): Boolean {
    return detectEpubFixedLayout(
        metadataOther = metadata.otherMetadata,
        readingOrderLayoutHints = readingOrder.map { link ->
            link.properties["layout"] as? String
        }
    )
}

internal fun detectEpubFixedLayout(
    metadataOther: Map<String, Any>,
    readingOrderLayoutHints: List<String?>
): Boolean {
    val metadataHint = metadataLayoutHint(metadataOther)
    if (metadataHint != null) {
        return metadataHint == LAYOUT_FIXED || metadataHint == LAYOUT_PRE_PAGINATED
    }

    if (readingOrderLayoutHints.isEmpty()) {
        return false
    }

    var hasFixedResource = false
    for (raw in readingOrderLayoutHints) {
        when (normalizeLayout(raw)) {
            LAYOUT_FIXED,
            LAYOUT_PRE_PAGINATED -> hasFixedResource = true
            LAYOUT_REFLOWABLE -> return false
            null -> return false
            else -> return false
        }
    }
    return hasFixedResource
}

private fun metadataLayoutHint(metadataOther: Map<String, Any>): String? {
    val presentation = metadataOther["presentation"] as? Map<*, *>
    val presentationLayout = presentation?.get("layout") as? String
    val legacyRenditionLayout = metadataOther["rendition:layout"] as? String
    val legacyLayout = metadataOther["layout"] as? String
    return normalizeLayout(presentationLayout ?: legacyRenditionLayout ?: legacyLayout)
}

private fun normalizeLayout(raw: String?): String? {
    return raw?.trim()?.lowercase(Locale.ROOT)
}

private const val LAYOUT_FIXED = "fixed"
private const val LAYOUT_PRE_PAGINATED = "pre-paginated"
private const val LAYOUT_REFLOWABLE = "reflowable"
