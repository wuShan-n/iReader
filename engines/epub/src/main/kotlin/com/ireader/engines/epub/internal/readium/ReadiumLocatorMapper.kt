package com.ireader.engines.epub.internal.readium

import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Url

internal object ReadiumLocatorMapper {

    fun toModel(locator: org.readium.r2.shared.publication.Locator): Locator {
        val cfi = locator.locations.fragments.firstOrNull { it.startsWith("epubcfi(") }
        val fragment = locator.locations.fragments.firstOrNull()
        val value = cfi
            ?: if (!fragment.isNullOrBlank()) {
                "href:${locator.href}#$fragment"
            } else {
                "href:${locator.href}"
            }

        val extras = buildMap {
            put("href", locator.href.toString())
            locator.title?.let { put("title", it) }
            locator.locations.progression?.let { put("progression", it.toString()) }
            locator.locations.totalProgression?.let { put("totalProgression", it.toString()) }
            locator.locations.position?.let { put("position", it.toString()) }
        }

        return Locator(
            scheme = LocatorSchemes.EPUB_CFI,
            value = value,
            extras = extras
        )
    }

    fun toReadium(
        publication: Publication,
        locator: Locator?
    ): org.readium.r2.shared.publication.Locator? {
        if (locator == null) return null

        return when (locator.scheme) {
            LocatorSchemes.EPUB_CFI -> parseEpubLocator(publication, locator)
            LocatorSchemes.REFLOW_PAGE -> parseReflowLocator(publication, locator)
            else -> null
        }
    }

    private fun parseEpubLocator(
        publication: Publication,
        locator: Locator
    ): org.readium.r2.shared.publication.Locator? {
        val value = locator.value
        return when {
            value.startsWith("epubcfi(") -> {
                val base = resolveBaseLocator(publication, locator.extras["href"])
                    ?: firstReadingOrderLocator(publication)
                    ?: return null
                base.copyWithLocations(
                    fragments = listOf(value),
                    progression = locator.extras["progression"]?.toDoubleOrNull(),
                    position = locator.extras["position"]?.toIntOrNull(),
                    totalProgression = locator.extras["totalProgression"]?.toDoubleOrNull()
                )
            }

            value.startsWith("href:") -> {
                val href = value.removePrefix("href:")
                val path = href.substringBefore('#')
                val fragment = href.substringAfter('#', "")
                val base = resolveBaseLocator(publication, path) ?: return null
                base.copyWithLocations(
                    fragments = if (fragment.isBlank()) emptyList() else listOf(fragment),
                    progression = locator.extras["progression"]?.toDoubleOrNull(),
                    position = locator.extras["position"]?.toIntOrNull(),
                    totalProgression = locator.extras["totalProgression"]?.toDoubleOrNull()
                )
            }

            value.startsWith("spine:") -> {
                val spine = value.removePrefix("spine:").toIntOrNull() ?: return null
                readingOrderLocator(publication, spine)
            }

            else -> null
        }
    }

    private fun parseReflowLocator(
        publication: Publication,
        locator: Locator
    ): org.readium.r2.shared.publication.Locator? {
        val spine = locator.value.substringBefore(':').toIntOrNull() ?: return null
        return readingOrderLocator(publication, spine)
    }

    private fun resolveBaseLocator(
        publication: Publication,
        href: String?
    ): org.readium.r2.shared.publication.Locator? {
        if (href.isNullOrBlank()) return null
        val url = Url(href) ?: return null
        val link = publication.linkWithHref(url) ?: return null
        return publication.locatorFromLink(link)
    }

    private fun firstReadingOrderLocator(
        publication: Publication
    ): org.readium.r2.shared.publication.Locator? {
        val first = publication.readingOrder.firstOrNull() ?: return null
        return publication.locatorFromLink(first)
    }

    private fun readingOrderLocator(
        publication: Publication,
        index: Int
    ): org.readium.r2.shared.publication.Locator? {
        val link = publication.readingOrder.getOrNull(index) ?: return null
        return publication.locatorFromLink(link)
    }
}
