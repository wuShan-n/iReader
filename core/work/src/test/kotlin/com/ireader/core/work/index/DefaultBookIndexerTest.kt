package com.ireader.core.work.index

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.room.Room
import com.ireader.core.data.book.BookRepo
import com.ireader.core.database.ReaderDatabase
import com.ireader.core.database.book.BookEntity
import com.ireader.core.database.book.BookSourceType
import com.ireader.core.database.book.IndexState
import com.ireader.core.database.book.ReadingStatus
import com.ireader.core.files.source.DocumentSource
import com.ireader.core.files.storage.BookStorage
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
import com.ireader.reader.api.render.PageId
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderContent
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DefaultBookIndexerTest {

    private lateinit var context: Context
    private lateinit var database: ReaderDatabase
    private lateinit var bookRepo: BookRepo
    private lateinit var storage: BookStorage
    private lateinit var runtime: FakeReaderRuntime
    private lateinit var indexer: DefaultBookIndexer
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, ReaderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bookRepo = BookRepo(
            bookDao = database.bookDao(),
            collectionDao = database.collectionDao(),
            bookCollectionDao = database.bookCollectionDao()
        )
        storage = BookStorage(context)
        runtime = FakeReaderRuntime()
        indexer = DefaultBookIndexer(
            bookRepo = bookRepo,
            storage = storage,
            runtime = runtime
        )
        tempDir = File(context.cacheDir, "indexer-test-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        runCatching { database.close() }
        runCatching { tempDir.deleteRecursively() }
    }

    @Test
    fun `index should extract epub embedded cover automatically`() = runTest {
        val epubFile = File(tempDir, "book.epub")
        createEpubWithCover(epubFile, coverColor = Color.RED)
        val inserted = insertBook(
            format = BookFormat.EPUB,
            file = epubFile,
            fileName = "book.epub",
            mimeType = "application/epub+zip"
        )
        runtime.probeResult = ReaderResult.Ok(
            BookProbeResult(
                format = BookFormat.EPUB,
                documentId = "doc-epub",
                metadata = DocumentMetadata(title = "Book"),
                coverBytes = null,
                capabilities = null
            )
        )

        val result = indexer.index(inserted.bookId)

        assertTrue(result.isSuccess)
        val updated = requireNotNull(bookRepo.getById(inserted.bookId))
        val coverPath = requireNotNull(updated.coverPath)
        assertTrue(File(coverPath).exists())
        val bitmap = BitmapFactory.decodeFile(coverPath)
        assertNotNull(bitmap)
        assertEquals(Color.RED, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
    }

    @Test
    fun `index should render pdf first page as cover`() = runTest {
        val pdfFile = File(tempDir, "book.pdf").apply { writeText("pdf") }
        val inserted = insertBook(
            format = BookFormat.PDF,
            file = pdfFile,
            fileName = "book.pdf",
            mimeType = "application/pdf"
        )
        runtime.probeResult = ReaderResult.Ok(
            BookProbeResult(
                format = BookFormat.PDF,
                documentId = "doc-pdf",
                metadata = DocumentMetadata(title = "PDF Book"),
                coverBytes = null,
                capabilities = null
            )
        )
        runtime.openSessionResult = ReaderResult.Ok(fakeSessionHandle(bitmapColor = Color.BLUE))

        val result = indexer.index(inserted.bookId)

        assertTrue(result.isSuccess)
        assertEquals(1, runtime.openSessionCalls)
        val updated = requireNotNull(bookRepo.getById(inserted.bookId))
        val bitmap = BitmapFactory.decodeFile(updated.coverPath)
        assertNotNull(bitmap)
        assertEquals(Color.BLUE, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
    }

    @Test
    fun `index should fallback to placeholder when pdf session cannot open`() = runTest {
        val pdfFile = File(tempDir, "broken.pdf").apply { writeText("broken") }
        val inserted = insertBook(
            format = BookFormat.PDF,
            file = pdfFile,
            fileName = "broken.pdf",
            mimeType = "application/pdf"
        )
        runtime.probeResult = ReaderResult.Ok(
            BookProbeResult(
                format = BookFormat.PDF,
                documentId = "doc-pdf",
                metadata = DocumentMetadata(title = "Broken"),
                coverBytes = null,
                capabilities = null
            )
        )
        runtime.openSessionResult = ReaderResult.Err(ReaderError.Internal("open failed"))

        val result = indexer.index(inserted.bookId)

        assertTrue(result.isSuccess)
        assertEquals(1, runtime.openSessionCalls)
        val updated = requireNotNull(bookRepo.getById(inserted.bookId))
        val bitmap = BitmapFactory.decodeFile(updated.coverPath)
        assertNotNull(bitmap)
        assertTrue(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) != Color.BLUE)
    }

    @Test
    fun `index should keep existing cover when not forced`() = runTest {
        val pdfFile = File(tempDir, "keep.pdf").apply { writeText("keep") }
        val existingCover = File(tempDir, "existing.png")
        writeSolidBitmap(existingCover, Color.GREEN, 80, 120)
        val inserted = insertBook(
            format = BookFormat.PDF,
            file = pdfFile,
            fileName = "keep.pdf",
            mimeType = "application/pdf",
            coverPath = existingCover.absolutePath
        )
        runtime.probeResult = ReaderResult.Ok(
            BookProbeResult(
                format = BookFormat.PDF,
                documentId = "doc-pdf",
                metadata = DocumentMetadata(title = "Keep"),
                coverBytes = null,
                capabilities = null
            )
        )
        runtime.openSessionResult = ReaderResult.Err(ReaderError.Internal("should not open"))

        val result = indexer.index(inserted.bookId)

        assertTrue(result.isSuccess)
        assertEquals(0, runtime.openSessionCalls)
        val updated = requireNotNull(bookRepo.getById(inserted.bookId))
        assertEquals(existingCover.absolutePath, updated.coverPath)
    }

    private suspend fun insertBook(
        format: BookFormat,
        file: File,
        fileName: String,
        mimeType: String?,
        coverPath: String? = null
    ): BookEntity {
        val now = System.currentTimeMillis()
        val bookId = bookRepo.upsert(
            BookEntity(
                documentId = null,
                sourceUri = "content://book/${UUID.randomUUID()}",
                sourceType = BookSourceType.IMPORTED_COPY,
                format = format,
                fileName = fileName,
                mimeType = mimeType,
                fileSizeBytes = file.length().coerceAtLeast(1L),
                lastModifiedEpochMs = file.lastModified(),
                canonicalPath = file.absolutePath,
                fingerprintSha256 = UUID.randomUUID().toString(),
                title = fileName.substringBeforeLast('.'),
                author = null,
                language = null,
                identifier = null,
                series = null,
                description = null,
                coverPath = coverPath,
                favorite = false,
                readingStatus = ReadingStatus.UNREAD,
                indexState = IndexState.PENDING,
                indexError = null,
                capabilitiesJson = null,
                addedAtEpochMs = now,
                updatedAtEpochMs = now,
                lastOpenedAtEpochMs = null
            )
        )
        return requireNotNull(bookRepo.getById(bookId))
    }

    private fun createEpubWithCover(epubFile: File, coverColor: Int) {
        val opf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Sample</dc:title>
              </metadata>
              <manifest>
                <item id="cover-item" href="images/cover.png" media-type="image/png" properties="cover-image"/>
                <item id="chapter-1" href="chapter.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine>
                <itemref idref="chapter-1"/>
              </spine>
            </package>
        """.trimIndent()

        ZipOutputStream(FileOutputStream(epubFile)).use { zip ->
            writeEntry(
                zip = zip,
                path = "META-INF/container.xml",
                text = """
                    <?xml version="1.0"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                """.trimIndent()
            )
            writeEntry(zip = zip, path = "OPS/package.opf", text = opf)
            writeEntry(zip = zip, path = "OPS/images/cover.png", bytes = bitmapBytes(coverColor))
            writeEntry(
                zip = zip,
                path = "OPS/chapter.xhtml",
                text = """<html xmlns="http://www.w3.org/1999/xhtml"><body><p>chapter</p></body></html>"""
            )
        }
    }

    private fun writeEntry(zip: ZipOutputStream, path: String, text: String) {
        writeEntry(zip = zip, path = path, bytes = text.toByteArray(Charsets.UTF_8))
    }

    private fun writeEntry(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun bitmapBytes(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
    }

    private fun writeSolidBitmap(file: File, color: Int, width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.flush()
        }
    }

    private fun fakeSessionHandle(bitmapColor: Int): ReaderSessionHandle {
        val bitmap = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888).apply { eraseColor(bitmapColor) }
        val page = RenderPage(
            id = PageId("page"),
            locator = Locator(scheme = "pdf.page", value = "0"),
            content = RenderContent.BitmapPage(bitmap)
        )
        val controller = FakeReaderController(page)
        val session = FakeReaderSession(controller)
        return ReaderSessionHandle(
            document = FakeReaderDocument(),
            session = session
        )
    }
}

private class FakeReaderRuntime : ReaderRuntime {
    var probeResult: ReaderResult<BookProbeResult> = ReaderResult.Err(ReaderError.Internal("probe not set"))
    var openSessionResult: ReaderResult<ReaderSessionHandle> = ReaderResult.Err(
        ReaderError.Internal("session not set")
    )
    var openSessionCalls: Int = 0

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
        openSessionCalls += 1
        return openSessionResult
    }

    override suspend fun probe(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<BookProbeResult> = probeResult
}

private class FakeReaderDocument : ReaderDocument {
    override val id: DocumentId = DocumentId("doc")
    override val format: BookFormat = BookFormat.PDF
    override val capabilities: DocumentCapabilities = DocumentCapabilities(
        reflowable = false,
        fixedLayout = true,
        outline = false,
        search = false,
        textExtraction = false,
        annotations = false,
        selection = false,
        links = false
    )
    override val openOptions: OpenOptions = OpenOptions()

    override suspend fun metadata(): ReaderResult<DocumentMetadata> = ReaderResult.Ok(DocumentMetadata())

    override suspend fun createSession(
        initialLocator: Locator?,
        initialConfig: RenderConfig
    ): ReaderResult<ReaderSession> = ReaderResult.Err(ReaderError.Internal("unused"))

    override fun close() = Unit
}

private class FakeReaderSession(
    override val controller: ReaderController
) : ReaderSession {
    override val id: SessionId = SessionId("session")
    override val outline: OutlineProvider? = null
    override val search: SearchProvider? = null
    override val text: TextProvider? = null
    override val annotations: AnnotationProvider? = null
    override val resources: ResourceProvider? = null
    override val selection: SelectionProvider? = null
    override fun close() = Unit
}

private class FakeReaderController(
    page: RenderPage
) : ReaderController {
    private val renderResult = ReaderResult.Ok(page)
    override val state = MutableStateFlow(
        RenderState(
            locator = Locator("pdf.page", "0"),
            progression = Progression(0.0),
            nav = NavigationAvailability(canGoPrev = false, canGoNext = false),
            config = RenderConfig.FixedPage()
        )
    )
    override val events: Flow<ReaderEvent> = MutableSharedFlow()

    override suspend fun bindSurface(surface: RenderSurface): ReaderResult<Unit> = ReaderResult.Ok(Unit)
    override suspend fun unbindSurface(): ReaderResult<Unit> = ReaderResult.Ok(Unit)
    override suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit> = ReaderResult.Ok(Unit)
    override suspend fun setConfig(config: RenderConfig): ReaderResult<Unit> = ReaderResult.Ok(Unit)
    override suspend fun render(policy: RenderPolicy): ReaderResult<RenderPage> = renderResult
    override suspend fun next(policy: RenderPolicy): ReaderResult<RenderPage> = renderResult
    override suspend fun prev(policy: RenderPolicy): ReaderResult<RenderPage> = renderResult
    override suspend fun goTo(locator: Locator, policy: RenderPolicy): ReaderResult<RenderPage> = renderResult
    override suspend fun goToProgress(percent: Double, policy: RenderPolicy): ReaderResult<RenderPage> = renderResult
    override suspend fun prefetchNeighbors(count: Int): ReaderResult<Unit> = ReaderResult.Ok(Unit)
    override suspend fun invalidate(reason: InvalidateReason): ReaderResult<Unit> = ReaderResult.Ok(Unit)
    override fun close() = Unit
}
