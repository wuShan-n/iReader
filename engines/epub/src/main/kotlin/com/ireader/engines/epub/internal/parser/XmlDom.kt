package com.ireader.engines.epub.internal.parser

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

internal object XmlDom {
    fun parse(file: File): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isIgnoringComments = true
            isCoalescing = true
        }
        return factory.newDocumentBuilder().parse(file)
    }

    fun children(node: Node): Sequence<Element> = sequence {
        var child = node.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                yield(child as Element)
            }
            child = child.nextSibling
        }
    }

    fun descendants(node: Node): Sequence<Element> = sequence {
        children(node).forEach { child ->
            yield(child)
            yieldAll(descendants(child))
        }
    }

    fun localName(element: Element): String {
        val local = element.localName
        if (!local.isNullOrBlank()) return local
        return element.tagName.substringAfter(':')
    }

    fun attr(element: Element, name: String): String? {
        if (element.hasAttribute(name)) {
            return element.getAttribute(name)
        }
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val node = attrs.item(i)
            val local = node.localName ?: node.nodeName.substringAfter(':')
            if (local.equals(name, ignoreCase = true)) {
                return node.nodeValue
            }
        }
        return null
    }

    fun textContentTrimmed(node: Node?): String? {
        val text = node?.textContent?.trim()
        return text?.takeIf { it.isNotBlank() }
    }
}
