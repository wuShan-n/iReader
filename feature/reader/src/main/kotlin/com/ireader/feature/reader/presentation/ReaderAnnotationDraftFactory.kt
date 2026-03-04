package com.ireader.feature.reader.presentation

import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.NormalizedRect
import com.ireader.reader.model.annotation.AnnotationAnchor
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationType

internal object ReaderAnnotationDraftFactory {

    fun create(
        selection: SelectionProvider.Selection?,
        fallbackLocator: Locator?
    ): ReaderResult<AnnotationDraft> {
        val anchor = selection?.toAnchor()
            ?: fallbackLocator?.toFallbackAnchor()
            ?: return ReaderResult.Err(
                ReaderError.Internal("Cannot create annotation without a valid locator")
            )

        val content = selection?.selectedText
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return ReaderResult.Ok(
            AnnotationDraft(
                type = AnnotationType.NOTE,
                anchor = anchor,
                content = content
            )
        )
    }

    private fun SelectionProvider.Selection.toAnchor(): AnnotationAnchor {
        val startLocator = start
        val endLocator = end
        if (startLocator != null && endLocator != null) {
            return AnnotationAnchor.ReflowRange(
                LocatorRange(
                    start = startLocator,
                    end = endLocator,
                    extras = extras
                )
            )
        }

        return if (locator.scheme == LocatorSchemes.PDF_PAGE) {
            AnnotationAnchor.FixedRects(
                page = locator,
                rects = selectionRects()
            )
        } else {
            AnnotationAnchor.ReflowRange(LocatorRange(start = locator, end = locator))
        }
    }

    private fun Locator.toFallbackAnchor(): AnnotationAnchor {
        return if (scheme == LocatorSchemes.PDF_PAGE) {
            AnnotationAnchor.FixedRects(page = this, rects = emptyList())
        } else {
            AnnotationAnchor.ReflowRange(LocatorRange(start = this, end = this))
        }
    }

    private fun SelectionProvider.Selection.selectionRects(): List<NormalizedRect> {
        if (rects.isNotEmpty()) return rects
        return listOfNotNull(bounds)
    }
}
