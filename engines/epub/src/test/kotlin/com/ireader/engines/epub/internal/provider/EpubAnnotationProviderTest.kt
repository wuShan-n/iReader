package com.ireader.engines.epub.internal.provider

import com.ireader.engines.epub.internal.locator.ReadiumLocatorSchemes
import com.ireader.engines.epub.internal.render.EpubDecorationsHost
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.AnnotationQuery
import com.ireader.reader.api.provider.AnnotationStore
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.annotation.Annotation
import com.ireader.reader.model.annotation.AnnotationAnchor
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationId
import com.ireader.reader.model.annotation.AnnotationStyle
import com.ireader.reader.model.annotation.AnnotationType
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.shared.publication.Locator as ReadiumLocator

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EpubAnnotationProviderTest {

    @Test
    fun `observe updates should apply only changed groups`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = EpubDecorationsHost(mainDispatcher = dispatcher)
        val calls = mutableListOf<Pair<String, Int>>()
        host.bind(recordingNavigator(calls))
        advanceUntilIdle()

        val store = FakeAnnotationStore()
        val provider = EpubAnnotationProvider(
            documentId = DocumentId("epub:test"),
            store = store,
            decorationsHost = host,
            scope = this
        )

        val a1 = reflowAnnotation("a1", "frag-1")
        val a2 = reflowAnnotation("a2", "frag-2")

        store.emit(listOf(a1, a2))
        advanceUntilIdle()
        assertEquals(
            listOf("user.annotation.a1" to 1, "user.annotation.a2" to 1),
            calls
        )

        calls.clear()
        store.emit(listOf(a2))
        advanceUntilIdle()
        assertEquals(listOf("user.annotation.a1" to 0), calls)

        provider.closeInternal()
    }

    private fun recordingNavigator(calls: MutableList<Pair<String, Int>>): DecorableNavigator {
        val loader = DecorableNavigator::class.java.classLoader
        val interfaces = arrayOf(DecorableNavigator::class.java)
        return Proxy.newProxyInstance(loader, interfaces) { _, method, args ->
            if (method.name == "applyDecorations") {
                @Suppress("UNCHECKED_CAST")
                val decorations = args?.get(0) as? List<Decoration> ?: emptyList()
                val group = args?.get(1) as? String ?: ""
                calls += group to decorations.size
                Unit
            } else {
                defaultValue(method.returnType)
            }
        } as DecorableNavigator
    }

    private fun reflowAnnotation(id: String, fragment: String): Annotation {
        val range = LocatorRange(
            start = readiumLocator("https://example.com/chapter.xhtml", fragment),
            end = readiumLocator("https://example.com/chapter.xhtml", "${fragment}-end")
        )
        return Annotation(
            id = AnnotationId(id),
            type = AnnotationType.HIGHLIGHT,
            anchor = AnnotationAnchor.ReflowRange(range),
            style = AnnotationStyle(colorArgb = 0x80FFCC00.toInt(), opacity = 1f),
            createdAtEpochMs = 1L
        )
    }

    private fun readiumLocator(href: String, fragment: String): Locator {
        val readium = ReadiumLocator(
            href = checkNotNull(Url(href)),
            mediaType = checkNotNull(MediaType("text/html")),
            locations = ReadiumLocator.Locations(
                fragments = listOf(fragment),
                position = 1,
                progression = 0.1,
                totalProgression = 0.1
            ),
            text = ReadiumLocator.Text(highlight = "sample")
        )
        return Locator(
            scheme = ReadiumLocatorSchemes.READIUM_LOCATOR_JSON,
            value = readium.toJSON().toString()
        )
    }

    private fun defaultValue(returnType: Class<*>): Any? {
        return when (returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> '\u0000'
            java.lang.Void.TYPE -> Unit
            else -> null
        }
    }
}

private class FakeAnnotationStore : AnnotationStore {
    private val state = MutableStateFlow<List<Annotation>>(emptyList())

    fun emit(annotations: List<Annotation>) {
        state.value = annotations
    }

    override fun observe(documentId: DocumentId): Flow<List<Annotation>> = state.asStateFlow()

    override suspend fun list(documentId: DocumentId): ReaderResult<List<Annotation>> =
        ReaderResult.Ok(state.value)

    override suspend fun query(documentId: DocumentId, query: AnnotationQuery): ReaderResult<List<Annotation>> =
        ReaderResult.Ok(state.value)

    override suspend fun create(documentId: DocumentId, draft: AnnotationDraft): ReaderResult<Annotation> {
        val now = System.currentTimeMillis()
        val created = Annotation(
            id = AnnotationId("created-${now}"),
            type = draft.type,
            anchor = draft.anchor,
            content = draft.content,
            style = draft.style,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            extra = draft.extra
        )
        state.value = state.value + created
        return ReaderResult.Ok(created)
    }

    override suspend fun update(documentId: DocumentId, annotation: Annotation): ReaderResult<Unit> =
        ReaderResult.Ok(Unit)

    override suspend fun delete(documentId: DocumentId, id: AnnotationId): ReaderResult<Unit> =
        ReaderResult.Ok(Unit)
}
