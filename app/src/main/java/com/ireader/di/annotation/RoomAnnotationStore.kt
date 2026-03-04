package com.ireader.di.annotation

import com.ireader.core.data.book.LocatorJsonCodec
import com.ireader.core.database.annotation.AnnotationAnchorType
import com.ireader.core.database.annotation.AnnotationDao
import com.ireader.core.database.annotation.AnnotationEntity
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationQuery
import com.ireader.reader.api.provider.AnnotationStore
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.NormalizedRect
import com.ireader.reader.model.annotation.Annotation
import com.ireader.reader.model.annotation.AnnotationAnchor
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationId
import com.ireader.reader.model.annotation.AnnotationStyle
import com.ireader.reader.model.annotation.AnnotationType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class RoomAnnotationStore @Inject constructor(
    private val annotationDao: AnnotationDao
) : AnnotationStore {

    override fun observe(documentId: DocumentId): Flow<List<Annotation>> {
        return annotationDao.observeByDocumentId(documentId.value)
            .map { entities ->
                entities.mapNotNull { entity -> entity.toModelOrNull() }
            }
    }

    override suspend fun list(documentId: DocumentId): ReaderResult<List<Annotation>> {
        return runCatching {
            annotationDao.listByDocumentId(documentId.value).mapNotNull { it.toModelOrNull() }
        }.fold(
            onSuccess = { ReaderResult.Ok(it) },
            onFailure = { ReaderResult.Err(ReaderError.Internal(cause = it)) }
        )
    }

    override suspend fun query(
        documentId: DocumentId,
        query: AnnotationQuery
    ): ReaderResult<List<Annotation>> {
        return runCatching {
            val entities = when {
                query.page != null -> annotationDao.listByDocumentIdAndAnchorType(
                    documentId = documentId.value,
                    anchorType = AnnotationAnchorType.FIXED_RECTS
                )

                query.range != null -> annotationDao.listByDocumentIdAndAnchorType(
                    documentId = documentId.value,
                    anchorType = AnnotationAnchorType.REFLOW_RANGE
                )

                else -> annotationDao.listByDocumentId(documentId.value)
            }
            entities.mapNotNull { entity -> entity.toModelOrNull() }
                .filter { annotation -> annotation.matches(query) }
        }.fold(
            onSuccess = { ReaderResult.Ok(it) },
            onFailure = { ReaderResult.Err(ReaderError.Internal(cause = it)) }
        )
    }

    override suspend fun create(
        documentId: DocumentId,
        draft: AnnotationDraft
    ): ReaderResult<Annotation> {
        return runCatching {
            val now = System.currentTimeMillis()
            val annotation = Annotation(
                id = AnnotationId(UUID.randomUUID().toString()),
                type = draft.type,
                anchor = draft.anchor,
                content = draft.content,
                style = draft.style,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                extra = draft.extra
            )
            annotationDao.upsert(annotation.toEntity(documentId.value))
            annotation
        }.fold(
            onSuccess = { ReaderResult.Ok(it) },
            onFailure = { ReaderResult.Err(ReaderError.Internal(cause = it)) }
        )
    }

    override suspend fun update(documentId: DocumentId, annotation: Annotation): ReaderResult<Unit> {
        return runCatching {
            val exists = annotationDao.exists(documentId.value, annotation.id.value)
            if (!exists) {
                return@runCatching ReaderResult.Err(
                    ReaderError.NotFound("Annotation not found: ${annotation.id.value}")
                )
            }
            val updated = annotation.copy(updatedAtEpochMs = System.currentTimeMillis())
            annotationDao.upsert(updated.toEntity(documentId.value))
            ReaderResult.Ok(Unit)
        }.fold(
            onSuccess = { it },
            onFailure = { ReaderResult.Err(ReaderError.Internal(cause = it)) }
        )
    }

    override suspend fun delete(documentId: DocumentId, id: AnnotationId): ReaderResult<Unit> {
        return runCatching {
            val rows = annotationDao.deleteById(documentId.value, id.value)
            if (rows <= 0) {
                ReaderResult.Err(ReaderError.NotFound("Annotation not found: ${id.value}"))
            } else {
                ReaderResult.Ok(Unit)
            }
        }.getOrElse {
            ReaderResult.Err(ReaderError.Internal(cause = it))
        }
    }

    private fun Annotation.matches(query: AnnotationQuery): Boolean {
        query.page?.let { page ->
            val fixed = anchor as? AnnotationAnchor.FixedRects ?: return false
            return sameLocator(fixed.page, page)
        }

        query.range?.let { range ->
            val reflow = anchor as? AnnotationAnchor.ReflowRange ?: return false
            return sameLocator(reflow.range.start, range.start) ||
                sameLocator(reflow.range.end, range.end)
        }

        return true
    }

    private fun sameLocator(a: Locator, b: Locator): Boolean =
        a.scheme == b.scheme && a.value == b.value

    private fun Annotation.toEntity(documentId: String): AnnotationEntity {
        val anchor = anchor
        return when (anchor) {
            is AnnotationAnchor.ReflowRange -> AnnotationEntity(
                id = id.value,
                documentId = documentId,
                type = type.name,
                anchorType = AnnotationAnchorType.REFLOW_RANGE,
                rangeStartLocatorJson = LocatorJsonCodec.encode(anchor.range.start),
                rangeEndLocatorJson = LocatorJsonCodec.encode(anchor.range.end),
                pageLocatorJson = null,
                rectsJson = null,
                content = content,
                styleJson = style.toJson(),
                extraJson = extra.toJson(),
                createdAtEpochMs = createdAtEpochMs,
                updatedAtEpochMs = updatedAtEpochMs
            )

            is AnnotationAnchor.FixedRects -> AnnotationEntity(
                id = id.value,
                documentId = documentId,
                type = type.name,
                anchorType = AnnotationAnchorType.FIXED_RECTS,
                rangeStartLocatorJson = null,
                rangeEndLocatorJson = null,
                pageLocatorJson = LocatorJsonCodec.encode(anchor.page),
                rectsJson = anchor.rects.toJson(),
                content = content,
                styleJson = style.toJson(),
                extraJson = extra.toJson(),
                createdAtEpochMs = createdAtEpochMs,
                updatedAtEpochMs = updatedAtEpochMs
            )
        }
    }

    private fun AnnotationEntity.toModelOrNull(): Annotation? {
        val type = runCatching { AnnotationType.valueOf(type) }.getOrNull() ?: return null
        val anchor = when (anchorType) {
            AnnotationAnchorType.REFLOW_RANGE -> {
                val start = rangeStartLocatorJson?.let(LocatorJsonCodec::decode) ?: return null
                val end = rangeEndLocatorJson?.let(LocatorJsonCodec::decode) ?: return null
                AnnotationAnchor.ReflowRange(LocatorRange(start = start, end = end))
            }

            AnnotationAnchorType.FIXED_RECTS -> {
                val page = pageLocatorJson?.let(LocatorJsonCodec::decode) ?: return null
                val rects = rectsJson?.toRects().orEmpty()
                AnnotationAnchor.FixedRects(page = page, rects = rects)
            }

            else -> return null
        }

        return Annotation(
            id = AnnotationId(id),
            type = type,
            anchor = anchor,
            content = content,
            style = styleJson.toAnnotationStyle(),
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
            extra = extraJson.toStringMap()
        )
    }
}

