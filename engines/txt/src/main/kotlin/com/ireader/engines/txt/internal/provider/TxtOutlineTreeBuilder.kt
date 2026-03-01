package com.ireader.engines.txt.internal.provider

import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.OutlineNode
import java.util.ArrayDeque

internal class TxtOutlineTreeBuilder(
    private val asTree: Boolean
) {
    private data class MutableNode(
        val level: Int,
        val title: String,
        val startChar: Int,
        val children: MutableList<MutableNode> = mutableListOf()
    )

    private val roots = mutableListOf<MutableNode>()
    private val stack = ArrayDeque<MutableNode>()

    fun add(level: Int, title: String, startChar: Int) {
        val safeLevel = level.coerceIn(1, 6)
        val node = MutableNode(
            level = safeLevel,
            title = title,
            startChar = startChar.coerceAtLeast(0)
        )

        if (!asTree) {
            roots.add(node)
            return
        }

        while (stack.isNotEmpty() && stack.last().level >= safeLevel) {
            stack.removeLast()
        }

        if (stack.isEmpty()) {
            roots.add(node)
        } else {
            stack.last().children.add(node)
        }
        stack.addLast(node)
    }

    fun build(): List<OutlineNode> = roots.map { it.toOutlineNode() }

    private fun MutableNode.toOutlineNode(): OutlineNode = OutlineNode(
        title = title,
        locator = Locator(LocatorSchemes.TXT_OFFSET, startChar.toString()),
        children = children.map { it.toOutlineNode() }
    )
}
