package com.ireader.core.work.enrich.epub

import android.graphics.Bitmap
import android.util.Xml
import com.ireader.core.work.enrich.BitmapDecode
import com.ireader.core.work.enrich.BitmapIO
import com.ireader.reader.model.DocumentMetadata
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.ArrayDeque
import java.util.zip.ZipFile
import org.xmlpull.v1.XmlPullParser

object EpubZipEnricher {

    data class Parsed(
        val metadata: DocumentMetadata,
        val coverPathInZip: String?
    )

    fun parse(file: File): Parsed? {
        return runCatching {
            ZipFile(file).use { zip ->
                val opfPath = findOpfPath(zip) ?: return null
                val opfEntry = zip.getEntry(opfPath) ?: return null
                zip.getInputStream(opfEntry).use { input ->
                    parseOpf(opfPath = opfPath, opfStream = input)
                }
            }
        }.getOrNull()
    }

    fun tryExtractCoverToPng(
        file: File,
        coverPathInZip: String,
        outFile: File,
        reqWidth: Int,
        reqHeight: Int
    ): Boolean {
        return runCatching {
            ZipFile(file).use { zip ->
                val normalizedPath = normalizeZipPath(coverPathInZip)
                val coverEntry = zip.getEntry(normalizedPath) ?: return false
                val bytes = zip.getInputStream(coverEntry).use { stream ->
                    stream.readAllBytesCapped(limitBytes = 12 * 1024 * 1024)
                } ?: return false

                val decoded = BitmapDecode.decodeSampled(bytes, reqWidth, reqHeight) ?: return false
                val outputBitmap = if (decoded.width == reqWidth && decoded.height == reqHeight) {
                    decoded
                } else {
                    Bitmap.createScaledBitmap(decoded, reqWidth, reqHeight, true)
                }
                BitmapIO.savePng(outFile, outputBitmap)
                true
            }
        }.getOrDefault(false)
    }

    private fun findOpfPath(zip: ZipFile): String? {
        val container = zip.getEntry(CONTAINER_XML_PATH) ?: return null
        zip.getInputStream(container).use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, null)

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name.equals("rootfile", ignoreCase = true)) {
                    val fullPath = attr(parser, "full-path")
                    if (!fullPath.isNullOrBlank()) {
                        return normalizeZipPath(fullPath)
                    }
                }
                event = parser.next()
            }
        }
        return null
    }

    private fun parseOpf(opfPath: String, opfStream: InputStream): Parsed {
        val baseDir = opfPath.substringBeforeLast('/', "")
        val parser = Xml.newPullParser()
        parser.setInput(opfStream, null)

        var title: String? = null
        var creator: String? = null
        var language: String? = null
        var identifier: String? = null

        var coverIdFromMeta: String? = null
        var coverHrefFromProperties: String? = null
        val manifestIdToHref = HashMap<String, String>(64)

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val name = parser.name
                when {
                    name.endsWith("title", ignoreCase = true) && title == null && isDcTag(parser) -> {
                        title = parser.nextTextOrNull()?.trim().takeUnless { it.isNullOrBlank() }
                    }

                    name.endsWith("creator", ignoreCase = true) && creator == null && isDcTag(parser) -> {
                        creator = parser.nextTextOrNull()?.trim().takeUnless { it.isNullOrBlank() }
                    }

                    name.endsWith("language", ignoreCase = true) && language == null && isDcTag(parser) -> {
                        language = parser.nextTextOrNull()?.trim().takeUnless { it.isNullOrBlank() }
                    }

                    name.endsWith("identifier", ignoreCase = true) && identifier == null && isDcTag(parser) -> {
                        identifier = parser.nextTextOrNull()?.trim().takeUnless { it.isNullOrBlank() }
                    }

                    name.equals("meta", ignoreCase = true) -> {
                        val metaName = attr(parser, "name")
                        val metaContent = attr(parser, "content")
                        if (metaName.equals("cover", ignoreCase = true) && !metaContent.isNullOrBlank()) {
                            coverIdFromMeta = metaContent.trim()
                        }
                    }

                    name.equals("item", ignoreCase = true) -> {
                        val id = attr(parser, "id").orEmpty()
                        val href = attr(parser, "href")?.trim()
                        val properties = attr(parser, "properties").orEmpty()

                        if (id.isNotBlank() && !href.isNullOrBlank()) {
                            manifestIdToHref[id] = href
                        }

                        if (coverHrefFromProperties == null &&
                            properties.split(' ').any { it.equals("cover-image", ignoreCase = true) }
                        ) {
                            coverHrefFromProperties = href
                        }
                    }
                }
            }
            event = parser.next()
        }

        val coverHref = when {
            !coverHrefFromProperties.isNullOrBlank() -> coverHrefFromProperties
            !coverIdFromMeta.isNullOrBlank() -> manifestIdToHref[coverIdFromMeta]
            else -> null
        }

        val coverPath = coverHref
            ?.let { stripQueryAndFragment(it) }
            ?.let { resolveZipPath(baseDir, it) }
            ?.let { normalizeZipPath(it) }
            ?.takeIf { it.isNotBlank() }

        return Parsed(
            metadata = DocumentMetadata(
                title = title,
                author = creator,
                language = language,
                identifier = identifier,
                extra = buildMap {
                    if (!coverPath.isNullOrBlank()) {
                        put("coverPath", coverPath)
                    }
                }
            ),
            coverPathInZip = coverPath
        )
    }

    private fun attr(parser: XmlPullParser, name: String): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i).equals(name, ignoreCase = true)) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    private fun isDcTag(parser: XmlPullParser): Boolean {
        val prefix = parser.prefix.orEmpty()
        val namespace = parser.namespace.orEmpty()
        return prefix.equals("dc", ignoreCase = true) || namespace.contains("purl.org/dc", ignoreCase = true)
    }

    private fun XmlPullParser.nextTextOrNull(): String? {
        return runCatching { nextText() }.getOrNull()
    }

    private fun stripQueryAndFragment(path: String): String {
        return path.substringBefore('#').substringBefore('?')
    }

    private fun resolveZipPath(baseDir: String, href: String): String {
        if (baseDir.isBlank()) {
            return href
        }
        return "$baseDir/$href"
    }

    private fun normalizeZipPath(path: String): String {
        val parts = path
            .replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() }

        val stack = ArrayDeque<String>(parts.size)
        for (part in parts) {
            when (part) {
                "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(part)
            }
        }
        return stack.joinToString("/")
    }

    private fun InputStream.readAllBytesCapped(limitBytes: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read <= 0) {
                break
            }
            total += read
            if (total > limitBytes) {
                return null
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private const val CONTAINER_XML_PATH = "META-INF/container.xml"
}