private fun AnnotationStyle.toJson(): String {
    val obj = JSONObject()
    colorArgb?.let { obj.put("colorArgb", it) }
    opacity?.let { obj.put("opacity", it) }
    obj.put("extra", JSONObject(extra))
    return obj.toString()
}

private fun String.toAnnotationStyle(): AnnotationStyle {
    val obj = runCatching { JSONObject(this) }.getOrDefault(JSONObject())
    return AnnotationStyle(
        colorArgb = if (obj.has("colorArgb")) obj.optInt("colorArgb") else null,
        opacity = if (obj.has("opacity")) obj.optDouble("opacity").toFloat() else null,
        extra = obj.optJSONObject("extra").toStringMap()
    )
}

private fun List<NormalizedRect>.toJson(): String {
    val array = JSONArray()
    forEach { rect ->
        array.put(
            JSONObject()
                .put("left", rect.left)
                .put("top", rect.top)
                .put("right", rect.right)
                .put("bottom", rect.bottom)
        )
    }
    return array.toString()
}

private fun String.toRects(): List<NormalizedRect> {
    val array = runCatching { JSONArray(this) }.getOrDefault(JSONArray())
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                NormalizedRect(
                    left = item.optDouble("left", 0.0).toFloat(),
                    top = item.optDouble("top", 0.0).toFloat(),
                    right = item.optDouble("right", 0.0).toFloat(),
                    bottom = item.optDouble("bottom", 0.0).toFloat()
                )
            )
        }
    }
}

private fun Map<String, String>.toJson(): String = JSONObject(this).toString()

private fun String?.toStringMap(): Map<String, String> {
    val obj = runCatching { JSONObject(this.orEmpty()) }.getOrDefault(JSONObject())
    return obj.toStringMap()
}

private fun JSONObject?.toStringMap(): Map<String, String> {
    val value = this ?: return emptyMap()
    return buildMap {
        val keys = value.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            put(key, value.optString(key))
        }
    }
}
