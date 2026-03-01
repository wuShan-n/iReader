package com.ireader.engines.epub.internal.parser

import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.OutlineNode
import java.io.File
import org.w3c.dom.Element

internal object NavParser {

    fun parse(navFile: File, navRelPath: String): List<OutlineNode> {
        val document = XmlDom.parse(navFile)
        val root = document.documentElement

        val navElement = XmlDom.descendants(root)
            .filter { XmlDom.localName(it).equals("nav", ignoreCase = true) }
            .firstOrNull { isTocNav(it) }
            ?: XmlDom.descendants(root)
                .firstOrNull { XmlDom.localName(it).equals("nav", ignoreCase = true) }
            ?: return emptyList()

        val ol = XmlDom.children(navElement)
            .firstOrNull { XmlDom.localName(it).equals("ol", ignoreCase = true) }
            ?: return emptyList()

        return parseOl(ol, navRelPath)
    }

    private fun parseOl(ol: Element, navRelPath: String): List<OutlineNode> {
        return XmlDom.children(ol)
            .filter { XmlDom.localName(it).equals("li", ignoreCase = true) }
            .mapNotNull { li -> parseLi(li, navRelPath) }
            .toList()
    }

    private fun parseLi(li: Element, navRelPath: String): OutlineNode? {
        val anchor = XmlDom.descendants(li)
            .firstOrNull { XmlDom.localName(it).equals("a", ignoreCase = true) }

        val href = anchor?.let { XmlDom.attr(it, "href")?.trim() }
        val title = XmlDom.textContentTrimmed(anchor)

        val childOl = XmlDom.children(li)
            .firstOrNull { XmlDom.localName(it).equals("ol", ignoreCase = true) }

        val children = childOl?.let { parseOl(it, navRelPath) }.orEmpty()

        if (href.isNullOrBlank() || title.isNullOrBlank()) {
            return if (children.isNotEmpty() && !title.isNullOrBlank()) {
                OutlineNode(
                    title = title,
                    locator = children.first().locator,
                    children = children
                )
            } else {
                null
            }
        }

        return OutlineNode(
            title = title,
            locator = Locator(
                scheme = LocatorSchemes.EPUB_CFI,
                value = hrefLocatorValue(navRelPath, href)
            ),
            children = children
        )
    }

    private fun isTocNav(nav: Element): Boolean {
        val rawType = listOfNotNull(
            XmlDom.attr(nav, "epub:type"),
            XmlDom.attr(nav, "type")
        ).joinToString(" ")

        return rawType
            .split(' ')
            .any { token -> token.equals("toc", ignoreCase = true) }
    }

    private fun hrefLocatorValue(baseRelPath: String, href: String): String {
        val resolved = PathResolver.resolveFrom(baseRelPath, href)
        val fragment = PathResolver.fragmentOf(href)
        return if (fragment.isBlank()) {
            "href:$resolved"
        } else {
            "href:$resolved#$fragment"
        }
    }
}
