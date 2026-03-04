package com.ireader.engines.epub.internal.provider

import com.ireader.engines.epub.internal.locator.toReadiumLocatorOrNull
import com.ireader.engines.epub.internal.locator.withReadiumFragments
import com.ireader.engines.epub.internal.render.EpubDecorationsHost
import com.ireader.reader.api.annotation.Decoration as AppDecoration
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.AnnotationQuery
import com.ireader.reader.api.provider.AnnotationStore
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.annotation.Annotation
import com.ireader.reader.model.annotation.AnnotationAnchor
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationId
import com.ireader.reader.model.annotation.AnnotationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.readium.r2.navigator.Decoration

internal class EpubAnnotationProvider(
    private val documentId: DocumentId,
    private val store: AnnotationStore,
    private val decorationsHost: EpubDecorationsHost,
    private val scope: CoroutineScope
) : AnnotationProvider {

    companion object {
        private const val GROUP_ANNOTATION_PREFIX = "user.annotation."
        private const val DEFAULT_HIGHLIGHT: Int = 0x66FFF59D
    }

    private var renderedDecorations: Map<String, Decoration> = emptyMap()

    private val observeJob: Job = scope.launch {
        store.observe(documentId)
            .distinctUntilChanged()
            .catch { emit(emptyList()) }
            .collectLatest { annotations ->
                val next = annotations.associateNotNull { annotation ->
                    annotation.toReadiumDecorationOrNull()?.let { decoration ->
                        annotation.id.value to decoration
                    }
                }
                applyIncrementalDecorations(next)
            }
    }

    override fun observeAll() = store.observe(documentId)

    override suspend fun listAll(): ReaderResult<List<Annotation>> =
        store.list(documentId)

    override suspend fun query(query: AnnotationQuery): ReaderResult<List<Annotation>> =
        store.query(documentId, query)

    override suspend fun create(draft: AnnotationDraft): ReaderResult<Annotation> =
        store.create(documentId, draft)

    override suspend fun update(annotation: Annotation): ReaderResult<Unit> =
        store.update(documentId, annotation)

    override suspend fun delete(id: AnnotationId): ReaderResult<Unit> =
        store.delete(documentId, id)

    override suspend fun decorationsFor(query: AnnotationQuery): ReaderResult<List<AppDecoration>> {
        return when (val result = store.query(documentId, query)) {
            is ReaderResult.Err -> result
            is ReaderResult.Ok -> {
                val mapped = result.value.map { annotation ->
                    when (val anchor = annotation.anchor) {
                        is AnnotationAnchor.ReflowRange -> {
                            AppDecoration.Reflow(
                                range = anchor.range,
                                style = annotation.style
                            )
                        }

                        is AnnotationAnchor.FixedRects -> {
                            AppDecoration.Fixed(
                                page = anchor.page,
                                rects = anchor.rects,
                                style = annotation.style
                            )
                        }
                    }
                }
                ReaderResult.Ok(mapped)
            }
        }
    }

    fun closeInternal() {
        observeJob.cancel()
        renderedDecorations = emptyMap()
    }

    private fun Annotation.toReadiumDecorationOrNull(): Decoration? {
        val reflow = anchor as? AnnotationAnchor.ReflowRange ?: return null
        val locator = rangeToReadiumLocatorOrNull(reflow.range) ?: return null

        val tint = applyOpacity(
            colorArgb = style.colorArgb ?: DEFAULT_HIGHLIGHT,
            opacity = style.opacity
        )

        val decorationStyle = when (type) {
            AnnotationType.HIGHLIGHT,
            AnnotationType.NOTE,
            AnnotationType.BOOKMARK -> Decoration.Style.Highlight(tint = tint)

            AnnotationType.UNDERLINE -> Decoration.Style.Underline(tint = tint)
        }

        return Decoration(
            id = id.value,
            locator = locator,
            style = decorationStyle,
            extras = mapOf(
                "type" to type.name
            )
        )
    }

    private suspend fun applyIncrementalDecorations(next: Map<String, Decoration>) {
        val previous = renderedDecorations
        val removedIds = previous.keys - next.keys
        for (id in removedIds) {
            decorationsHost.apply(groupFor(id), emptyList())
        }
        for ((id, decoration) in next) {
            val previousDecoration = previous[id]
            if (previousDecoration != decoration) {
                decorationsHost.apply(groupFor(id), listOf(decoration))
            }
        }
        renderedDecorations = next
    }

    private fun groupFor(annotationId: String): String = GROUP_ANNOTATION_PREFIX + annotationId

    private fun rangeToReadiumLocatorOrNull(range: com.ireader.reader.model.LocatorRange): org.readium.r2.shared.publication.Locator? {
        val start = range.start.toReadiumLocatorOrNull() ?: return null
        val end = range.end.toReadiumLocatorOrNull()
        val startFragments = start.locations.fragments.filter { it.isNotBlank() }
        val endFragments = end?.locations?.fragments?.filter { it.isNotBlank() }.orEmpty()
        val mergedFragments = when {
            startFragments.isEmpty() && endFragments.isEmpty() -> emptyList()
            startFragments.isEmpty() -> listOf(endFragments.first())
            endFragments.isEmpty() -> listOf(startFragments.first())
            else -> listOf(startFragments.first(), endFragments.last())
        }
        if (mergedFragments.isEmpty()) {
            return start
        }
        return range.start
            .withReadiumFragments(mergedFragments)
            .toReadiumLocatorOrNull()
    }

    private fun applyOpacity(colorArgb: Int, opacity: Float?): Int {
        if (opacity == null) return colorArgb
        val alpha = ((opacity.coerceIn(0f, 1f) * 255f).toInt() and 0xFF) shl 24
        return (colorArgb and 0x00FFFFFF) or alpha
    }
}

private inline fun <K, V, R> Iterable<V>.associateNotNull(transform: (V) -> Pair<K, R>?): Map<K, R> {
    val out = LinkedHashMap<K, R>()
    for (item in this) {
        val pair = transform(item) ?: continue
        out[pair.first] = pair.second
    }
    return out
}
