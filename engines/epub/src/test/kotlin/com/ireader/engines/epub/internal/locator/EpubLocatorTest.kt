package com.ireader.engines.epub.internal.locator

import com.ireader.engines.epub.internal.open.EpubContainer
import com.ireader.engines.epub.internal.parser.model.EpubManifestItem
import com.ireader.engines.epub.internal.parser.model.EpubPackage
import com.ireader.engines.epub.internal.parser.model.EpubSpineItem
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpubLocatorTest {

    private val container: EpubContainer by lazy {
        val root = Files.createTempDirectory("epub-locator").toFile()
        val base = Files.createTempDirectory("epub-locator-base").toFile()
        val pkg = EpubPackage(
            metadata = DocumentMetadata(title = "Book"),
            manifest = mapOf(
                "ch1" to EpubManifestItem("ch1", "Text/ch1.xhtml", "application/xhtml+xml", null),
                "ch2" to EpubManifestItem("ch2", "Text/ch2.xhtml", "application/xhtml+xml", null)
            ),
            spine = listOf(
                EpubSpineItem("ch1", "OPS/Text/ch1.xhtml", "application/xhtml+xml"),
                EpubSpineItem("ch2", "OPS/Text/ch2.xhtml", "application/xhtml+xml")
            ),
            opfPath = "OPS/content.opf",
            opfDir = "OPS",
            navPath = null,
            ncxPath = null,
            mediaTypeByPath = emptyMap()
        )
        EpubContainer(
            id = DocumentId("epub:test"),
            rootDir = root,
            baseDir = base,
            authority = "com.ireader.epub",
            opf = pkg,
            outline = emptyList()
        )
    }

    @Test
    fun `spine index from spine locator`() {
        val index = EpubLocator.spineIndexOf(
            container,
            Locator(LocatorSchemes.EPUB_CFI, "spine:1")
        )
        assertEquals(1, index)
    }

    @Test
    fun `spine index from href locator`() {
        val index = EpubLocator.spineIndexOf(
            container,
            Locator(LocatorSchemes.EPUB_CFI, "href:OPS/Text/ch2.xhtml#frag")
        )
        assertEquals(1, index)
    }

    @Test
    fun `invalid locator returns null`() {
        val index = EpubLocator.spineIndexOf(
            container,
            Locator(LocatorSchemes.EPUB_CFI, "epubcfi(/6/2)")
        )
        assertNull(index)
    }

    @Test
    fun `reflow locator supports optional signature part`() {
        val parsed = EpubLocator.parseReflowPage("2:9:12345")
        assertEquals(2, parsed?.first)
        assertEquals(9, parsed?.second)
    }
}
