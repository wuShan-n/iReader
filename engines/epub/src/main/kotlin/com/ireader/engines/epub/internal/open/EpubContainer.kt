package com.ireader.engines.epub.internal.open

import android.net.Uri
import com.ireader.engines.epub.internal.content.EpubUri
import com.ireader.engines.epub.internal.parser.model.EpubPackage
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.OutlineNode

internal data class EpubContainer(
    val id: DocumentId,
    val rootDir: java.io.File,
    val authority: String,
    val opf: EpubPackage,
    val outline: List<OutlineNode>
) {
    val spineCount: Int get() = opf.spine.size

    private val spineIndexByHref: Map<String, Int> =
        opf.spine.mapIndexed { index, item -> item.href to index }.toMap()

    private val titleByHref: Map<String, String> = buildMap {
        fun walk(nodes: List<OutlineNode>) {
            nodes.forEach { node ->
                val href = node.locator.value
                    .takeIf { it.startsWith("href:") }
                    ?.removePrefix("href:")
                    ?.substringBefore('#')
                if (!href.isNullOrBlank() && !containsKey(href)) {
                    put(href, node.title)
                }
                if (node.children.isNotEmpty()) {
                    walk(node.children)
                }
            }
        }
        walk(outline)
    }

    fun spinePath(index: Int): String = opf.spine[index].href

    fun spineUri(index: Int): Uri = EpubUri.buildFileUri(authority, id.value, spinePath(index))

    fun spineIndexOfHref(href: String): Int? = spineIndexByHref[href]

    fun titleForSpine(index: Int): String? {
        val href = opf.spine.getOrNull(index)?.href ?: return null
        return titleByHref[href]
    }
}
