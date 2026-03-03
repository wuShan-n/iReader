package com.ireader.feature.reader.presentation

import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.book.BookRecord
import com.ireader.core.data.book.LocatorCodec
import com.ireader.core.data.book.ProgressRepo
import com.ireader.core.database.book.BookDao
import com.ireader.core.database.book.BookEntity
import com.ireader.core.database.book.LibraryBookRow
import com.ireader.core.database.collection.BookCollectionDao
import com.ireader.core.database.collection.CollectionDao
import com.ireader.core.database.collection.CollectionEntity
import com.ireader.core.database.progress.ProgressDao
import com.ireader.core.database.progress.ProgressEntity
import com.ireader.core.datastore.reader.ReaderSettingsStore
import com.ireader.core.files.source.BookSourceResolver
import com.ireader.core.files.source.DocumentSource
import com.ireader.feature.reader.domain.usecase.ObserveEffectiveConfig
import com.ireader.feature.reader.domain.usecase.OpenReaderSession
import com.ireader.feature.reader.domain.usecase.SaveReadingProgress
import com.ireader.feature.reader.testing.MainDispatcherRule
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.model.Locator
import com.ireader.reader.runtime.BookProbeResult
import com.ireader.reader.runtime.ReaderRuntime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy

class ReaderViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `start should show book not found when repository returns null`() = runTest {
        val vm = newViewModel(bookById = emptyMap())

        vm.dispatch(ReaderIntent.Start(bookId = 42L, locatorArg = null))

        val state = vm.state.value
        val error = state.error
        assertFalse(state.isOpening)
        assertNotNull(error)
        assertEquals("Book not found", (error?.message as UiText.Dynamic).value)
    }

    @Test
    fun `update config with persist should write settings and update state`() = runTest {
        val settings = FakeReaderSettingsStore()
        val vm = newViewModel(
            bookById = emptyMap(),
            settingsStore = settings
        )
        val config = RenderConfig.ReflowText(fontSizeSp = 22f)

        vm.dispatch(ReaderIntent.UpdateConfig(config = config, persist = true))

        assertEquals(config, settings.lastReflow)
        assertEquals(config, vm.state.value.currentConfig)
    }

    private fun newViewModel(
        bookById: Map<Long, BookEntity>,
        settingsStore: FakeReaderSettingsStore = FakeReaderSettingsStore()
    ): ReaderViewModel {
        val bookRepo = BookRepo(
            bookDao = fakeBookDao(bookById),
            collectionDao = fakeCollectionDao(),
            bookCollectionDao = fakeBookCollectionDao()
        )
        val progressRepo = ProgressRepo(fakeProgressDao())
        val locatorCodec = object : LocatorCodec {
            override fun encode(locator: Locator): String = "${locator.scheme}:${locator.value}"
            override fun decode(raw: String): Locator? = null
        }
        val runtime = object : ReaderRuntime {
            override suspend fun openDocument(source: DocumentSource, options: OpenOptions): ReaderResult<ReaderDocument> {
                return ReaderResult.Err(ReaderError.NotFound())
            }

            override suspend fun openSession(
                source: DocumentSource,
                options: OpenOptions,
                initialLocator: Locator?,
                initialConfig: RenderConfig?,
                resolveInitialConfig: (suspend (DocumentCapabilities) -> RenderConfig)?
            ) = ReaderResult.Err(ReaderError.NotFound())

            override suspend fun probe(source: DocumentSource, options: OpenOptions): ReaderResult<BookProbeResult> {
                return ReaderResult.Err(ReaderError.NotFound())
            }
        }

        return ReaderViewModel(
            bookRepo = bookRepo,
            progressRepo = progressRepo,
            settingsStore = settingsStore,
            sourceResolver = object : BookSourceResolver {
                override fun resolve(book: BookRecord) = null
            },
            locatorCodec = locatorCodec,
            openReaderSession = OpenReaderSession(runtime = runtime, settings = settingsStore),
            observeEffectiveConfig = ObserveEffectiveConfig(settingsStore = settingsStore),
            saveReadingProgress = SaveReadingProgress(progressRepo = progressRepo, locatorCodec = locatorCodec),
            errorMapper = ReaderUiErrorMapper()
        )
    }

    private fun fakeBookDao(bookById: Map<Long, BookEntity>): BookDao {
        return proxyInterface(BookDao::class.java) { method, args ->
            when (method.name) {
                "getById" -> bookById[args?.get(0) as Long]
                "observeById" -> flowOf(bookById[args?.get(0) as Long])
                "observeMissing" -> flowOf(emptyList<BookEntity>())
                "observeLibrary" -> flowOf(emptyList<LibraryBookRow>())
                "listAll" -> bookById.values.toList()
                "findByFingerprint", "getByDocumentId" -> null
                "upsert" -> 1L
                "updateIndexState",
                "updateLastOpened",
                "updateFavorite",
                "updateReadingStatus",
                "updateSource",
                "updateMetadata",
                "deleteById" -> Unit
                else -> error("Unexpected BookDao call: ${method.name}")
            }
        }
    }

    private fun fakeCollectionDao(): CollectionDao {
        return proxyInterface(CollectionDao::class.java) { method, _ ->
            when (method.name) {
                "observeAll" -> flowOf(emptyList<CollectionEntity>())
                "upsert" -> 1L
                "getById", "getByName" -> null
                "deleteById" -> Unit
                else -> error("Unexpected CollectionDao call: ${method.name}")
            }
        }
    }

    private fun fakeBookCollectionDao(): BookCollectionDao {
        return proxyInterface(BookCollectionDao::class.java) { method, _ ->
            when (method.name) {
                "insert" -> 1L
                "delete", "deleteAllForBook" -> Unit
                "listCollectionIdsForBook" -> emptyList<Long>()
                else -> error("Unexpected BookCollectionDao call: ${method.name}")
            }
        }
    }

    private fun fakeProgressDao(): ProgressDao {
        return proxyInterface(ProgressDao::class.java) { method, _ ->
            when (method.name) {
                "upsert", "deleteByBookId" -> Unit
                "getByBookId" -> null
                "observeByBookId" -> flowOf(null)
                else -> error("Unexpected ProgressDao call: ${method.name}")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> proxyInterface(
        clazz: Class<T>,
        handler: (method: java.lang.reflect.Method, args: Array<Any?>?) -> Any?
    ): T {
        return Proxy.newProxyInstance(
            clazz.classLoader,
            arrayOf(clazz)
        ) { _, method, args ->
            handler(method, args)
        } as T
    }
}

private class FakeReaderSettingsStore : ReaderSettingsStore {
    var lastReflow: RenderConfig.ReflowText? = null
    var lastFixed: RenderConfig.FixedPage? = null

    override val reflowConfig: Flow<RenderConfig.ReflowText> = flowOf(RenderConfig.ReflowText())
    override val fixedConfig: Flow<RenderConfig.FixedPage> = flowOf(RenderConfig.FixedPage())

    override suspend fun getReflowConfig(): RenderConfig.ReflowText = RenderConfig.ReflowText()

    override suspend fun getFixedConfig(): RenderConfig.FixedPage = RenderConfig.FixedPage()

    override suspend fun setReflowConfig(config: RenderConfig.ReflowText) {
        lastReflow = config
    }

    override suspend fun setFixedConfig(config: RenderConfig.FixedPage) {
        lastFixed = config
    }
}
