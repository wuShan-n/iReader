package com.ireader.feature.reader.presentation

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.NormalizedRect
import com.ireader.reader.model.annotation.AnnotationAnchor
import com.ireader.reader.model.annotation.AnnotationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderAnnotationDraftFactoryTest {

    @Test
    fun `should build reflow draft from selection range`() {
        val start = Locator(scheme = LocatorSchemes.EPUB_CFI, value = "epubcfi(/6/2[start])")
        val end = Locator(scheme = LocatorSchemes.EPUB_CFI, value = "epubcfi(/6/2[end])")
        val selection = SelectionProvider.Selection(
            locator = start,
            start = start,
            end = end,
            selectedText = "  highlighted text  "
        )

        val result = ReaderAnnotationDraftFactory.create(selection = selection, fallbackLocator = null)

        val draft = (result as ReaderResult.Ok).value
        assertEquals(AnnotationType.NOTE, draft.type)
        assertEquals("highlighted text", draft.content)
        val anchor = draft.anchor as AnnotationAnchor.ReflowRange
        assertEquals(start, anchor.range.start)
        assertEquals(end, anchor.range.end)
    }

    @Test
    fun `should fallback to fixed anchor for pdf locator`() {
        val locator = Locator(scheme = LocatorSchemes.PDF_PAGE, value = "12")

        val result = ReaderAnnotationDraftFactory.create(selection = null, fallbackLocator = locator)

        val draft = (result as ReaderResult.Ok).value
        val anchor = draft.anchor as AnnotationAnchor.FixedRects
        assertEquals(locator, anchor.page)
        assertTrue(anchor.rects.isEmpty())
        assertNull(draft.content)
    }

    @Test
    fun `should fallback to reflow anchor for non-pdf locator`() {
        val locator = Locator(scheme = LocatorSchemes.TXT_OFFSET, value = "1024")

        val result = ReaderAnnotationDraftFactory.create(selection = null, fallbackLocator = locator)

        val draft = (result as ReaderResult.Ok).value
        val anchor = draft.anchor as AnnotationAnchor.ReflowRange
        assertEquals(locator, anchor.range.start)
        assertEquals(locator, anchor.range.end)
    }

    @Test
    fun `should return error when neither selection nor locator exists`() {
        val result = ReaderAnnotationDraftFactory.create(selection = null, fallbackLocator = null)
        assertTrue(result is ReaderResult.Err)
    }

    @Test
    fun `should map pdf selection bounds into fixed rect anchor`() {
        val rect = NormalizedRect(left = 0.1f, top = 0.2f, right = 0.3f, bottom = 0.4f)
        val page = Locator(scheme = LocatorSchemes.PDF_PAGE, value = "5")
        val selection = SelectionProvider.Selection(
            locator = page,
            bounds = rect,
            selectedText = ""
        )

        val result = ReaderAnnotationDraftFactory.create(selection = selection, fallbackLocator = null)

        val draft = (result as ReaderResult.Ok).value
        val anchor = draft.anchor as AnnotationAnchor.FixedRects
        assertEquals(page, anchor.page)
        assertEquals(listOf(rect), anchor.rects)
        assertNull(draft.content)
    }
}
