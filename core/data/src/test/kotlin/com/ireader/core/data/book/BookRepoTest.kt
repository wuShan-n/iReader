package com.ireader.core.data.book

import androidx.sqlite.db.SupportSQLiteQuery
import com.ireader.core.database.book.BookDao
import com.ireader.core.database.book.BookEntity
import com.ireader.core.database.book.IndexState as DbIndexState
import com.ireader.core.database.book.LibraryBookRow
import com.ireader.core.database.book.ReadingStatus as DbReadingStatus
import com.ireader.core.database.collection.BookCollectionDao
import com.ireader.core.database.collection.BookCollectionEntity
import com.ireader.core.database.collection.CollectionDao
import com.ireader.core.database.collection.CollectionEntity
import com.ireader.reader.model.BookFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookRepoTest {

    @Test
    fun `createCollection should return existing id when normalized name already exists`() = runTest {
        val bookDao = FakeBookDao()
        val collectionDao = FakeCollectionDao().apply {
            byName["Sci-Fi"] = CollectionEntity(
                collectionId = 9L,
                name = "Sci-Fi",
                createdAtEpochMs = 1L,
                sortOrder = 0
            )
        }
        val repo = BookRepo(
            bookDao = bookDao,
            collectionDao = collectionDao,
            bookCollectionDao = FakeBookCollectionDao()
        )

        val id = repo.createCollection("  Sci-Fi  ")

        assertEquals(9L, id)
        assertTrue(collectionDao.upserted.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `createCollection should reject blank name`() = runTest {
        val repo = BookRepo(
            bookDao = FakeBookDao(),
            collectionDao = FakeCollectionDao(),
            bookCollectionDao = FakeBookCollectionDao()
        )

        repo.createCollection("   ")
    }

    @Test
    fun `createCollection should trim and persist normalized name`() = runTest {
        val collectionDao = FakeCollectionDao()
        val repo = BookRepo(
            bookDao = FakeBookDao(),
            collectionDao = collectionDao,
            bookCollectionDao = FakeBookCollectionDao()
        )

        val id = repo.createCollection("  New Collection  ")

        assertEquals(1L, id)
        assertEquals("New Collection", collectionDao.upserted.single().name)
    }

    @Test
    fun `observeLibrary should clamp progression into zero to one`() = runTest {
        val first = sampleBook(bookId = 1L, title = "A")
        val second = sampleBook(bookId = 2L, title = "B")
        val third = sampleBook(bookId = 3L, title = "C")
        val bookDao = FakeBookDao().apply {
            libraryRows.value = listOf(
                LibraryBookRow(book = first, progression = 1.5, progressUpdatedAtEpochMs = 1L),
                LibraryBookRow(book = second, progression = -0.2, progressUpdatedAtEpochMs = 2L),
                LibraryBookRow(book = third, progression = null, progressUpdatedAtEpochMs = null)
            )
        }
        val repo = BookRepo(
            bookDao = bookDao,
            collectionDao = FakeCollectionDao(),
            bookCollectionDao = FakeBookCollectionDao()
        )

        val items = repo.observeLibrary(LibraryQuery()).first()

        assertEquals(listOf(1.0, 0.0, 0.0), items.map { it.progression })
    }

    @Test
    fun `state updates should map enums and pass timestamps`() = runTest {
        val bookDao = FakeBookDao()
        val repo = BookRepo(
            bookDao = bookDao,
            collectionDao = FakeCollectionDao(),
            bookCollectionDao = FakeBookCollectionDao()
        )

        repo.setFavorite(bookId = 11L, favorite = true)
        repo.setReadingStatus(bookId = 11L, status = ReadingStatus.READING)
        repo.setIndexState(bookId = 11L, state = IndexState.ERROR, error = "boom")

        assertEquals(true, bookDao.updateFavoriteArgs?.favorite)
        assertEquals(DbReadingStatus.READING, bookDao.updateReadingStatusArgs?.status)
        assertEquals(DbIndexState.ERROR, bookDao.updateIndexStateArgs?.state)
        assertEquals("boom", bookDao.updateIndexStateArgs?.error)
        assertTrue((bookDao.updateFavoriteArgs?.updatedAt ?: 0L) > 0L)
        assertTrue((bookDao.updateReadingStatusArgs?.updatedAt ?: 0L) > 0L)
        assertTrue((bookDao.updateIndexStateArgs?.updatedAt ?: 0L) > 0L)
    }

    @Test
    fun `updateMetadata should map index state and pass all fields`() = runTest {
        val bookDao = FakeBookDao()
        val repo = BookRepo(
            bookDao = bookDao,
            collectionDao = FakeCollectionDao(),
            bookCollectionDao = FakeBookCollectionDao()
        )

        repo.updateMetadata(
            bookId = 7L,
            documentId = "doc-new",
            format = BookFormat.PDF,
            title = "Title",
            author = "Author",
            language = "zh",
            identifier = "id",
            series = "series",
            description = "desc",
            coverPath = "/tmp/cover.png",
            capabilitiesJson = """{"outline":true}""",
            indexState = IndexState.INDEXED,
            indexError = null
        )

        val args = bookDao.updateMetadataArgs
        assertEquals(7L, args?.bookId)
        assertEquals("doc-new", args?.documentId)
        assertEquals(BookFormat.PDF, args?.format)
        assertEquals("Title", args?.title)
        assertEquals("Author", args?.author)
        assertEquals("zh", args?.language)
        assertEquals("id", args?.identifier)
        assertEquals("series", args?.series)
        assertEquals("desc", args?.description)
        assertEquals("/tmp/cover.png", args?.coverPath)
        assertEquals("""{"outline":true}""", args?.capabilitiesJson)
        assertEquals(DbIndexState.INDEXED, args?.indexState)
        assertEquals(null, args?.indexError)
        assertTrue((args?.updatedAt ?: 0L) > 0L)
    }

    @Test
    fun `updateBookSource should pass source fields and timestamp`() = runTest {
        val bookDao = FakeBookDao()
        val repo = BookRepo(
            bookDao = bookDao,
            collectionDao = FakeCollectionDao(),
            bookCollectionDao = FakeBookCollectionDao()
        )

        repo.updateBookSource(
            bookId = 5L,
            sourceUri = "content://book/5",
            canonicalPath = "/tmp/book-5.pdf",
            lastModifiedEpochMs = 123L
        )

        val args = bookDao.updateSourceArgs
        assertEquals(5L, args?.bookId)
        assertEquals("content://book/5", args?.sourceUri)
        assertEquals("/tmp/book-5.pdf", args?.canonicalPath)
        assertEquals(123L, args?.lastModifiedEpochMs)
        assertTrue((args?.updatedAt ?: 0L) > 0L)
    }
}

private class FakeBookDao : BookDao {
    private val records: MutableMap<Long, BookEntity> = mutableMapOf()
    private var nextId: Long = 1L
    val libraryRows: MutableStateFlow<List<LibraryBookRow>> = MutableStateFlow(emptyList())
    var updateFavoriteArgs: UpdateFavoriteArgs? = null
    var updateReadingStatusArgs: UpdateReadingStatusArgs? = null
    var updateIndexStateArgs: UpdateIndexStateArgs? = null
    var updateSourceArgs: UpdateSourceArgs? = null
    var updateMetadataArgs: UpdateMetadataArgs? = null
    var updateLastOpenedArgs: UpdateLastOpenedArgs? = null

    override suspend fun upsert(entity: BookEntity): Long {
        val id = if (entity.bookId > 0L) entity.bookId else nextId++
        records[id] = entity.copy(bookId = id)
        return id
    }

    override suspend fun findByFingerprint(fingerprint: String): BookEntity? {
        return records.values
            .filter { it.fingerprintSha256 == fingerprint }
            .maxByOrNull { it.updatedAtEpochMs }
    }

    override suspend fun getById(bookId: Long): BookEntity? = records[bookId]

    override suspend fun listAll(): List<BookEntity> = records.values.toList()

    override suspend fun getByDocumentId(documentId: String): BookEntity? {
        return records.values.firstOrNull { it.documentId == documentId }
    }

    override suspend fun deleteById(bookId: Long) {
        records.remove(bookId)
    }

    override fun observeById(bookId: Long): Flow<BookEntity?> = flowOf(records[bookId])

    override fun observeMissing(): Flow<List<BookEntity>> = flowOf(emptyList())

    override fun observeLibrary(query: SupportSQLiteQuery): Flow<List<LibraryBookRow>> = libraryRows

    override suspend fun updateIndexState(bookId: Long, state: DbIndexState, error: String?, updatedAt: Long) {
        updateIndexStateArgs = UpdateIndexStateArgs(bookId, state, error, updatedAt)
    }

    override suspend fun updateLastOpened(bookId: Long, lastOpenedAt: Long, updatedAt: Long) {
        updateLastOpenedArgs = UpdateLastOpenedArgs(bookId, lastOpenedAt, updatedAt)
    }

    override suspend fun updateFavorite(bookId: Long, favorite: Boolean, updatedAt: Long) {
        updateFavoriteArgs = UpdateFavoriteArgs(bookId, favorite, updatedAt)
    }

    override suspend fun updateReadingStatus(bookId: Long, status: DbReadingStatus, updatedAt: Long) {
        updateReadingStatusArgs = UpdateReadingStatusArgs(bookId, status, updatedAt)
    }

    override suspend fun updateSource(
        bookId: Long,
        sourceUri: String?,
        canonicalPath: String,
        lastModifiedEpochMs: Long?,
        updatedAt: Long
    ) {
        updateSourceArgs = UpdateSourceArgs(bookId, sourceUri, canonicalPath, lastModifiedEpochMs, updatedAt)
    }

    override suspend fun updateMetadata(
        bookId: Long,
        documentId: String?,
        format: BookFormat,
        title: String?,
        author: String?,
        language: String?,
        identifier: String?,
        series: String?,
        description: String?,
        coverPath: String?,
        capabilitiesJson: String?,
        indexState: DbIndexState,
        indexError: String?,
        updatedAt: Long
    ) {
        updateMetadataArgs = UpdateMetadataArgs(
            bookId = bookId,
            documentId = documentId,
            format = format,
            title = title,
            author = author,
            language = language,
            identifier = identifier,
            series = series,
            description = description,
            coverPath = coverPath,
            capabilitiesJson = capabilitiesJson,
            indexState = indexState,
            indexError = indexError,
            updatedAt = updatedAt
        )
    }
}

private class FakeCollectionDao : CollectionDao {
    private var nextId = 1L
    val byName: MutableMap<String, CollectionEntity> = mutableMapOf()
    val upserted: MutableList<CollectionEntity> = mutableListOf()
    private val rows: MutableStateFlow<List<CollectionEntity>> = MutableStateFlow(emptyList())

    override suspend fun upsert(entity: CollectionEntity): Long {
        upserted += entity
        val id = if (entity.collectionId > 0L) entity.collectionId else nextId++
        val resolved = entity.copy(collectionId = id)
        byName[resolved.name] = resolved
        rows.value = byName.values.toList()
        return id
    }

    override suspend fun getById(collectionId: Long): CollectionEntity? {
        return byName.values.firstOrNull { it.collectionId == collectionId }
    }

    override suspend fun getByName(name: String): CollectionEntity? = byName[name]

    override fun observeAll(): Flow<List<CollectionEntity>> = rows

    override suspend fun deleteById(collectionId: Long) {
        val existing = byName.values.firstOrNull { it.collectionId == collectionId } ?: return
        byName.remove(existing.name)
        rows.value = byName.values.toList()
    }
}

private class FakeBookCollectionDao : BookCollectionDao {
    private val rows: MutableMap<Long, MutableList<Long>> = mutableMapOf()

    override suspend fun insert(entity: BookCollectionEntity): Long {
        val values = rows.getOrPut(entity.bookId) { mutableListOf() }
        if (entity.collectionId in values) {
            return -1L
        }
        values += entity.collectionId
        return 1L
    }

    override suspend fun delete(bookId: Long, collectionId: Long) {
        rows[bookId]?.remove(collectionId)
    }

    override suspend fun deleteAllForBook(bookId: Long) {
        rows.remove(bookId)
    }

    override suspend fun listCollectionIdsForBook(bookId: Long): List<Long> {
        return rows[bookId]?.toList() ?: emptyList()
    }
}

private data class UpdateFavoriteArgs(val bookId: Long, val favorite: Boolean, val updatedAt: Long)
private data class UpdateReadingStatusArgs(val bookId: Long, val status: DbReadingStatus, val updatedAt: Long)
private data class UpdateIndexStateArgs(val bookId: Long, val state: DbIndexState, val error: String?, val updatedAt: Long)
private data class UpdateLastOpenedArgs(val bookId: Long, val lastOpenedAt: Long, val updatedAt: Long)
private data class UpdateSourceArgs(
    val bookId: Long,
    val sourceUri: String?,
    val canonicalPath: String,
    val lastModifiedEpochMs: Long?,
    val updatedAt: Long
)
private data class UpdateMetadataArgs(
    val bookId: Long,
    val documentId: String?,
    val format: BookFormat,
    val title: String?,
    val author: String?,
    val language: String?,
    val identifier: String?,
    val series: String?,
    val description: String?,
    val coverPath: String?,
    val capabilitiesJson: String?,
    val indexState: DbIndexState,
    val indexError: String?,
    val updatedAt: Long
)

private fun sampleBook(bookId: Long, title: String): BookEntity {
    return BookEntity(
        bookId = bookId,
        documentId = "doc-$bookId",
        sourceUri = null,
        sourceType = com.ireader.core.database.book.BookSourceType.IMPORTED_COPY,
        format = BookFormat.EPUB,
        fileName = "book-$bookId.epub",
        mimeType = "application/epub+zip",
        fileSizeBytes = 100L,
        lastModifiedEpochMs = null,
        canonicalPath = "/tmp/book-$bookId.epub",
        fingerprintSha256 = "fp-$bookId",
        title = title,
        author = "author-$bookId",
        language = "en",
        identifier = null,
        series = null,
        description = null,
        coverPath = null,
        favorite = false,
        readingStatus = DbReadingStatus.UNREAD,
        indexState = DbIndexState.PENDING,
        indexError = null,
        capabilitiesJson = null,
        addedAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        lastOpenedAtEpochMs = null
    )
}
