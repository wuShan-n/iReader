package com.ireader.engines.txt.internal.provider

import com.ireader.engines.txt.internal.storage.TxtTextStore
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.model.OutlineNode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class TxtOutlineProvider(
    private val store: TxtTextStore,
    private val ioDispatcher: CoroutineDispatcher,
    private val cache: TxtOutlineCache
) : OutlineProvider {

    private val maxNodes = 500
    private val chunkChars = 64 * 1024

    private val chineseChapter = Regex("""^\s*(第.{1,12}[章节回卷部篇])\s*(.*)$""")
    private val englishChapter = Regex("""^\s*(CHAPTER|Chapter)\s+\d+.*$""")
    private val markdownWithLevel = Regex("""^\s*(#{1,6})\s+(.+)$""")
    private val numberedWithLevel = Regex("""^\s*(\d+(?:\.\d+)*)\s*[\.\、\)]\s+(.+)$""")

    override suspend fun getOutline(): ReaderResult<List<OutlineNode>> = withContext(ioDispatcher) {
        cache.getMemory()?.let { return@withContext ReaderResult.Ok(it) }

        cache.loadFromDisk()?.let { restored ->
            cache.setMemory(restored)
            return@withContext ReaderResult.Ok(restored)
        }

        runCatching {
            val total = store.totalChars().coerceAtLeast(0)
            if (total == 0) return@runCatching emptyList()

            val builder = TxtOutlineTreeBuilder(asTree = cache.asTree)
            var added = 0
            var offset = 0
            var carry = ""

            while (offset < total && added < maxNodes) {
                val readLen = (total - offset).coerceAtMost(chunkChars)
                val chunk = store.readChars(offset, readLen)
                if (chunk.isEmpty()) break

                val merged = carry + chunk
                val lines = merged.split('\n')
                val completeLineCount = if (merged.endsWith('\n')) lines.size else lines.size - 1
                val baseOffset = (offset - carry.length).coerceAtLeast(0)
                var localOffset = 0

                for (i in 0 until completeLineCount) {
                    val rawLine = lines[i]
                    val cleaned = rawLine.trimEnd('\r')
                    val titleInfo = extractTitleWithLevel(cleaned)
                    if (titleInfo != null) {
                        val lineStart = (baseOffset + localOffset).coerceAtLeast(0)
                        builder.add(
                            level = titleInfo.level,
                            title = titleInfo.title,
                            startChar = lineStart
                        )
                        added += 1
                        if (added >= maxNodes) break
                    }
                    localOffset += rawLine.length + 1
                }

                carry = if (merged.endsWith('\n')) "" else lines.lastOrNull().orEmpty()
                offset += chunk.length
            }

            if (carry.isNotBlank() && added < maxNodes) {
                val titleInfo = extractTitleWithLevel(carry.trimEnd('\r'))
                if (titleInfo != null) {
                    val lineStart = (total - carry.length).coerceAtLeast(0)
                    builder.add(
                        level = titleInfo.level,
                        title = titleInfo.title,
                        startChar = lineStart
                    )
                }
            }

            builder.build()
        }.fold(
            onSuccess = { nodes ->
                cache.setMemory(nodes)
                cache.saveToDisk(nodes)
                ReaderResult.Ok(nodes)
            },
            onFailure = {
                ReaderResult.Ok(emptyList())
            }
        )
    }

    private data class TitleInfo(
        val title: String,
        val level: Int
    )

    private fun extractTitleWithLevel(line: String): TitleInfo? {
        if (line.isBlank()) return null

        markdownWithLevel.matchEntire(line)?.let { match ->
            return TitleInfo(
                title = match.groupValues[2].trim(),
                level = match.groupValues[1].length.coerceIn(1, 6)
            )
        }

        chineseChapter.matchEntire(line)?.let { match ->
            val head = match.groupValues[1].trim()
            val tail = match.groupValues.getOrNull(2)?.trim().orEmpty()
            val title = if (tail.isEmpty()) head else "$head $tail"
            val level = if (head.contains("节")) 2 else 1
            return TitleInfo(title = title, level = level)
        }

        if (englishChapter.matches(line)) {
            return TitleInfo(title = line.trim(), level = 1)
        }

        numberedWithLevel.matchEntire(line)?.let { match ->
            val seg = match.groupValues[1]
            val level = (seg.count { it == '.' } + 1).coerceIn(1, 6)
            return TitleInfo(title = line.trim(), level = level)
        }

        return null
    }
}
