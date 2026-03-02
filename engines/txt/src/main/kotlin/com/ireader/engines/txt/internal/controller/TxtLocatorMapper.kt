package com.ireader.engines.txt.internal.controller

import com.ireader.engines.txt.internal.storage.TxtTextStore
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal object TxtLocatorExtras {
    const val VERSION = "v"
    const val PROGRESSION = "progression"
    const val SNIPPET = "snippet"
    const val SNIPPET_HASH = "snippetHash"
    const val VERSION_VALUE = "2"
}

internal class TxtLocatorMapper(
    private val store: TxtTextStore,
    private val snippetLength: Int = 48,
    private val sampleStrideChars: Int = 32 * 1024,
    private val sampleWindowChars: Int = 512,
    private val maxSamples: Int = 512,
    private val smallDocumentFullScanThresholdChars: Int = 600_000,
    private val snippetWindowMinChars: Int = 4_096,
    private val snippetWindowMaxChars: Int = 256_000,
    private val snippetWindowCapChars: Int = 1_000_000
) {
    private data class SnippetSample(
        val offset: Int,
        val text: String
    )

    private data class SparseSnippetIndex(
        val totalChars: Int,
        val samples: List<SnippetSample>
    )

    @Volatile
    private var sparseIndex: SparseSnippetIndex? = null

    fun locatorForOffsetFast(offset: Int, totalChars: Int): Locator {
        if (totalChars <= 0) {
            return Locator(
                scheme = LocatorSchemes.TXT_OFFSET,
                value = "0",
                extras = mapOf(
                    TxtLocatorExtras.VERSION to TxtLocatorExtras.VERSION_VALUE,
                    TxtLocatorExtras.PROGRESSION to "0.000000"
                )
            )
        }
        val clamped = offset.coerceIn(0, totalChars - 1)
        val progression = clamped.toDouble() / totalChars.toDouble()
        return locatorWithProgression(clamped.toString(), progression)
    }

    fun locatorForBoundaryOffset(offset: Int, totalChars: Int): Locator {
        if (totalChars <= 0) {
            return Locator(
                scheme = LocatorSchemes.TXT_OFFSET,
                value = "0",
                extras = mapOf(
                    TxtLocatorExtras.VERSION to TxtLocatorExtras.VERSION_VALUE,
                    TxtLocatorExtras.PROGRESSION to "0.000000"
                )
            )
        }
        val clamped = offset.coerceIn(0, totalChars)
        val progression = clamped.toDouble() / totalChars.toDouble()
        return locatorWithProgression(clamped.toString(), progression)
    }

    private fun locatorWithProgression(value: String, progression: Double): Locator {
        return Locator(
            scheme = LocatorSchemes.TXT_OFFSET,
            value = value,
            extras = mapOf(
                TxtLocatorExtras.VERSION to TxtLocatorExtras.VERSION_VALUE,
                TxtLocatorExtras.PROGRESSION to String.format(Locale.US, "%.6f", progression)
            )
        )
    }

    suspend fun locatorForOffset(offset: Int, totalChars: Int): Locator {
        if (totalChars <= 0) {
            return locatorForOffsetFast(0, 0)
        }

        val clamped = offset.coerceIn(0, totalChars - 1)
        val base = locatorForOffsetFast(clamped, totalChars)
        val snippet = readSnippet(clamped, totalChars)
        val extras = buildMap<String, String> {
            putAll(base.extras)
            if (snippet.isNotBlank()) {
                put(TxtLocatorExtras.SNIPPET, snippet)
                put(TxtLocatorExtras.SNIPPET_HASH, hashSnippet(snippet))
            }
        }

        return base.copy(extras = extras)
    }

    suspend fun offsetForLocator(locator: Locator?, totalChars: Int): Int {
        if (totalChars <= 0) return 0
        if (locator == null || locator.scheme != LocatorSchemes.TXT_OFFSET) return 0

        val parsed = locator.value.toIntOrNull()?.coerceIn(0, totalChars - 1) ?: 0

        val snippet = locator.extras[TxtLocatorExtras.SNIPPET]?.takeIf { it.isNotBlank() }
        if (snippet != null) {
            resolveBySnippet(parsed, snippet, totalChars)?.let { return it }
        }

        val progression = locator.extras[TxtLocatorExtras.PROGRESSION]?.toDoubleOrNull()
        if (progression != null && progression.isFinite()) {
            val target = (progression.coerceIn(0.0, 1.0) * totalChars).toInt()
            return target.coerceIn(0, totalChars - 1)
        }

        return parsed
    }

    private suspend fun readSnippet(offset: Int, totalChars: Int): String {
        val half = (snippetLength / 2).coerceAtLeast(12)
        val start = max(0, offset - half)
        val end = min(totalChars, start + snippetLength.coerceAtLeast(24))
        if (end <= start) return ""
        return store.readRange(start, end)
    }

    private suspend fun resolveBySnippet(
        fallbackOffset: Int,
        snippet: String,
        totalChars: Int
    ): Int? {
        val baseWindow = (snippet.length * 1024)
            .coerceIn(snippetWindowMinChars, snippetWindowMaxChars)
        val scales = intArrayOf(1, 2, 4)
        for (scale in scales) {
            val size = (baseWindow * scale).coerceAtMost(snippetWindowCapChars)
            val windowStart = (fallbackOffset - size / 2).coerceAtLeast(0)
            val windowEnd = (windowStart + size).coerceAtMost(totalChars)
            if (windowEnd <= windowStart) continue
            val segment = store.readRange(windowStart, windowEnd)
            val nearest = nearestMatch(segment, snippet, fallbackOffset - windowStart)
            if (nearest >= 0) return windowStart + nearest
        }

        searchSparseSamples(snippet, fallbackOffset, totalChars)?.let { return it }

        if (totalChars <= smallDocumentFullScanThresholdChars) {
            val full = store.readRange(0, totalChars)
            val nearest = nearestMatch(full, snippet, fallbackOffset)
            if (nearest >= 0) return nearest
        }
        return null
    }

    private suspend fun searchSparseSamples(
        snippet: String,
        fallbackOffset: Int,
        totalChars: Int
    ): Int? {
        val index = ensureSparseIndex(totalChars)
        if (index.samples.isEmpty()) return null

        var best: Int? = null
        var bestDistance = Int.MAX_VALUE
        for (sample in index.samples) {
            val local = sample.text.indexOf(snippet)
            if (local < 0) continue
            val candidate = (sample.offset + local).coerceIn(0, totalChars - 1)
            val distance = abs(candidate - fallbackOffset)
            if (distance < bestDistance) {
                bestDistance = distance
                best = candidate
            }
        }
        return best
    }

    private suspend fun ensureSparseIndex(totalChars: Int): SparseSnippetIndex {
        val cached = sparseIndex
        if (cached != null && cached.totalChars == totalChars) return cached

        val stride = sampleStrideChars
            .coerceAtLeast(max(snippetLength * 4, 2_048))
            .coerceAtMost(max(totalChars, 2_048))
        val windowChars = sampleWindowChars
            .coerceAtLeast(snippetLength * 2)
            .coerceIn(128, 4096)
        val limit = maxSamples.coerceAtLeast(16)

        val samples = ArrayList<SnippetSample>(min(limit, (totalChars / stride) + 2))
        var offset = 0
        while (offset < totalChars && samples.size < limit) {
            val end = (offset + windowChars).coerceAtMost(totalChars)
            if (end > offset) {
                val text = store.readRange(offset, end)
                if (text.isNotEmpty()) {
                    samples.add(SnippetSample(offset = offset, text = text))
                }
            }
            offset += stride
        }
        if (samples.isEmpty() && totalChars > 0) {
            val end = min(totalChars, windowChars)
            val text = store.readRange(0, end)
            if (text.isNotEmpty()) {
                samples.add(SnippetSample(offset = 0, text = text))
            }
        }

        return SparseSnippetIndex(
            totalChars = totalChars,
            samples = samples
        ).also { sparseIndex = it }
    }

    private fun nearestMatch(haystack: String, needle: String, pivot: Int): Int {
        if (needle.isEmpty() || haystack.length < needle.length) return -1
        var idx = haystack.indexOf(needle)
        var best = -1
        var bestDistance = Int.MAX_VALUE
        while (idx >= 0) {
            val distance = abs(idx - pivot)
            if (distance < bestDistance) {
                bestDistance = distance
                best = idx
            }
            idx = haystack.indexOf(needle, idx + 1)
        }
        return best
    }

    private fun hashSnippet(snippet: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(snippet.toByteArray(Charsets.UTF_8))
        return buildString(16) {
            for (i in 0 until 8) {
                val b = digest[i].toInt() and 0xFF
                append("0123456789abcdef"[b ushr 4])
                append("0123456789abcdef"[b and 0x0F])
            }
        }
    }
}
