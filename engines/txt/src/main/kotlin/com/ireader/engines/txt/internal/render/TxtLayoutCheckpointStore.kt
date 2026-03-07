package com.ireader.engines.txt.internal.render

import com.ireader.engines.common.pagination.PageMap
import java.io.File
import java.util.TreeSet
import kotlin.math.roundToInt

internal class TxtLayoutCheckpointStore(
    private val enabled: Boolean,
    private val paginationDir: File
) {
    private var layoutHash: String? = null
    private var pageStarts: TreeSet<Long> = TreeSet()
    private var dirty: Boolean = false

    fun bindLayout(layoutHash: String?) {
        if (!enabled || layoutHash.isNullOrBlank()) {
            clearState()
            return
        }
        if (this.layoutHash == layoutHash) {
            return
        }
        saveIfDirty()
        this.layoutHash = layoutHash
        pageStarts = PageMap.load(checkpointFile(layoutHash))
        dirty = false
    }

    fun activeLayoutHash(): String? = layoutHash?.takeIf { enabled && it.isNotBlank() }

    fun nearestStartBefore(offsetExclusive: Long): Long? {
        if (!enabled) {
            return null
        }
        return pageStarts.lower(offsetExclusive)
    }

    fun startForProgress(percent: Double): Long? {
        if (!enabled || pageStarts.size < 4) {
            return null
        }
        val normalized = percent.coerceIn(0.0, 1.0)
        val values = pageStarts.toList()
        val index = ((values.lastIndex) * normalized)
            .roundToInt()
            .coerceIn(0, values.lastIndex)
        return values[index]
    }

    fun record(startOffset: Long) {
        val layout = activeLayoutHash() ?: return
        if (pageStarts.add(startOffset.coerceAtLeast(0L))) {
            dirty = true
            if (pageStarts.size % SAVE_INTERVAL_PAGES == 0) {
                saveIfDirty()
            }
        }
        if (pageStarts.firstOrNull() != 0L) {
            pageStarts.add(0L)
            dirty = true
        }
        layout
    }

    fun saveIfDirty() {
        val layout = activeLayoutHash() ?: return
        if (!dirty) {
            return
        }
        PageMap.save(checkpointFile(layout), sparseCheckpoints())
        dirty = false
    }

    fun invalidate() {
        clearState()
    }

    fun invalidateAll() {
        clearState()
        runCatching { File(paginationDir, "layout").deleteRecursively() }
    }

    private fun sparseCheckpoints(): List<Long> {
        if (pageStarts.isEmpty()) {
            return emptyList()
        }
        val values = pageStarts.toList()
        return buildList {
            values.forEachIndexed { index, value ->
                if (index == 0 || index == values.lastIndex || index % CHECKPOINT_INTERVAL_PAGES == 0) {
                    add(value)
                }
            }
        }
    }

    private fun checkpointFile(layoutHash: String): File {
        return File(File(paginationDir, "layout/$layoutHash"), "page.ckpt")
    }

    private fun clearState() {
        layoutHash = null
        pageStarts.clear()
        dirty = false
    }

    private companion object {
        private const val CHECKPOINT_INTERVAL_PAGES = 8
        private const val SAVE_INTERVAL_PAGES = 8
    }
}
