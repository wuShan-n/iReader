package com.ireader.engines.txt.internal.storage

internal data class ChunkAnchor(
    val charOffset: Int,
    val byteOffset: Long
)

internal class ChunkIndex(
    val anchors: List<ChunkAnchor>,
    val totalChars: Int,
    val startByteOffset: Long
) {
    fun anchorFor(targetChar: Int): ChunkAnchor {
        if (anchors.isEmpty()) return ChunkAnchor(0, startByteOffset)

        val target = targetChar.coerceAtLeast(0)
        var lo = 0
        var hi = anchors.lastIndex
        var best = anchors[0]

        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val candidate = anchors[mid]
            if (candidate.charOffset <= target) {
                best = candidate
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return best
    }
}
