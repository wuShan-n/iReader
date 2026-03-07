@file:Suppress("MagicNumber", "NestedBlockDepth", "ReturnCount", "TooGenericExceptionCaught")

package com.ireader.engines.txt.internal.provider

import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.common.io.prepareTempFile
import com.ireader.engines.common.io.replaceFileAtomically
import com.ireader.engines.txt.internal.locator.TextAnchorAffinity
import com.ireader.engines.txt.internal.locator.TxtAnchorLocatorCodec
import com.ireader.engines.txt.internal.open.TxtBlockIndex
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.runtime.BlockStore
import com.ireader.engines.txt.internal.runtime.BreakResolver
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.model.LocatorExtraKeys
import com.ireader.reader.model.OutlineNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class TxtOutlineProvider(
    private val files: TxtBookFiles,
    private val meta: TxtMeta,
    private val blockIndex: TxtBlockIndex,
    private val breakResolver: BreakResolver,
    private val blockStore: BlockStore,
    private val ioDispatcher: CoroutineDispatcher,
    private val persistOutline: Boolean
) : OutlineProvider {

    private val detector = ChapterDetector()
    private val providerScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    fun warmup() {
        if (!persistOutline) {
            return
        }
        providerScope.launch {
            if (loadFromCache() == null) {
                saveToCache(detectOutline())
            }
        }
    }

    fun close() {
        providerScope.cancel()
    }

    fun invalidate() {
        providerScope.coroutineContext.cancelChildren()
        runCatching { files.outlineIdx.delete() }
    }

    override suspend fun getOutline(): ReaderResult<List<OutlineNode>> {
        return withContext(ioDispatcher) {
            try {
                if (persistOutline) {
                    val cached = loadFromCache()
                    if (cached != null) {
                        return@withContext ReaderResult.Ok(cached)
                    }
                }

                val detected = detectOutline()
                if (persistOutline) {
                    saveToCache(detected)
                }
                ReaderResult.Ok(detected.map(::toOutlineNode))
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                ReaderResult.Err(e.toReaderError())
            }
        }
    }

    private fun loadFromCache(): List<OutlineNode>? {
        val file = files.outlineIdx
        if (!file.exists()) {
            return null
        }
        val json = runCatching { JSONObject(file.readText()) }.getOrElse { return null }
        if (json.optInt("version", -1) != 1) {
            return null
        }
        if (json.optString("sampleHash", "") != meta.sampleHash) {
            return null
        }
        val items = json.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<OutlineNode>(items.length())
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val title = item.optString("title")
            val offset = item.optLong("offset", -1L)
            val confidence = item.optDouble("confidence", 0.0)
            if (title.isBlank() || offset < 0L || confidence < OUTLINE_CONFIDENCE_THRESHOLD) {
                continue
            }
            out.add(
                OutlineNode(
                    title = title,
                    locator = TxtAnchorLocatorCodec.locatorForOffset(
                        offset = offset,
                        blockIndex = blockIndex,
                        revision = meta.contentRevision,
                        extras = outlineExtras(
                            confidence = confidence,
                            level = item.optInt("level", 1)
                        )
                    )
                )
            )
        }
        return out
    }

    private fun saveToCache(outline: List<DetectedOutlineNode>) {
        val items = JSONArray()
        outline.forEach { node ->
            items.put(
                JSONObject().apply {
                    put("title", node.title)
                    put("offset", node.offset)
                    put("confidence", node.confidence)
                    put("level", node.level)
                }
            )
        }
        val root = JSONObject().apply {
            put("version", 1)
            put("sampleHash", meta.sampleHash)
            put("items", items)
        }
        val temp = java.io.File(files.bookDir, "outline.idx.tmp")
        prepareTempFile(temp)
        temp.writeText(root.toString())
        replaceFileAtomically(tempFile = temp, targetFile = files.outlineIdx)
    }

    private fun detectOutline(): List<DetectedOutlineNode> {
        val out = ArrayList<DetectedOutlineNode>(64)
        val seen = HashSet<String>()
        var cursor = blockStore.anchorForOffset(0L, TextAnchorAffinity.FORWARD)
        var safety = 0
        while (cursor.utf16Offset < blockIndex.lengthCodeUnits && safety < MAX_BATCHES) {
            val batch = blockStore.readParagraphs(
                startAnchor = cursor,
                codeUnitBudget = OUTLINE_SCAN_BUDGET
            )
            if (batch.paragraphs.isEmpty()) {
                break
            }
            safety++
            batch.paragraphs.forEach { paragraph ->
                val title = paragraph.displayText.trim()
                if (!detector.isChapterTitle(title) || !seen.add(title)) {
                    return@forEach
                }
                val confidence = confidenceFor(title, paragraph.displayText)
                if (confidence < OUTLINE_CONFIDENCE_THRESHOLD) {
                    return@forEach
                }
                out.add(
                    DetectedOutlineNode(
                        title = title,
                        offset = paragraph.startOffset,
                        level = 1,
                        confidence = confidence
                    )
                )
                if (out.size >= MAX_OUTLINE_ITEMS) {
                    return out
                }
            }
            cursor = batch.nextAnchor ?: break
        }
        return out
    }

    private fun confidenceFor(title: String, paragraphText: String): Double {
        var score = 0.55
        if (title.length in 2..32) {
            score += 0.15
        }
        if ('\n' !in paragraphText.trimEnd()) {
            score += 0.15
        }
        if (paragraphText.endsWith('\n')) {
            score += 0.10
        }
        if (title.any { it.isDigit() } || title.any { it == '第' || it == '章' || it == '卷' }) {
            score += 0.10
        }
        return score.coerceAtMost(1.0)
    }

    private fun toOutlineNode(node: DetectedOutlineNode): OutlineNode {
        return OutlineNode(
            title = node.title,
            locator = TxtAnchorLocatorCodec.locatorForOffset(
                offset = node.offset,
                blockIndex = blockIndex,
                revision = meta.contentRevision,
                extras = outlineExtras(
                    confidence = node.confidence,
                    level = node.level
                )
            )
        )
    }

    private fun outlineExtras(
        confidence: Double,
        level: Int
    ): Map<String, String> {
        return mapOf(
            LocatorExtraKeys.OUTLINE_CONFIDENCE to confidence.toString(),
            LocatorExtraKeys.OUTLINE_LEVEL to level.toString()
        )
    }

    private data class DetectedOutlineNode(
        val title: String,
        val offset: Long,
        val level: Int,
        val confidence: Double
    )

    private companion object {
        private const val MAX_OUTLINE_ITEMS = 300
        private const val MAX_BATCHES = 64
        private const val OUTLINE_SCAN_BUDGET = 64_000
        private const val OUTLINE_CONFIDENCE_THRESHOLD = 0.65
    }
}
