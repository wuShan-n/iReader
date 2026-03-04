package com.ireader.feature.library.domain.usecase

import com.ireader.core.data.book.BookRepo
import com.ireader.feature.library.testing.fakeBookCollectionDao
import com.ireader.feature.library.testing.fakeBookDao
import com.ireader.feature.library.testing.fakeCollectionDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ToggleFavoriteUseCaseTest {

    @Test
    fun `invoke should invert current favorite value`() = runTest {
        var capturedBookId: Long? = null
        var capturedFavorite: Boolean? = null
        val bookRepo = BookRepo(
            bookDao = fakeBookDao(
                overrides = mapOf(
                    "updateFavorite" to { args ->
                        capturedBookId = args?.get(0) as Long
                        capturedFavorite = args[1] as Boolean
                        Unit
                    }
                )
            ),
            collectionDao = fakeCollectionDao(),
            bookCollectionDao = fakeBookCollectionDao()
        )
        val useCase = ToggleFavoriteUseCase(bookRepo = bookRepo)

        useCase(bookId = 9L, current = true)

        assertEquals(9L, capturedBookId)
        assertEquals(false, capturedFavorite)
    }
}
