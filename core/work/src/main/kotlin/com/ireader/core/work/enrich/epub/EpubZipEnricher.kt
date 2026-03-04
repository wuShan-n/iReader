package com.ireader.core.work.enrich.epub

import android.graphics.Bitmap
import android.util.Xml
import com.ireader.core.work.enrich.BitmapDecode
import com.ireader.core.work.enrich.BitmapIO
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.ArrayDeque
import java.util.zip.ZipFile
import org.xmlpull.v1.XmlPullParser

object EpubZipEnricher {

    fun tryExtractCoverToPng(
        file: File,
        outFile: File,
        reqWidth: Int,
        reqHeight: Int
    ): Boolean {
        return runCatching {
            ZipFile(file).use { zip ->
                val coverPathInZip = discoverCoverPathInZip(zip) ?: return@use false
                extractCoverToPng(
                    zip = zip,
                    coverPathInZip = coverPathInZip,
                    outFile = outFile,
                    reqWidth = reqWidth,
                    reqHeight = reqHeight
                )
            }
        }.getOrDefault(false)
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
                extractCoverToPng(
                    zip = zip,
                    coverPathInZip = coverPathInZip,
                    outFile = outFile,
                    reqWidth = reqWidth,
                    reqHeight = reqHeight
                )
            }
        }.getOrDefault(false)
    }

    private fun extractCoverToPng(
        zip: ZipFile,
        coverPathInZip: String,
        outFile: File,
        reqWidth: Int,
        reqHeight: Int
    ): Boolean {
        val coverEntry = findEntry(zip = zip, rawPath = coverPathInZip) ?: return false
        val bytes = zip.getInputStream(coverEntry).use { stream ->
            stream.readAllBytesCapped(limitBytes = MAX_IMAGE_BYTES)
        } ?: return false

        val decoded = BitmapDecode.decodeSampled(bytes, reqWidth, reqHeight) ?: return false
        val outputBitmap = if (decoded.width == reqWidth && decoded.height == reqHeight) {
            decoded
        } else {
            Bitmap.createScaledBitmap(decoded, reqWidth, reqHeight, true)
        }
        BitmapIO.savePng(outFile, outputBitmap)
        return true
    }

    private fun discoverCoverPathInZip(zip: ZipFile): String? {
        val rootFilePath = parseRootFilePath(zip) ?: return null
        val rootEntry = findEntry(zip = zip, rawPath = rootFilePath) ?: return null
        val opfBytes = zip.getInputStream(rootEntry).use { input ->
            input.readAllBytesCapped(limitBytes = MAX_XML_BYTES)
        } ?: return null

        val parsed = parseOpf(opfBytes) ?: return null
        val coverCandidates = buildList {
            parsed.manifestItems
                .filter { "cover-image" in it.properties }
                .forEach { add(resolveRelativePath(rootFilePath, it.href)) }
            parsed.coverMetaIds
                .mapNotNull { parsed.manifestById[it] }
                .forEach { add(resolveRelativePath(rootFilePath, it.href)) }
            parsed.guideCoverHrefs
                .forEach { add(resolveRelativePath(rootFilePath, it)) }
            parsed.manifestItems
                .filter { item ->
                    val isImage = item.mediaType?.startsWith("image/", ignoreCase = true) == true
                    val idLikeCover = item.id?.contains("cover", ignoreCase = true) == true
                    val hrefLikeCover = item.href.contains("cover", ignoreCase = true)
                    isImage && (idLikeCover || hrefLikeCover)
                }
                .forEach { add(resolveRelativePath(rootFilePath, it.href)) }
        }.distinct()

        return coverCandidates.firstOrNull { candidate ->
            findEntry(zip = zip, rawPath = candidate) != null
        }
    }

    private fun parseRootFilePath(zip: ZipFile): String? {
        val containerEntry = findEntry(zip = zip, rawPath = CONTAINER_PATH) ?: return null
        val containerBytes = zip.getInputStream(containerEntry).use { input ->
            input.readAllBytesCapped(limitBytes = MAX_XML_BYTES)
        } ?: return null
        return parseRootFilePath(containerBytes)
    }

    private fun parseRootFilePath(containerXml: ByteArray): String? {
        val parser = Xml.newPullParser()
        parser.setInput(containerXml.inputStream(), null)
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name.equals("rootfile", ignoreCase = true)) {
                val fullPath = parser.attributeValue("full-path")
                if (!fullPath.isNullOrBlank()) {
                    return normalizeZipPath(fullPath)
                }
            }
            eventType = parser.next()
        }
        return null
    }

    private fun parseOpf(opfXml: ByteArray): ParsedOpf? {
        return runCatching {
            val parser = Xml.newPullParser()
            parser.setInput(opfXml.inputStream(), null)

            val manifestById = LinkedHashMap<String, ManifestItem>()
            val manifestItems = mutableListOf<ManifestItem>()
            val coverMetaIds = mutableListOf<String>()
            val guideCoverHrefs = mutableListOf<String>()

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name.lowercase()) {
                        "item" -> {
                            val href = parser.attributeValue("href")?.trim().orEmpty()
                            if (href.isNotBlank()) {
                                val id = parser.attributeValue("id")?.trim().orEmpty().ifBlank { null }
                                val mediaType = parser.attributeValue("media-type")?.trim().orEmpty().ifBlank { null }
                                val properties = parser.attributeValue("properties")
                                    ?.split(Regex("\\s+"))
                                    ?.map { it.trim() }
                                    ?.filter { it.isNotBlank() }
                                    ?.toSet()
                                    ?: emptySet()

                                val item = ManifestItem(
                                    id = id,
                                    href = href,
                                    mediaType = mediaType,
                                    properties = properties
                                )
                                manifestItems += item
                                if (id != null) {
                                    manifestById[id] = item
                                }
                            }
                        }

                        "meta" -> {
                            val name = parser.attributeValue("name")
                            if (name.equals("cover", ignoreCase = true)) {
                                parser.attributeValue("content")
                                    ?.trim()
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let(coverMetaIds::add)
                            }
                        }

                        "reference" -> {
                            val type = parser.attributeValue("type")
                            if (type?.contains("cover", ignoreCase = true) == true) {
                                parser.attributeValue("href")
                                    ?.trim()
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let(guideCoverHrefs::add)
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            ParsedOpf(
                manifestById = manifestById,
                manifestItems = manifestItems,
                coverMetaIds = coverMetaIds,
                guideCoverHrefs = guideCoverHrefs
            )
        }.getOrNull()
    }

    private fun findEntry(zip: ZipFile, rawPath: String): java.util.zip.ZipEntry? {
        val normalized = normalizeZipPath(rawPath)
        zip.getEntry(normalized)?.let { return it }

        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (normalizeZipPath(entry.name).equals(normalized, ignoreCase = true)) {
                return entry
            }
        }
        return null
    }

    private fun resolveRelativePath(basePath: String, href: String): String {
        val cleanHref = href
            .substringBefore('#')
            .substringBefore('?')
            .trim()
        if (cleanHref.isBlank()) {
            return ""
        }
        if (cleanHref.startsWith("/")) {
            return normalizeZipPath(cleanHref)
        }

        val baseDir = normalizeZipPath(basePath).substringBeforeLast('/', "")
        return if (baseDir.isBlank()) {
            normalizeZipPath(cleanHref)
        } else {
            normalizeZipPath("$baseDir/$cleanHref")
        }
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

    private fun XmlPullParser.attributeValue(name: String): String? {
        for (index in 0 until attributeCount) {
            if (getAttributeName(index).equals(name, ignoreCase = true)) {
                return getAttributeValue(index)
            }
        }
        return null
    }

    private data class ManifestItem(
        val id: String?,
        val href: String,
        val mediaType: String?,
        val properties: Set<String>
    )

    private data class ParsedOpf(
        val manifestById: Map<String, ManifestItem>,
        val manifestItems: List<ManifestItem>,
        val coverMetaIds: List<String>,
        val guideCoverHrefs: List<String>
    )

    private const val CONTAINER_PATH = "META-INF/container.xml"
    private const val MAX_IMAGE_BYTES = 12 * 1024 * 1024
    private const val MAX_XML_BYTES = 2 * 1024 * 1024
}
