package com.ireader.engines.epub.internal.parser

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OpfParserTest {

    @Test
    fun `parse builds spine and metadata`() {
        val tempDir = Files.createTempDirectory("opf-test").toFile()
        val opfFile = File(tempDir, "content.opf")
        opfFile.writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Test Book</dc:title>
                <dc:creator>Author A</dc:creator>
                <dc:language>zh</dc:language>
                <dc:identifier id="bookid">book-1</dc:identifier>
              </metadata>
              <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="toc" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                <item id="ch1" href="Text/ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="ch2" href="Text/ch2.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine toc="toc">
                <itemref idref="ch1"/>
                <itemref idref="ch2"/>
              </spine>
            </package>
            """.trimIndent()
        )

        val parsed = OpfParser.parse(opfFile = opfFile, opfPath = "OPS/content.opf")

        assertEquals("Test Book", parsed.metadata.title)
        assertEquals("Author A", parsed.metadata.author)
        assertEquals("zh", parsed.metadata.language)
        assertEquals("book-1", parsed.metadata.identifier)

        assertEquals(2, parsed.spine.size)
        assertEquals("OPS/Text/ch1.xhtml", parsed.spine[0].href)
        assertEquals("OPS/Text/ch2.xhtml", parsed.spine[1].href)

        assertEquals("OPS/nav.xhtml", parsed.navPath)
        assertEquals("OPS/toc.ncx", parsed.ncxPath)
        assertNotNull(parsed.mediaTypeByPath["OPS/Text/ch1.xhtml"])
    }
}
