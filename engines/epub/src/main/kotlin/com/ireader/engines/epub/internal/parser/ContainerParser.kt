package com.ireader.engines.epub.internal.parser

import java.io.File

internal object ContainerParser {

    fun parseOpfPath(containerXml: File): String? {
        val document = XmlDom.parse(containerXml)
        val root = document.documentElement ?: return null

        val rootfile = XmlDom.descendants(root)
            .firstOrNull { XmlDom.localName(it).equals("rootfile", ignoreCase = true) }
            ?: return null

        val fullPath = XmlDom.attr(rootfile, "full-path")?.trim().orEmpty()
        if (fullPath.isBlank()) return null
        return PathResolver.normalizePath(fullPath)
    }
}
