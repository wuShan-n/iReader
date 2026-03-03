package com.ireader.feature.reader.domain.usecase

import com.ireader.core.data.book.LocatorCodec
import com.ireader.core.data.book.ProgressRepo
import com.ireader.core.database.progress.ProgressDao
import com.ireader.core.database.progress.ProgressEntity
import com.ireader.reader.model.Locator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SaveReadingProgressTest {

    @Test
    fun `progress should be clamped and locator encoded`() = runTest {
        val progressDao = FakeProgressDao()
        val progressRepo = ProgressRepo(progressDao)
        val codec = object : LocatorCodec {
            override fun encode(locator: Locator): String = "encoded:${locator.value}"
            override fun decode(raw: String): Locator? = null
        }
        val useCase = SaveReadingProgress(progressRepo = progressRepo, locatorCodec = codec)

        useCase(
            bookId = 7L,
            locator = Locator("txt.offset", "42"),
            progression = 2.0
        )

        val saved = progressDao.lastUpsert
        assertNotNull(saved)
        assertEquals(7L, saved?.bookId)
        assertEquals("encoded:42", saved?.locatorJson)
        assertEquals(1.0, saved?.progression ?: -1.0, 0.0)
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

