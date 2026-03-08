package com.ireader.feature.reader.domain.usecase

import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.book.LocatorCodec
import com.ireader.core.data.book.ProgressRepo
import com.ireader.core.data.reader.ReaderLaunchRepository
import com.ireader.core.data.reader.ReaderPreferencesRepository
import com.ireader.core.database.book.BookDao
import com.ireader.core.database.book.BookEntity
import com.ireader.core.database.book.LibraryBookRow
import com.ireader.core.database.collection.BookCollectionDao
import com.ireader.core.database.collection.CollectionDao
import com.ireader.core.database.collection.CollectionEntity
import com.ireader.core.database.progress.ProgressDao
import com.ireader.core.database.progress.ProgressEntity
import com.ireader.core.datastore.reader.ReaderDisplayPrefs
import com.ireader.core.datastore.reader.ReaderOpenSettingsSnapshot
import com.ireader.core.datastore.reader.ReaderSettingsStore
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.DocumentSource
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.Locator
import com.ireader.reader.runtime.BookProbeResult
import com.ireader.reader.runtime.ReaderHandle
import com.ireader.reader.runtime.ReaderRuntime
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ReaderLaunchRepositorySaveProgressTest {

    @Test
    fun `progress should be clamped and locator encoded`() = runTest {
        val progressDao = FakeProgressDao()
        val repository = ReaderLaunchRepository(
            bookRepo = BookRepo(
                bookDao = fakeBookDao(),
                collectionDao = fakeCollectionDao(),
                bookCollectionDao = fakeBookCollectionDao()
            ),
            progressRepo = ProgressRepo(progressDao),
            locatorCodec = object : LocatorCodec {
                override fun encode(locator: Locator): String = "encoded:${locator.value}"
                override fun decode(raw: String): Locator? = null
            },
            sourceResolver = object : com.ireader.core.data.book.BookSourceResolver {
                override fun resolve(book: com.ireader.core.data.book.BookRecord): DocumentSource? = null
            },
            preferencesRepository = ReaderPreferencesRepository(NoopReaderSettingsStore()),
            runtime = NoopReaderRuntime()
        )

        repository.saveProgress(
            bookId = 7L,
            locator = Locator("txt.stable.anchor", "42:0"),
            progression = 2.0
        )

        val saved = progressDao.lastUpsert
        assertNotNull(saved)
        assertEquals(7L, saved?.bookId)
        assertEquals("encoded:42:0", saved?.locatorJson)
        assertEquals(1.0, saved?.progression ?: -1.0, 0.0)
    }

    private fun fakeBookDao(): BookDao {
        return proxyInterface(BookDao::class.java) { method, _ ->
            when (method.name) {
                "getById", "getByDocumentId", "findByFingerprint" -> null
                "observeById" -> flowOf(null)
                "observeMissing" -> flowOf(emptyList<BookEntity>())
                "observeLibrary" -> flowOf(emptyList<LibraryBookRow>())
                "listAll" -> emptyList<BookEntity>()
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

    @Suppress("UNCHECKED_CAST")
    private fun <T> proxyInterface(
        clazz: Class<T>,
        handler: (method: java.lang.reflect.Method, args: Array<Any?>?) -> Any?
    ): T {
        return Proxy.newProxyInstance(
            clazz.classLoader,
            arrayOf(clazz)
        ) { _, method, args -> handler(method, args) } as T
    }
}

private class FakeProgressDao : ProgressDao {
    var lastUpsert: ProgressEntity? = null

    override suspend fun upsert(entity: ProgressEntity) {
        lastUpsert = entity
    }

    override suspend fun getByBookId(bookId: Long): ProgressEntity? = null

    override fun observeByBookId(bookId: Long): Flow<ProgressEntity?> = flowOf(null)

    override suspend fun deleteByBookId(bookId: Long) = Unit
}

private class NoopReaderSettingsStore : ReaderSettingsStore {
    override val reflowConfig: Flow<com.ireader.reader.api.render.RenderConfig.ReflowText> =
        flowOf(com.ireader.reader.api.render.RenderConfig.ReflowText())
    override val fixedConfig: Flow<com.ireader.reader.api.render.RenderConfig.FixedPage> =
        flowOf(com.ireader.reader.api.render.RenderConfig.FixedPage())
    override val displayPrefs: Flow<ReaderDisplayPrefs> = flowOf(ReaderDisplayPrefs())

    override suspend fun getReflowConfig() = com.ireader.reader.api.render.RenderConfig.ReflowText()

    override suspend fun getFixedConfig() = com.ireader.reader.api.render.RenderConfig.FixedPage()

    override suspend fun getDisplayPrefs() = ReaderDisplayPrefs()

    override suspend fun getOpenSettingsSnapshot(): ReaderOpenSettingsSnapshot {
        return ReaderOpenSettingsSnapshot(
            reflowConfig = com.ireader.reader.api.render.RenderConfig.ReflowText(),
            fixedConfig = com.ireader.reader.api.render.RenderConfig.FixedPage(),
            displayPrefs = ReaderDisplayPrefs()
        )
    }

    override suspend fun setReflowConfig(config: com.ireader.reader.api.render.RenderConfig.ReflowText) = Unit

    override suspend fun setFixedConfig(config: com.ireader.reader.api.render.RenderConfig.FixedPage) = Unit

    override suspend fun setDisplayPrefs(prefs: ReaderDisplayPrefs) = Unit
}

private class NoopReaderRuntime : ReaderRuntime {
    override suspend fun openDocument(source: DocumentSource, options: OpenOptions) =
        ReaderResult.Err(ReaderError.Internal("unused"))

    override suspend fun openSession(
        source: DocumentSource,
        options: OpenOptions,
        initialLocator: Locator?,
        initialConfig: com.ireader.reader.api.render.RenderConfig?,
        resolveInitialConfig: (suspend (com.ireader.reader.api.engine.DocumentCapabilities) -> com.ireader.reader.api.render.RenderConfig)?
    ): ReaderResult<ReaderHandle> = ReaderResult.Err(ReaderError.Internal("unused"))

    override suspend fun probe(source: DocumentSource, options: OpenOptions): ReaderResult<BookProbeResult> =
        ReaderResult.Err(ReaderError.Internal("unused"))
}
