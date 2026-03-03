package com.ireader.feature.reader.domain.usecase

import android.net.Uri
import android.os.ParcelFileDescriptor
import com.ireader.core.datastore.reader.ReaderSettingsStore
import com.ireader.core.files.source.DocumentSource
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.api.provider.SearchProvider
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
import com.ireader.reader.runtime.BookProbeResult
import com.ireader.reader.runtime.ReaderRuntime
import com.ireader.reader.runtime.ReaderSessionHandle
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenReaderSessionTest {

    @Test
    fun `runtime error should be returned`() = runTest {
        val runtime = FakeRuntime(
            openSessionResult = ReaderResult.Err(ReaderError.UnsupportedFormat()),
            capabilitiesForConfig = reflowCapabilities()
        )
        val store = FakeReaderSettingsStore()
        val useCase = OpenReaderSession(runtime = runtime, settings = store)

        val result = useCase(
            source = FakeDocumentSource(),
            options = OpenOptions(),
            initialLocator = null
        )

        assertTrue(result is ReaderResult.Err)
    }

    @Test
    fun `fixed layout should load fixed config from settings`() = runTest {
        val fixedConfig = RenderConfig.FixedPage(zoom = 2.0f)
        val store = FakeReaderSettingsStore(fixed = fixedConfig)
        val runtime = FakeRuntime(
            openSessionResult = ReaderResult.Ok(ReaderSessionHandle(FakeDocument(), FakeSession())),
            capabilitiesForConfig = fixedCapabilities()
        )
        val useCase = OpenReaderSession(runtime = runtime, settings = store)

        val result = useCase(
            source = FakeDocumentSource(),
            options = OpenOptions(),
            initialLocator = Locator("pdf.page", "1")
        )

        assertTrue(result is ReaderResult.Ok)
        assertEquals(1, store.fixedGetCount)
        assertEquals(0, store.reflowGetCount)
        assertEquals(fixedConfig, runtime.lastResolvedConfig)
    }

    @Test
    fun `reflow layout should load reflow config from settings`() = runTest {
        val reflowConfig = RenderConfig.ReflowText(fontSizeSp = 22f)
        val store = FakeReaderSettingsStore(reflow = reflowConfig)
        val runtime = FakeRuntime(
            openSessionResult = ReaderResult.Ok(ReaderSessionHandle(FakeDocument(), FakeSession())),
            capabilitiesForConfig = reflowCapabilities()
        )
        val useCase = OpenReaderSession(runtime = runtime, settings = store)

        val result = useCase(
            source = FakeDocumentSource(),
            options = OpenOptions(),
            initialLocator = null
        )

        assertTrue(result is ReaderResult.Ok)
        assertEquals(0, store.fixedGetCount)
        assertEquals(1, store.reflowGetCount)
        assertEquals(reflowConfig, runtime.lastResolvedConfig)
    }

    private fun fixedCapabilities() = DocumentCapabilities(
        reflowable = false,
        fixedLayout = true,
        outline = false,
        search = false,
        textExtraction = false,
        annotations = false,
        links = false
    )

    private fun reflowCapabilities() = fixedCapabilities().copy(
        reflowable = true,
        fixedLayout = false
    )
}

private class FakeReaderSettingsStore(
    private val reflow: RenderConfig.ReflowText = RenderConfig.ReflowText(),
    private val fixed: RenderConfig.FixedPage = RenderConfig.FixedPage()
) : ReaderSettingsStore {
    var reflowGetCount = 0
    var fixedGetCount = 0

    override val reflowConfig: Flow<RenderConfig.ReflowText> = flowOf(reflow)
    override val fixedConfig: Flow<RenderConfig.FixedPage> = flowOf(fixed)

    override suspend fun getReflowConfig(): RenderConfig.ReflowText {
        reflowGetCount++
        return reflow
    }

    override suspend fun getFixedConfig(): RenderConfig.FixedPage {
        fixedGetCount++
        return fixed
    }

    override suspend fun setReflowConfig(config: RenderConfig.ReflowText) = Unit

    override suspend fun setFixedConfig(config: RenderConfig.FixedPage) = Unit
}

private class FakeRuntime(
    private val openSessionResult: ReaderResult<ReaderSessionHandle>,
    private val capabilitiesForConfig: DocumentCapabilities
) : ReaderRuntime {
    var lastResolvedConfig: RenderConfig? = null

    override suspend fun openDocument(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<ReaderDocument> = ReaderResult.Err(ReaderError.Internal("unused"))

    override suspend fun openSession(
        source: DocumentSource,
        options: OpenOptions,
        initialLocator: Locator?,
        initialConfig: RenderConfig?,
        resolveInitialConfig: (suspend (DocumentCapabilities) -> RenderConfig)?
    ): ReaderResult<ReaderSessionHandle> {
        lastResolvedConfig = when {
            initialConfig != null -> initialConfig
            resolveInitialConfig != null -> resolveInitialConfig(capabilitiesForConfig)
            else -> null
        }
        return openSessionResult
    }

    override suspend fun probe(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<BookProbeResult> = ReaderResult.Err(ReaderError.Internal("unused"))
}

private class FakeDocument : ReaderDocument {
    override val id: DocumentId = DocumentId("doc")
    override val format: BookFormat = BookFormat.TXT
    override val capabilities: DocumentCapabilities = DocumentCapabilities(
        reflowable = true,
        fixedLayout = false,
        outline = false,
        search = false,
        textExtraction = false,
        annotations = false,
        links = false
    )
    override val openOptions: OpenOptions = OpenOptions()

    override suspend fun metadata(): ReaderResult<DocumentMetadata> = ReaderResult.Ok(DocumentMetadata())

    override suspend fun createSession(
        initialLocator: Locator?,
        initialConfig: RenderConfig
    ): ReaderResult<ReaderSession> = ReaderResult.Ok(FakeSession())

    override fun close() = Unit
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
    override fun close() = Unit
}

private class FakeController : ReaderController {
    private val stateValue = RenderState(
        locator = Locator(scheme = "txt.offset", value = "0"),
        progression = Progression(0.0),
        nav = NavigationAvailability(canGoPrev = false, canGoNext = false),
        config = RenderConfig.ReflowText()
    )
    override val state = MutableStateFlow(stateValue)
    override val events: Flow<ReaderEvent> = MutableSharedFlow()

    override suspend fun bindSurface(surface: RenderSurface): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun unbindSurface(): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> = ReaderResult.Err(ReaderError.Internal("unused"))

    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> = ReaderResult.Err(ReaderError.Internal("unused"))

    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> = ReaderResult.Err(ReaderError.Internal("unused"))

    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> =
        ReaderResult.Err(ReaderError.Internal("unused"))

    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> =
        ReaderResult.Err(ReaderError.Internal("unused"))

    override suspend fun prefetchNeighbors(count: Int): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> = ReaderResult.Ok(Unit)

    override fun close() = Unit
}

private class FakeDocumentSource : DocumentSource {
    override val uri: Uri
        get() = Uri.EMPTY
    override val displayName: String? = "book"
    override val mimeType: String? = "text/plain"
    override val sizeBytes: Long? = 1

    override suspend fun openInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override suspend fun openFileDescriptor(mode: String): ParcelFileDescriptor? = null
}
