package com.ireader.engines.epub.internal.text

import com.ireader.engines.epub.internal.parser.XmlDom
import java.io.File
import org.w3c.dom.Node

internal object XhtmlTextExtractor {

    fun extract(file: File): String {
        val doc = XmlDom.parse(file)
        val body = XmlDom.descendants(doc.documentElement)
            .firstOrNull { XmlDom.localName(it).equals("body", ignoreCase = true) }
            ?: return sanitize(doc.documentElement.textContent.orEmpty())

        val builder = StringBuilder(8192)
        appendNodeText(body, builder)
        return sanitize(builder.toString())
    }

    private fun appendNodeText(node: Node, out: StringBuilder) {
        var child = node.firstChild
        while (child != null) {
            when (child.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                    val text = child.nodeValue
                    if (!text.isNullOrBlank()) {
                        out.append(text).append(' ')
                    }
                }

                Node.ELEMENT_NODE -> {
                    appendNodeText(child, out)
                    val tag = child.nodeName.substringAfter(':').lowercase()
                    if (tag in BLOCK_TAGS) {
                        out.append(' ')
                    }
                }
            }
            child = child.nextSibling
        }
    }

    private fun sanitize(text: String): String {
        return text
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private val BLOCK_TAGS = setOf(
        "p", "div", "li", "h1", "h2", "h3", "h4", "h5", "h6",
        "section", "article", "blockquote", "tr", "td", "br"
    )
}
