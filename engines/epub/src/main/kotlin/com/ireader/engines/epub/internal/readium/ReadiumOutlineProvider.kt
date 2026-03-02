package com.ireader.engines.epub.internal.readium

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.OutlineNode
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication

internal class ReadiumOutlineProvider(
    private val publication: Publication
) : OutlineProvider {

    override suspend fun getOutline(): ReaderResult<List<OutlineNode>> {
        return ReaderResult.Ok(
            publication.tableOfContents.mapNotNull(::mapLink)
        )
    }

    private fun mapLink(link: Link): OutlineNode? {
        val locator = publication.locatorFromLink(link)
            ?.let(ReadiumLocatorMapper::toModel)
            ?: Locator(
                scheme = LocatorSchemes.EPUB_CFI,
                value = "href:${link.url()}"
            )

        val title = link.title?.ifBlank { null } ?: locator.extras["title"] ?: return null
        return OutlineNode(
            title = title,
            locator = locator,
            children = link.children.mapNotNull(::mapLink)
        )
    }
}
