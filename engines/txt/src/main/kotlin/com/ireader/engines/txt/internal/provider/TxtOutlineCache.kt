package com.ireader.engines.txt.internal.provider

import com.ireader.engines.txt.TxtEngineConfig
import com.ireader.reader.model.OutlineNode
import java.io.File

internal class TxtOutlineCache(
    private val config: TxtEngineConfig,
    private val docNamespace: String,
    private val charsetName: String
) {
    private companion object {
        private const val VERSION_HEADER_PREFIX = "#txt-outline-v"
        private const val CURRENT_VERSION = 2
    }

    // Persisted format is v2-only: level<TAB>offset<TAB>title
    val asTree: Boolean
        get() = config.outlineAsTree

    @Volatile
    private var memory: List<OutlineNode>? = null

    fun getMemory(): List<OutlineNode>? = memory

    fun setMemory(nodes: List<OutlineNode>) {
        memory = nodes
    }

    fun loadFromDisk(): List<OutlineNode>? {
        if (!config.persistOutline) return null
        val file = file() ?: return null
        if (!file.exists()) return null
        val lines = runCatching { file.readLines(Charsets.UTF_8) }.getOrNull() ?: return null
        val startIndex = when {
            lines.isEmpty() -> 0
            lines.first().startsWith(VERSION_HEADER_PREFIX) -> {
                val version = lines.first().removePrefix(VERSION_HEADER_PREFIX).toIntOrNull()
                if (version != CURRENT_VERSION) return null
                1
            }
            else -> 0
        }

        return runCatching {
            val builder = TxtOutlineTreeBuilder(asTree = asTree)
            for (line in lines.drop(startIndex)) {
                val parts = line.split('\t')
                if (parts.size < 3) continue
                val level = parts[0].toIntOrNull() ?: continue
                val offset = parts[1].toIntOrNull() ?: continue
                val title = parts.subList(2, parts.size).joinToString("\t")
                builder.add(level, title, offset)
            }
            builder.build()
        }.getOrNull()
    }

    fun saveToDisk(nodes: List<OutlineNode>) {
        if (!config.persistOutline) return
        val file = file() ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(
                buildString {
                    append(VERSION_HEADER_PREFIX)
                    append(CURRENT_VERSION)
                    append('\n')
                    appendNodes(nodes, level = 1)
                },
                Charsets.UTF_8
            )
            if (file.exists()) file.delete()
            val renamed = tmp.renameTo(file)
            if (!renamed) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }

    private fun file(): File? {
        val base = config.cacheDir ?: return null
        val folder = File(base, "reader-txt-v2/outline")
        return File(folder, "${docNamespace.hashCode()}_${charsetName.hashCode()}.txt")
    }

    private fun StringBuilder.appendNodes(nodes: List<OutlineNode>, level: Int) {
        nodes.forEach { node ->
            append(level.coerceIn(1, 6))
            append('\t')
            append(node.locator.value)
            append('\t')
            append(node.title.replace('\n', ' '))
            append('\n')
            if (node.children.isNotEmpty()) {
                appendNodes(node.children, level + 1)
            }
        }
    }
}
