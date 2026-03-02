package com.ireader.engines.epub.internal.provider

import com.ireader.engines.epub.internal.locator.toReadiumLocatorOrNull
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
        private const val GROUP_ANNOTATIONS = "user.annotations"
        private const val DEFAULT_HIGHLIGHT: Int = 0x66FFF59D
    }

    private val observeJob: Job = scope.launch {
        store.observe(documentId)
            .distinctUntilChanged()
            .catch { emit(emptyList()) }
            .collectLatest { annotations ->
                val decorations = annotations.mapNotNull { annotation ->
                    annotation.toReadiumDecorationOrNull()
                }
                decorationsHost.apply(GROUP_ANNOTATIONS, decorations)
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
    }

    private fun Annotation.toReadiumDecorationOrNull(): Decoration? {
        val reflow = anchor as? AnnotationAnchor.ReflowRange ?: return null
        val locator = reflow.range.start.toReadiumLocatorOrNull() ?: return null

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

    private fun applyOpacity(colorArgb: Int, opacity: Float?): Int {
        if (opacity == null) return colorArgb
        val alpha = ((opacity.coerceIn(0f, 1f) * 255f).toInt() and 0xFF) shl 24
        return (colorArgb and 0x00FFFFFF) or alpha
    }
}
