package com.ireader.engines.epub.internal.pagination

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

internal class EpubPageMetricsStore(
    private val file: File
) {
    private val pagesByKey = ConcurrentHashMap<String, Int>()
    @Volatile
    private var dirty: Boolean = false

    init {
        load()
    }

    fun getPages(spine: Int, sig: Int): Int? = pagesByKey["$spine:$sig"]

    fun putPages(spine: Int, sig: Int, pages: Int) {
        val safePages = pages.coerceIn(1, 5000)
        val key = "$spine:$sig"
        val previous = pagesByKey.put(key, safePages)
        if (previous != safePages) {
            dirty = true
        }
    }

    fun flush() {
        if (!dirty) return
        synchronized(this) {
            if (!dirty) return
            val properties = Properties()
            for ((key, value) in pagesByKey.entries) {
                properties.setProperty(key, value.toString())
            }
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { output ->
                properties.store(output, "epub page metrics")
            }
            dirty = false
        }
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val properties = Properties()
            FileInputStream(file).use { input ->
                properties.load(input)
            }
            for (name in properties.stringPropertyNames()) {
                val value = properties.getProperty(name)?.toIntOrNull() ?: continue
                pagesByKey[name] = value.coerceIn(1, 5000)
            }
        }
    }
}
