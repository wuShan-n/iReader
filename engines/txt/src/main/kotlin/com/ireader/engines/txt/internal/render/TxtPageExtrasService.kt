package com.ireader.engines.txt.internal.render

import com.ireader.engines.common.cache.LruCache
import com.ireader.engines.txt.internal.link.LinkDetector
import com.ireader.engines.txt.internal.open.TxtBlockIndex
import com.ireader.engines.txt.internal.projection.TextProjectionEngine
import com.ireader.reader.api.annotation.Decoration
import com.ireader.reader.api.error.getOrNull
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.AnnotationQuery
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.LocatorRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class TxtPageExtras(
    val links: List<DocumentLink>,
    val decorations: List<Decoration>
)

internal class TxtPageExtrasService(
    maxPageCache: Int,
    private val blockIndex: TxtBlockIndex,
    private val contentFingerprint: String,
    private val projectionEngine: TextProjectionEngine,
    private val annotationProvider: AnnotationProvider?,
    launchTask: (String, suspend CoroutineScope.() -> Unit) -> Job
) {
    private val cacheMutex = Mutex()
    private val pageLinksCache = LruCache<PageRangeKey, List<DocumentLink>>((maxPageCache * 3).coerceAtLeast(8))
    private val pageDecorCache = LruCache<DecorKey, List<Decoration>>((maxPageCache * 3).coerceAtLeast(8))

    private var annotationObserverJob: Job? = null
    private var annotationRevision: Long = 0L
    private var hasAnyAnnotations: Boolean = false

    init {
        val provider = annotationProvider
        if (provider != null) {
            annotationObserverJob = launchTask("observe-annotations") {
                provider.observeAll().collect { list ->
                    cacheMutex.withLock {
                        annotationRevision++
                        hasAnyAnnotations = list.isNotEmpty()
                        pageDecorCache.clear()
                    }
                }
            }
        }
    }

    suspend fun pageExtrasFor(
        startOffset: Long,
        endOffset: Long,
        text: CharSequence,
        range: LocatorRange,
        projectedBoundaryToRawOffsets: LongArray
    ): TxtPageExtras {
        val links = linksFor(
            startOffset = startOffset,
            endOffset = endOffset,
            text = text,
            projectedBoundaryToRawOffsets = projectedBoundaryToRawOffsets
        )
        val decorations = decorationsFor(
            startOffset = startOffset,
            endOffset = endOffset,
            range = range
        )
        return TxtPageExtras(links = links, decorations = decorations)
    }

    suspend fun invalidate() {
        cacheMutex.withLock {
            pageLinksCache.clear()
            pageDecorCache.clear()
        }
    }

    fun close() {
        annotationObserverJob?.cancel()
        annotationObserverJob = null
    }

    private suspend fun linksFor(
        startOffset: Long,
        endOffset: Long,
        text: CharSequence,
        projectedBoundaryToRawOffsets: LongArray
    ): List<DocumentLink> {
        val key = PageRangeKey(startOffset = startOffset, endOffset = endOffset)
        cacheMutex.withLock {
            pageLinksCache[key]?.let { return it }
        }
        val detected = LinkDetector.detect(
            text = text,
            pageStartOffset = startOffset,
            blockIndex = blockIndex,
            contentFingerprint = contentFingerprint,
            projectionEngine = projectionEngine,
            projectedBoundaryToRawOffsets = projectedBoundaryToRawOffsets
        )
        return cacheMutex.withLock {
            pageLinksCache[key] ?: detected.also { pageLinksCache[key] = it }
        }
    }

    private suspend fun decorationsFor(
        startOffset: Long,
        endOffset: Long,
        range: LocatorRange
    ): List<Decoration> {
        val provider = annotationProvider ?: return emptyList()
        val lookup = cacheMutex.withLock {
            if (!hasAnyAnnotations) {
                return@withLock DecorLookup(
                    key = null,
                    revision = annotationRevision,
                    cached = emptyList(),
                    shouldQuery = false
                )
            }
            val revision = annotationRevision
            val key = DecorKey(startOffset = startOffset, endOffset = endOffset, rev = revision)
            DecorLookup(
                key = key,
                revision = revision,
                cached = pageDecorCache[key],
                shouldQuery = true
            )
        }
        lookup.cached?.let { return it }
        if (!lookup.shouldQuery || lookup.key == null) {
            return emptyList()
        }

        val queried = provider
            .decorationsFor(AnnotationQuery(range = range))
            .getOrNull()
            ?: emptyList()
        return cacheMutex.withLock {
            val latestRevision = annotationRevision
            if (latestRevision != lookup.revision) {
                val latestKey = DecorKey(
                    startOffset = startOffset,
                    endOffset = endOffset,
                    rev = latestRevision
                )
                pageDecorCache[latestKey] ?: queried
            } else {
                pageDecorCache[lookup.key] = queried
                queried
            }
        }
    }

    private data class PageRangeKey(
        val startOffset: Long,
        val endOffset: Long
    )

    private data class DecorKey(
        val startOffset: Long,
        val endOffset: Long,
        val rev: Long
    )

    private data class DecorLookup(
        val key: DecorKey?,
        val revision: Long,
        val cached: List<Decoration>?,
        val shouldQuery: Boolean
    )
}
