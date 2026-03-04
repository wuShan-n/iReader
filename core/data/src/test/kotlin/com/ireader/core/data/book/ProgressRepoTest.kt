package com.ireader.core.data.book

import com.ireader.core.database.progress.ProgressDao
import com.ireader.core.database.progress.ProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressRepoTest {

    @Test
    fun `getByBookId should map dao entity to record`() = runTest {
        val dao = FakeProgressDao().apply {
            byBookId[7L] = ProgressEntity(
                bookId = 7L,
                locatorJson = """{"scheme":"txt.block","value":"1:1"}""",
                progression = 0.25,
                updatedAtEpochMs = 123L
            )
        }
        val repo = ProgressRepo(dao)

        val record = repo.getByBookId(7L)

        assertEquals(7L, record?.bookId)
        assertEquals(0.25, record?.progression ?: 0.0, 0.0001)
        assertEquals(123L, record?.updatedAtEpochMs)
    }

    @Test
    fun `observeByBookId should emit mapped nullable record`() = runTest {
        val dao = FakeProgressDao()
        val repo = ProgressRepo(dao)
        dao.emit(
            ProgressEntity(
                bookId = 11L,
                locatorJson = "loc",
                progression = 0.5,
                updatedAtEpochMs = 9L
            )
        )

        val record = repo.observeByBookId(11L).first()

        assertEquals(11L, record?.bookId)
        assertEquals("loc", record?.locatorJson)
    }

    @Test
    fun `upsert should clamp progression to zero to one`() = runTest {
        val dao = FakeProgressDao()
        val repo = ProgressRepo(dao)

        repo.upsert(bookId = 1L, locatorJson = "a", progression = 1.7, updatedAtEpochMs = 100L)
        repo.upsert(bookId = 2L, locatorJson = "b", progression = -0.3, updatedAtEpochMs = 101L)

        assertEquals(1.0, dao.byBookId[1L]?.progression ?: 0.0, 0.0001)
        assertEquals(0.0, dao.byBookId[2L]?.progression ?: 1.0, 0.0001)
    }

    @Test
    fun `deleteByBookId should delegate to dao`() = runTest {
        val dao = FakeProgressDao().apply {
            byBookId[5L] = ProgressEntity(
                bookId = 5L,
                locatorJson = "x",
                progression = 0.1,
                updatedAtEpochMs = 1L
            )
        }
        val repo = ProgressRepo(dao)

        repo.deleteByBookId(5L)

        assertEquals(null, dao.byBookId[5L])
    }
}

private class FakeProgressDao : ProgressDao {
    val byBookId: MutableMap<Long, ProgressEntity> = mutableMapOf()
    private val flows: MutableMap<Long, MutableStateFlow<ProgressEntity?>> = mutableMapOf()

    fun emit(entity: ProgressEntity) {
        byBookId[entity.bookId] = entity
        flows.getOrPut(entity.bookId) { MutableStateFlow(null) }.value = entity
    }

    override suspend fun upsert(entity: ProgressEntity) {
        emit(entity)
    }

    override suspend fun getByBookId(bookId: Long): ProgressEntity? = byBookId[bookId]

    override fun observeByBookId(bookId: Long): Flow<ProgressEntity?> {
        return flows.getOrPut(bookId) { MutableStateFlow(byBookId[bookId]) }
    }

    override suspend fun deleteByBookId(bookId: Long) {
        byBookId.remove(bookId)
        flows.getOrPut(bookId) { MutableStateFlow(null) }.value = null
    }
}
