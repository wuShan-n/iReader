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
    metadataLayoutHint(metadataOther)?.let { hint ->
        return hint == LAYOUT_FIXED || hint == LAYOUT_PRE_PAGINATED
    }

    if (readingOrderLayoutHints.isEmpty()) return false

    val normalized = readingOrderLayoutHints.map(::normalizeLayout)
    if (normalized.any { it == null }) return false

    // 只要出现明确的 reflowable，就不是固定版式
    if (normalized.any { it == LAYOUT_REFLOWABLE }) return false

    // 任何未知字符串都视为不可靠 -> false（与原逻辑一致）
    val fixedSet = setOf(LAYOUT_FIXED, LAYOUT_PRE_PAGINATED)
    if (normalized.any { it !in fixedSet }) return false

    // 至少有一个固定资源才算 fixed layout
    return normalized.any { it in fixedSet }
}

private fun metadataLayoutHint(metadataOther: Map<String, Any>): String? {
    val presentation = metadataOther["presentation"] as? Map<*, *>
    val presentationLayout = presentation?.get("layout") as? String
    val legacyRenditionLayout = metadataOther["rendition:layout"] as? String
    val legacyLayout = metadataOther["layout"] as? String
    return normalizeLayout(presentationLayout ?: legacyRenditionLayout ?: legacyLayout)
}

private fun normalizeLayout(raw: String?): String? =
    raw?.trim()?.lowercase(Locale.ROOT)

private const val LAYOUT_FIXED = "fixed"
private const val LAYOUT_PRE_PAGINATED = "pre-paginated"
private const val LAYOUT_REFLOWABLE = "reflowable"
