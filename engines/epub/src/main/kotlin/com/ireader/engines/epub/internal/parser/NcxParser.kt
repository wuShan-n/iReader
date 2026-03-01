package com.ireader.engines.epub.internal.parser

import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.OutlineNode
import java.io.File
import org.w3c.dom.Element

internal object NcxParser {

    fun parse(ncxFile: File, ncxRelPath: String): List<OutlineNode> {
        val document = XmlDom.parse(ncxFile)
        val root = document.documentElement

        val navMap = XmlDom.descendants(root)
            .firstOrNull { XmlDom.localName(it).equals("navMap", ignoreCase = true) }
            ?: return emptyList()

        return XmlDom.children(navMap)
            .filter { XmlDom.localName(it).equals("navPoint", ignoreCase = true) }
            .mapNotNull { navPoint -> parseNavPoint(navPoint, ncxRelPath) }
            .toList()
    }

    private fun parseNavPoint(navPoint: Element, ncxRelPath: String): OutlineNode? {
        val labelNode = XmlDom.descendants(navPoint)
            .firstOrNull { XmlDom.localName(it).equals("text", ignoreCase = true) }
        val contentNode = XmlDom.descendants(navPoint)
            .firstOrNull { XmlDom.localName(it).equals("content", ignoreCase = true) }

        val title = XmlDom.textContentTrimmed(labelNode)
        val src = contentNode?.let { XmlDom.attr(it, "src")?.trim() }

        val children = XmlDom.children(navPoint)
            .filter { XmlDom.localName(it).equals("navPoint", ignoreCase = true) }
            .mapNotNull { child -> parseNavPoint(child, ncxRelPath) }
            .toList()

        if (title.isNullOrBlank() || src.isNullOrBlank()) {
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

        val resolved = PathResolver.resolveFrom(ncxRelPath, src)
        val fragment = PathResolver.fragmentOf(src)
        val value = if (fragment.isBlank()) "href:$resolved" else "href:$resolved#$fragment"

        return OutlineNode(
            title = title,
            locator = Locator(LocatorSchemes.EPUB_CFI, value),
            children = children
        )
    }
}
