package com.ireader.engines.epub.internal.link

import com.ireader.engines.epub.internal.cache.SimpleLruCache
import com.ireader.engines.epub.internal.open.EpubContainer
import com.ireader.engines.epub.internal.parser.PathResolver
import com.ireader.engines.epub.internal.parser.XmlDom
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.LinkTarget
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import java.io.File

internal class LinkExtractor(
    private val container: EpubContainer
) {
    private val cache = SimpleLruCache<Int, List<DocumentLink>>(maxSize = 48)

    fun linksForSpine(spineIndex: Int): List<DocumentLink> {
        return cache.getOrPut(spineIndex) {
            val baseRelPath = container.spinePath(spineIndex)
            val file = File(container.rootDir, baseRelPath)
            if (!file.exists()) return@getOrPut emptyList()

            runCatching {
                val doc = XmlDom.parse(file)
                XmlDom.descendants(doc.documentElement)
                    .filter { XmlDom.localName(it).equals("a", ignoreCase = true) }
                    .mapNotNull { anchor ->
                        val href = XmlDom.attr(anchor, "href")?.trim().orEmpty()
                        if (href.isBlank()) return@mapNotNull null
                        if (href.startsWith("javascript:", ignoreCase = true)) return@mapNotNull null

                        val title = XmlDom.textContentTrimmed(anchor)
                        if (isAbsoluteScheme(href)) {
                            DocumentLink(
                                target = LinkTarget.External(url = href),
                                title = title
                            )
                        } else {
                            val resolved = PathResolver.resolveFrom(baseRelPath, href)
                            val fragment = PathResolver.fragmentOf(href)
                            val value = if (fragment.isBlank()) {
                                "href:$resolved"
                            } else {
                                "href:$resolved#$fragment"
                            }
                            DocumentLink(
                                target = LinkTarget.Internal(
                                    locator = Locator(
                                        scheme = LocatorSchemes.EPUB_CFI,
                                        value = value
                                    )
                                ),
                                title = title
                            )
                        }
                    }
                    .toList()
            }.getOrDefault(emptyList())
        }
    }

    private fun isAbsoluteScheme(href: String): Boolean {
        return SCHEME_REGEX.containsMatchIn(href)
    }

    private companion object {
        private val SCHEME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    }
}
