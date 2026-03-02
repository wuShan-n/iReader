package com.ireader.engines.epub.internal.parser

import com.ireader.engines.epub.internal.parser.model.EpubManifestItem
import com.ireader.engines.epub.internal.parser.model.EpubPackage
import com.ireader.engines.epub.internal.parser.model.EpubSpineItem
import com.ireader.reader.model.DocumentMetadata
import java.io.File

internal object OpfParser {

    fun parse(opfFile: File, opfPath: String): EpubPackage {
        val document = XmlDom.parse(opfFile)
        val root = document.documentElement

        val manifest = linkedMapOf<String, EpubManifestItem>()
        val spineIdRefs = mutableListOf<String>()

        var title: String? = null
        var author: String? = null
        var language: String? = null
        var identifier: String? = null
        var spineTocId: String? = null

        // ---- cover detection (EPUB2 + EPUB3 common patterns) ----
        var coverIdFromMeta: String? = null               // <meta name="cover" content="cover-image-id" />
        var coverHrefFromProperties: String? = null       // <item properties="cover-image" ... />

        XmlDom.descendants(root).forEach { element ->
            when (XmlDom.localName(element).lowercase()) {
                "title" -> if (title == null) {
                    title = XmlDom.textContentTrimmed(element)
                }

                "creator", "author" -> if (author == null) {
                    author = XmlDom.textContentTrimmed(element)
                }

                "language" -> if (language == null) {
                    language = XmlDom.textContentTrimmed(element)
                }

                "identifier" -> if (identifier == null) {
                    identifier = XmlDom.textContentTrimmed(element)
                }

                "meta" -> {
                    // EPUB2 cover convention: <meta name="cover" content="cover-image-id" />
                    val metaName = XmlDom.attr(element, "name")?.trim()
                    val metaContent = XmlDom.attr(element, "content")?.trim()
                    if (metaName.equals("cover", ignoreCase = true) && !metaContent.isNullOrBlank()) {
                        coverIdFromMeta = metaContent
                    }
                }

                "spine" -> {
                    spineTocId = XmlDom.attr(element, "toc")?.trim()
                }

                "item" -> {
                    val id = XmlDom.attr(element, "id")?.trim().orEmpty()
                    val href = XmlDom.attr(element, "href")?.trim().orEmpty()
                    val mediaType = XmlDom.attr(element, "media-type")?.trim()
                    val properties = XmlDom.attr(element, "properties")?.trim()

                    if (id.isNotBlank() && href.isNotBlank()) {
                        manifest[id] = EpubManifestItem(
                            id = id,
                            href = href,
                            mediaType = mediaType,
                            properties = properties
                        )
                    }

                    // EPUB3 cover convention: properties contains "cover-image"
                    if (coverHrefFromProperties == null && hasCoverImageProperty(properties)) {
                        coverHrefFromProperties = href
                    }
                }

                "itemref" -> {
                    val idRef = XmlDom.attr(element, "idref")?.trim().orEmpty()
                    if (idRef.isNotBlank()) {
                        spineIdRefs += idRef
                    }
                }
            }
        }

        val spine = spineIdRefs.mapNotNull { idRef ->
            val manifestItem = manifest[idRef] ?: return@mapNotNull null
            EpubSpineItem(
                idRef = idRef,
                href = PathResolver.resolveFrom(opfPath, manifestItem.href),
                mediaType = manifestItem.mediaType
            )
        }

        val navPath = manifest.values
            .firstOrNull { item -> hasNavProperty(item.properties) }
            ?.href
            ?.let { href -> PathResolver.resolveFrom(opfPath, href) }

        val ncxPath = manifest.values
            .firstOrNull { item -> item.mediaType == NCX_MEDIA_TYPE }
            ?.href
            ?.let { href -> PathResolver.resolveFrom(opfPath, href) }
            ?: spineTocId
                ?.let { id -> manifest[id]?.href }
                ?.let { href -> PathResolver.resolveFrom(opfPath, href) }

        // ---- resolve cover path into zip-internal normalized path ----
        val coverHref = when {
            !coverHrefFromProperties.isNullOrBlank() -> coverHrefFromProperties
            !coverIdFromMeta.isNullOrBlank() -> manifest[coverIdFromMeta]?.href
            else -> null
        }

        val coverPath = coverHref
            ?.let(::stripQueryAndFragment)
            ?.takeIf { it.isNotBlank() }
            ?.let { href -> PathResolver.resolveFrom(opfPath, href) }
            ?.let { PathResolver.normalizePath(it) }

        val metadata = DocumentMetadata(
            title = title,
            author = author,
            language = language,
            identifier = identifier,
            extra = buildMap {
                if (!coverPath.isNullOrBlank()) {
                    put("coverPath", coverPath)
                }
            }
        )

        val mediaTypeByPath = buildMap {
            manifest.values.forEach { item ->
                val mediaType = item.mediaType ?: return@forEach
                val path = PathResolver.resolveFrom(opfPath, item.href)
                put(path, mediaType)
            }
        }

        return EpubPackage(
            metadata = metadata,
            manifest = manifest.toMap(),
            spine = spine,
            opfPath = PathResolver.normalizePath(opfPath),
            opfDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "").trim('/'),
            navPath = navPath,
            ncxPath = ncxPath,
            mediaTypeByPath = mediaTypeByPath
        )
    }

    private const val NCX_MEDIA_TYPE = "application/x-dtbncx+xml"

    private fun hasNavProperty(properties: String?): Boolean {
        if (properties.isNullOrBlank()) return false
        return properties
            .split(' ')
            .any { property -> property.equals("nav", ignoreCase = true) }
    }

    private fun hasCoverImageProperty(properties: String?): Boolean {
        if (properties.isNullOrBlank()) return false
        return properties
            .split(' ')
            .any { p -> p.equals("cover-image", ignoreCase = true) }
    }

    private fun stripQueryAndFragment(href: String): String {
        return href.substringBefore('#').substringBefore('?')
    }
}