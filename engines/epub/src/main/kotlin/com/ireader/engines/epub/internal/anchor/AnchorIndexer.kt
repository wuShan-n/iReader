package com.ireader.engines.epub.internal.anchor

import com.ireader.engines.epub.internal.cache.SimpleLruCache
import com.ireader.engines.epub.internal.open.EpubContainer
import com.ireader.engines.epub.internal.parser.XmlDom
import java.io.File
import org.w3c.dom.Node

internal class AnchorIndexer(
    private val container: EpubContainer
) {
    private val cache = SimpleLruCache<Int, Map<String, Int>>(maxSize = 48)

    fun charOffsetFor(spineIndex: Int, anchorId: String): Int? {
        val map = cache.getOrPut(spineIndex) { buildIndex(spineIndex) }
        return map[anchorId]
    }

    private fun buildIndex(spineIndex: Int): Map<String, Int> {
        val file = File(container.rootDir, container.spinePath(spineIndex))
        if (!file.exists()) return emptyMap()

        return runCatching {
            val doc = XmlDom.parse(file)
            val body = XmlDom.descendants(doc.documentElement)
                .firstOrNull { XmlDom.localName(it).equals("body", ignoreCase = true) }
                ?: return@runCatching emptyMap()

            val map = HashMap<String, Int>(128)
            var charCount = 0
            walk(body) { node ->
                if (node.nodeType == Node.ELEMENT_NODE) {
                    val element = node as org.w3c.dom.Element
                    val id = XmlDom.attr(element, "id")?.trim()
                    val name = if (XmlDom.localName(element).equals("a", ignoreCase = true)) {
                        XmlDom.attr(element, "name")?.trim()
                    } else {
                        null
                    }
                    if (!id.isNullOrBlank()) map.putIfAbsent(id, charCount)
                    if (!name.isNullOrBlank()) map.putIfAbsent(name, charCount)
                } else if (node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE) {
                    val text = node.nodeValue.orEmpty()
                    if (text.isNotBlank()) {
                        charCount += text.length + 1
                    }
                }
            }
            map
        }.getOrDefault(emptyMap())
    }

    private fun walk(node: Node, block: (Node) -> Unit) {
        block(node)
        var child = node.firstChild
        while (child != null) {
            walk(child, block)
            child = child.nextSibling
        }
    }
}
