package com.ireader.engines.epub.internal.parser

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContainerParserTest {

    @Test
    fun `parseOpfPath returns path from container xml`() {
        val tempDir = Files.createTempDirectory("container-test").toFile()
        val container = File(tempDir, "container.xml")
        container.writeText(
            """
            <?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """.trimIndent()
        )

        assertEquals("OPS/content.opf", ContainerParser.parseOpfPath(container))
    }

    @Test
    fun `parseOpfPath returns null when rootfile missing`() {
        val tempDir = Files.createTempDirectory("container-test-empty").toFile()
        val container = File(tempDir, "container.xml")
        container.writeText("<container><rootfiles/></container>")

        assertNull(ContainerParser.parseOpfPath(container))
    }
}
