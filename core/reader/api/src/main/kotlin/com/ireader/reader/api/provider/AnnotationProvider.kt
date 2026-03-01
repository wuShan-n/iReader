package com.ireader.reader.api.provider

import com.ireader.reader.model.annotation.Annotation
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationId
import com.ireader.reader.api.annotation.Decoration
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import kotlinx.coroutines.flow.Flow

data class AnnotationQuery(
    val page: Locator? = null,            // fixed 查询（PDF）
    val range: LocatorRange? = null       // reflow 查询（TXT/EPUB）
)

interface AnnotationProvider {

    fun observeAll(): Flow<List<Annotation>>

    suspend fun listAll(): ReaderResult<List<Annotation>>

    suspend fun query(query: AnnotationQuery): ReaderResult<List<Annotation>>

    suspend fun create(draft: AnnotationDraft): ReaderResult<Annotation>

    suspend fun update(annotation: Annotation): ReaderResult<Unit>

    suspend fun delete(id: AnnotationId): ReaderResult<Unit>

    /**
     * 提供给渲染层的 Decoration（默认可以由 annotation->decoration 直接映射）
     * 引擎也可以做更复杂的映射（比如 EPUB CFI 对应到章节分页后的 charRange）。
     */
    suspend fun decorationsFor(query: AnnotationQuery): ReaderResult<List<Decoration>>
}


