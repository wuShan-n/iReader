package com.ireader.engines.epub.internal.content

import android.net.Uri

internal object EpubUri {
    private const val ROOT_SEGMENT = "epub"
    private const val FILE_SEGMENT = "file"

    fun buildFileUri(authority: String, docId: String, relativePath: String): Uri {
        val normalized = relativePath.trimStart('/')
        val builder = Uri.Builder()
            .scheme("content")
            .authority(authority)
            .appendPath(ROOT_SEGMENT)
            .appendPath(docId)
            .appendPath(FILE_SEGMENT)

        normalized.split('/').filter { it.isNotBlank() }.forEach { segment ->
            builder.appendPath(segment)
        }
        return builder.build()
    }

    fun parse(uri: Uri): Parsed? {
        val segments = uri.pathSegments ?: return null
        if (segments.size < 4) return null
        if (segments[0] != ROOT_SEGMENT) return null
        if (segments[2] != FILE_SEGMENT) return null

        val docId = segments[1]
        if (docId.isBlank()) return null

        val relativePath = segments.subList(3, segments.size).joinToString("/")
        if (relativePath.isBlank()) return null

        return Parsed(docId = docId, relativePath = relativePath)
    }

    data class Parsed(
        val docId: String,
        val relativePath: String
    )
}
