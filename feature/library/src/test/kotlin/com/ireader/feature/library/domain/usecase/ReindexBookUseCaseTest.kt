package com.ireader.feature.library.domain.usecase

import com.ireader.core.data.book.BookRepo
import com.ireader.core.database.book.IndexState
import com.ireader.feature.library.testing.FakeBookMaintenanceScheduler
import com.ireader.feature.library.testing.fakeBookCollectionDao
import com.ireader.feature.library.testing.fakeBookDao
import com.ireader.feature.library.testing.fakeCollectionDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReindexBookUseCaseTest {

    @Test
    fun `invoke should mark index pending and enqueue when id is valid`() = runTest {
        var updatedId: Long? = null
        var updatedState: IndexState? = null
        val bookRepo = BookRepo(
            bookDao = fakeBookDao(
                overrides = mapOf(
                    "updateIndexState" to { args ->
                        updatedId = args?.get(0) as Long
                        updatedState = args[1] as IndexState
                        Unit
                    }
                )
            ),
            collectionDao = fakeCollectionDao(),
            bookCollectionDao = fakeBookCollectionDao()
        )
        val scheduler = FakeBookMaintenanceScheduler()
        val useCase = ReindexBookUseCase(bookRepo = bookRepo, scheduler = scheduler)

        useCase(bookId = 7L)

        assertEquals(7L, updatedId)
        assertEquals(IndexState.PENDING, updatedState)
        assertEquals(listOf(listOf(7L)), scheduler.reindexRequests)
    }

    @Test
    fun `invoke should do nothing when id is invalid`() = runTest {
        var updateCalled = false
        val bookRepo = BookRepo(
            bookDao = fakeBookDao(
                overrides = mapOf(
                    "updateIndexState" to {
                        updateCalled = true
                        Unit
                    }
                )
            ),
            collectionDao = fakeCollectionDao(),
            bookCollectionDao = fakeBookCollectionDao()
        )
        val scheduler = FakeBookMaintenanceScheduler()
        val useCase = ReindexBookUseCase(bookRepo = bookRepo, scheduler = scheduler)

        useCase(bookId = 0L)

        assertTrue(!updateCalled)
        assertTrue(scheduler.reindexRequests.isEmpty())
    }
}
