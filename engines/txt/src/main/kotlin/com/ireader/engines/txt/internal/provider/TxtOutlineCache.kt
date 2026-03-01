package com.ireader.engines.txt.internal.provider

import com.ireader.engines.txt.TxtEngineConfig
import com.ireader.reader.model.OutlineNode
import java.io.File

internal class TxtOutlineCache(
    private val config: TxtEngineConfig,
    private val docNamespace: String,
    private val charsetName: String
) {
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

        return runCatching {
            val builder = TxtOutlineTreeBuilder(asTree = asTree)
            file.readLines(Charsets.UTF_8).forEach { line ->
                val parts = line.split('\t')
                if (parts.size >= 3) {
                    val level = parts[0].toIntOrNull() ?: return@forEach
                    val offset = parts[1].toIntOrNull() ?: return@forEach
                    val title = parts.subList(2, parts.size).joinToString("\t")
                    builder.add(level, title, offset)
                } else if (parts.size == 2) {
                    // Backward compatible with the old flat format.
                    val offset = parts[0].toIntOrNull() ?: return@forEach
                    val title = parts[1]
                    builder.add(1, title, offset)
                }
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
        val folder = File(base, "reader-txt/outline")
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
