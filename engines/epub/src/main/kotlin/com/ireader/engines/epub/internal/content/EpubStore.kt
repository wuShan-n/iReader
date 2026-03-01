package com.ireader.engines.epub.internal.content

import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal object EpubStore {
    private val extractedRoots = ConcurrentHashMap<String, File>()

    fun register(docId: String, extractedRoot: File) {
        extractedRoots[docId] = extractedRoot
    }

    fun unregister(docId: String) {
        extractedRoots.remove(docId)
    }

    fun extractedRootOf(docId: String): File? = extractedRoots[docId]
}
