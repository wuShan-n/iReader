package com.ireader.reader.runtime

import android.net.Uri
import android.os.ParcelFileDescriptor
import com.ireader.core.files.source.DocumentSource
import com.ireader.reader.api.engine.EngineRegistry
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.api.provider.SelectionController
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.api.render.InvalidateReason
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.NavigationAvailability
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.api.render.RenderState
import com.ireader.reader.api.render.RenderSurface
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.model.DocumentId
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.model.Locator
import com.ireader.reader.model.Progression
import com.ireader.reader.model.SessionId
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DefaultReaderRuntimeTest {

    @Test
    fun `openSession should use custom config resolver`() = runTest {
        val expectedConfig = RenderConfig.FixedPage(zoom = 3f)
        val document = FakeDocument(
            capabilities = DocumentCapabilities(
                reflowable = false,
                fixedLayout = true,
                outline = false,
                search = false,
                textExtraction = false,
                annotations = false,
                selection = false,
                links = false
            ),
            createSessionResult = ReaderResult.Ok(FakeSession())
        )
        val runtime = DefaultReaderRuntime(
            engineRegistry = FakeRegistry(FakeEngine(document = document))
        )

        val result = runtime.openSession(
            source = FakeSource(),
            options = OpenOptions(hintFormat = BookFormat.TXT),
            resolveInitialConfig = { expectedConfig }
        )

        assertTrue(result is ReaderResult.Ok)
        assertEquals(expectedConfig, document.lastConfig)
    }

    @Test
    fun `openSession should close document when session creation fails`() = runTest {
        val document = FakeDocument(
            capabilities = DocumentCapabilities(
                reflowable = true,
                fixedLayout = false,
                outline = false,
                search = false,
                textExtraction = false,
                annotations = false,
                selection = false,
                links = false
            ),
            createSessionResult = ReaderResult.Err(ReaderError.Internal())
        )
        val runtime = DefaultReaderRuntime(
            engineRegistry = FakeRegistry(FakeEngine(document = document))
        )

        val result = runtime.openSession(
            source = FakeSource(),
            options = OpenOptions(hintFormat = BookFormat.TXT)
        )

        assertTrue(result is ReaderResult.Err)
        assertTrue(document.closed)
    }
}

private class FakeRegistry(
    private val engine: ReaderEngine
) : EngineRegistry {
    override fun engineFor(format: BookFormat): ReaderEngine = engine
}

private class FakeEngine(
    private val document: ReaderDocument
) : ReaderEngine {
    override val supportedFormats: Set<BookFormat> = setOf(BookFormat.TXT)

    override suspend fun open(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<ReaderDocument> = ReaderResult.Ok(document)
}

private class FakeDocument(
    override val capabilities: DocumentCapabilities,
    private val createSessionResult: ReaderResult<ReaderSession>
) : ReaderDocument {
    var closed = false
    var lastConfig: RenderConfig? = null
    override val id: DocumentId = DocumentId("doc")
    override val format: BookFormat = BookFormat.TXT
    override val openOptions: OpenOptions = OpenOptions()

    override suspend fun metadata(): ReaderResult<DocumentMetadata> = ReaderResult.Ok(DocumentMetadata())

    override suspend fun createSession(
        initialLocator: Locator?,
        initialConfig: RenderConfig
    ): ReaderResult<ReaderSession> {
        lastConfig = initialConfig
        return createSessionResult
    }

    override fun close() {
        closed = true
    }
}

private class FakeSession : ReaderSession {
    override val id: SessionId = SessionId("session")
    override val controller: ReaderController = FakeController()
    override val outline: OutlineProvider? = null
    override val search: SearchProvider? = null
    override val text: TextProvider? = null
    override val annotations: AnnotationProvider? = null
    override val resources: ResourceProvider? = null
    override val selection: SelectionProvider? = null
    override val selectionController: SelectionController? = null
    override fun close() = Unit
}

private class FakeController : ReaderController {
    override val state = MutableStateFlow(
        RenderState(
            locator = Locator("txt.offset", "0"),
            progression = Progression(0.0),
            nav = NavigationAvailability(canGoPrev = false, canGoNext = false),
            config = RenderConfig.ReflowText()
        )
    )
    override val events: Flow<ReaderEvent> = MutableSharedFlow()

    override suspend fun bindSurface(surface: RenderSurface): ReaderResult<Unit> = ReaderResult.Ok(Unit)
    override suspend fun unbindSurface(): ReaderResult<Unit> = ReaderResult.Ok(Unit)
    override suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit> = ReaderResult.Ok(Unit)
    override suspend fun setTextLayouterFactory(
        factory: com.ireader.reader.api.render.TextLayouterFactory
    ): ReaderResult<Unit> = ReaderResult.Ok(Unit)
    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> = ReaderResult.Ok(Unit)
    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> = ReaderResult.Err(ReaderError.Internal("unused"))
    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> = ReaderResult.Err(ReaderError.Internal("unused"))
    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> = ReaderResult.Err(ReaderError.Internal("unused"))
    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> = ReaderResult.Err(ReaderError.Internal("unused"))
    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> = ReaderResult.Err(ReaderError.Internal("unused"))
    override suspend fun prefetchNeighbors(count: Int): ReaderResult<Unit> = ReaderResult.Ok(Unit)
    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> = ReaderResult.Ok(Unit)
    override fun close() = Unit
}

private class FakeSource : DocumentSource {
    override val uri: Uri = Uri.EMPTY
    override val displayName: String? = "book.txt"
    override val mimeType: String? = "text/plain"
    override val sizeBytes: Long? = 1

    override suspend fun openInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override suspend fun openFileDescriptor(mode: String): ParcelFileDescriptor? = null
}
