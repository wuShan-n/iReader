package com.ireader.engines.epub.internal.provider

import com.ireader.engines.epub.internal.locator.toAppLocator
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.model.OutlineNode
import kotlinx.coroutines.CancellationException
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication

internal class EpubOutlineProvider(
    private val publication: Publication
) : OutlineProvider {

    override suspend fun getOutline(): ReaderResult<List<OutlineNode>> {
        return try {
            ReaderResult.Ok(publication.tableOfContents.mapNotNull { it.toOutlineNodeOrNull() })
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            ReaderResult.Err(ReaderError.Internal(cause = t))
        }
    }

    private fun Link.toOutlineNodeOrNull(): OutlineNode? {
        val locator = publication.locatorFromLink(this) ?: return null
        return OutlineNode(
            title = title ?: "Untitled",
            locator = locator.toAppLocator(),
            children = children.mapNotNull { it.toOutlineNodeOrNull() }
        )
    }
}
