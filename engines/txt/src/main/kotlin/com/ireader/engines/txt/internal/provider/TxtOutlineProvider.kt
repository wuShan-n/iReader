@file:Suppress("MagicNumber", "NestedBlockDepth", "ReturnCount", "TooGenericExceptionCaught")

package com.ireader.engines.txt.internal.provider

import com.ireader.engines.common.android.error.toReaderError
import com.ireader.engines.txt.internal.locator.TxtBlockLocatorCodec
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.model.OutlineNode
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class TxtOutlineProvider(
    private val files: TxtBookFiles,
    private val meta: TxtMeta,
    private val store: Utf16TextStore,
    private val ioDispatcher: CoroutineDispatcher,
    private val persistOutline: Boolean
) : OutlineProvider {

    private val detector = ChapterDetector()

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
                ReaderResult.Ok(detected)
            } catch (t: Throwable) {
                ReaderResult.Err(t.toReaderError())
            }
        }
    }

    private fun loadFromCache(): List<OutlineNode>? {
        val file = files.outlineJson
        if (!file.exists()) {
            return null
        }
        val json = JSONObject(file.readText())
        if (json.optInt("version", -1) != 1) {
            return null
        }
        if (json.optString("sampleHash", "") != meta.sampleHash) {
            return null
        }
        val items = json.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<OutlineNode>(items.length())
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val title = item.optString("title")
            val offset = item.optLong("offset", -1L)
            if (title.isBlank() || offset < 0L) {
                continue
            }
            out.add(
                OutlineNode(
                    title = title,
                    locator = TxtBlockLocatorCodec.locatorForOffset(offset, store.lengthChars)
                )
            )
        }
        return out
    }

    private fun saveToCache(outline: List<OutlineNode>) {
        val items = JSONArray()
        for (node in outline) {
            val offset = TxtBlockLocatorCodec.parseOffset(node.locator, store.lengthChars) ?: continue
            items.put(
                JSONObject().apply {
                    put("title", node.title)
                    put("offset", offset)
                }
            )
        }
        val root = JSONObject().apply {
            put("version", 1)
            put("sampleHash", meta.sampleHash)
            put("items", items)
        }
        files.outlineJson.writeText(root.toString())
    }

    private suspend fun detectOutline(): List<OutlineNode> {
        val out = ArrayList<OutlineNode>(64)
        val seen = HashSet<String>()
        val chunkChars = 64_000
        var carry = ""
        var cursor = 0L

        while (cursor < store.lengthChars) {
            coroutineContext.ensureActive()
            val readCount = min(chunkChars.toLong(), store.lengthChars - cursor).toInt()
            val chunk = store.readString(cursor, readCount)
            if (chunk.isEmpty()) {
                break
            }
            val merged = carry + chunk
            var lineStart = 0
            while (true) {
                val newline = merged.indexOf('\n', lineStart)
                if (newline < 0) {
                    break
                }
                val line = merged.substring(lineStart, newline).trim()
                if (detector.isChapterTitle(line) && seen.add(line)) {
                    val offset = (cursor - carry.length + lineStart).coerceAtLeast(0)
                    out.add(
                        OutlineNode(
                            title = line,
                            locator = TxtBlockLocatorCodec.locatorForOffset(offset, store.lengthChars)
                        )
                    )
                    if (out.size >= MAX_OUTLINE_ITEMS) {
                        return out
                    }
                }
                lineStart = newline + 1
            }
            carry = merged.substring(lineStart)
            cursor += readCount.toLong()
        }
        return out
    }

    private companion object {
        private const val MAX_OUTLINE_ITEMS = 300
    }
}
