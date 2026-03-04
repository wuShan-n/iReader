package com.ireader.core.work.enrich.epub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class EpubZipEnricherTest {

    private lateinit var context: Context
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        tempDir = File(context.cacheDir, "epub-cover-test-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        runCatching { tempDir.deleteRecursively() }
    }

    @Test
    fun `auto extraction should discover cover from manifest cover-image property`() {
        val epub = File(tempDir, "auto.epub")
        val output = File(tempDir, "auto.png")
        createEpub(
            file = epub,
            opf = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <manifest>
                    <item id="cover" href="images/cover.png" media-type="image/png" properties="cover-image"/>
                  </manifest>
                  <spine/>
                </package>
            """.trimIndent(),
            images = mapOf("OPS/images/cover.png" to solidBitmapBytes(Color.RED))
        )

        val ok = EpubZipEnricher.tryExtractCoverToPng(
            file = epub,
            outFile = output,
            reqWidth = 120,
            reqHeight = 160
        )

        assertTrue(ok)
        assertTrue(output.exists())
        val bitmap = BitmapFactory.decodeFile(output.absolutePath)
        assertNotNull(bitmap)
        assertEquals(Color.RED, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
    }

    @Test
    fun `auto extraction should fallback to meta cover id`() {
        val epub = File(tempDir, "meta.epub")
        val output = File(tempDir, "meta.png")
        createEpub(
            file = epub,
            opf = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <meta name="cover" content="cover-id"/>
                  </metadata>
                  <manifest>
                    <item id="cover-id" href="images/cover.jpg" media-type="image/jpeg"/>
                  </manifest>
                  <spine/>
                </package>
            """.trimIndent(),
            images = mapOf("OPS/images/cover.jpg" to solidBitmapBytes(Color.BLUE))
        )

        val ok = EpubZipEnricher.tryExtractCoverToPng(
            file = epub,
            outFile = output,
            reqWidth = 120,
            reqHeight = 160
        )

        assertTrue(ok)
        val bitmap = BitmapFactory.decodeFile(output.absolutePath)
        assertNotNull(bitmap)
        assertEquals(Color.BLUE, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
    }

    @Test
    fun `path extraction should normalize relative segments`() {
        val epub = File(tempDir, "norm.epub")
        val output = File(tempDir, "norm.png")
        createEpub(
            file = epub,
            opf = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <manifest>
                    <item id="cover" href="images/folder/cover.png" media-type="image/png" properties="cover-image"/>
                  </manifest>
                  <spine/>
                </package>
            """.trimIndent(),
            images = mapOf("OPS/images/folder/cover.png" to solidBitmapBytes(Color.GREEN))
        )

        val ok = EpubZipEnricher.tryExtractCoverToPng(
            file = epub,
            coverPathInZip = "OPS/images/../images/folder/cover.png",
            outFile = output,
            reqWidth = 120,
            reqHeight = 160
        )

        assertTrue(ok)
        val bitmap = BitmapFactory.decodeFile(output.absolutePath)
        assertNotNull(bitmap)
        assertEquals(Color.GREEN, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
    }

    @Test
    fun `auto extraction should return false when no cover metadata exists`() {
        val epub = File(tempDir, "nocover.epub")
        val output = File(tempDir, "nocover.png")
        createEpub(
            file = epub,
            opf = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <manifest>
                    <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine/>
                </package>
            """.trimIndent(),
            images = emptyMap()
        )

        val ok = EpubZipEnricher.tryExtractCoverToPng(
            file = epub,
            outFile = output,
            reqWidth = 120,
            reqHeight = 160
        )

        assertFalse(ok)
        assertFalse(output.exists())
    }

    private fun createEpub(file: File, opf: String, images: Map<String, ByteArray>) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            writeEntry(
                zip = zip,
                path = "META-INF/container.xml",
                text = """
                    <?xml version="1.0"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                """.trimIndent()
            )
            writeEntry(zip = zip, path = "OPS/package.opf", text = opf)
            images.forEach { (path, data) ->
                writeEntry(zip = zip, path = path, bytes = data)
            }
        }
    }

    private fun writeEntry(zip: ZipOutputStream, path: String, text: String) {
        writeEntry(zip = zip, path = path, bytes = text.toByteArray(Charsets.UTF_8))
    }

    private fun writeEntry(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun solidBitmapBytes(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }
}
