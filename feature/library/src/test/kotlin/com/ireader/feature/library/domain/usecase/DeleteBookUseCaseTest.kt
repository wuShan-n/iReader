package com.ireader.feature.library.domain.usecase

import android.content.Context
import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.book.ProgressRepo
import com.ireader.core.files.storage.BookStorage
import com.ireader.feature.library.testing.fakeBookCollectionDao
import com.ireader.feature.library.testing.fakeBookDao
import com.ireader.feature.library.testing.fakeCollectionDao
import com.ireader.feature.library.testing.fakeProgressDao
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DeleteBookUseCaseTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `invoke should delete book row progress row and stored files`() = runTest {
        var deletedBookId: Long? = null
        var deletedProgressBookId: Long? = null
        val bookRepo = BookRepo(
            bookDao = fakeBookDao(
                overrides = mapOf(
                    "deleteById" to { args ->
                        deletedBookId = args?.get(0) as Long
                        Unit
                    }
                )
            ),
            collectionDao = fakeCollectionDao(),
            bookCollectionDao = fakeBookCollectionDao()
        )
        val progressRepo = ProgressRepo(
            progressDao = fakeProgressDao(
                overrides = mapOf(
                    "deleteByBookId" to { args ->
                        deletedProgressBookId = args?.get(0) as Long
                        Unit
                    }
                )
            )
        )
        val storage = BookStorage(context)
        val useCase = DeleteBookUseCase(
            bookRepo = bookRepo,
            progressRepo = progressRepo,
            storage = storage
        )
        val bookId = 1234L
        val bookDir = storage.bookDir(bookId)
        File(bookDir, "original.txt").writeText("hello")

        useCase(bookId)

        assertEquals(bookId, deletedBookId)
        assertEquals(bookId, deletedProgressBookId)
        assertFalse(bookDir.exists())
    }
}
