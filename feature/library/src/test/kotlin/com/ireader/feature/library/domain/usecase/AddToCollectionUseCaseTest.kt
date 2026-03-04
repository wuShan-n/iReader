package com.ireader.feature.library.domain.usecase

import com.ireader.core.data.book.BookRepo
import com.ireader.core.database.collection.CollectionEntity
import com.ireader.feature.library.testing.fakeBookCollectionDao
import com.ireader.feature.library.testing.fakeBookDao
import com.ireader.feature.library.testing.fakeCollectionDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AddToCollectionUseCaseTest {

    @Test
    fun `invoke should create collection and attach book`() = runTest {
        var createdCollection: CollectionEntity? = null
        var insertedBookId: Long? = null
        var insertedCollectionId: Long? = null

        val bookRepo = BookRepo(
            bookDao = fakeBookDao(),
            collectionDao = fakeCollectionDao(
                overrides = mapOf(
                    "getByName" to { _ -> null },
                    "upsert" to { args ->
                        createdCollection = args?.get(0) as CollectionEntity
                        101L
                    }
                )
            ),
            bookCollectionDao = fakeBookCollectionDao(
                overrides = mapOf(
                    "insert" to { args ->
                        val entity = args?.get(0) as com.ireader.core.database.collection.BookCollectionEntity
                        insertedBookId = entity.bookId
                        insertedCollectionId = entity.collectionId
                        1L
                    }
                )
            )
        )
        val useCase = AddToCollectionUseCase(bookRepo = bookRepo)

        useCase(bookId = 88L, collectionName = "SciFi")

        assertNotNull(createdCollection)
        assertEquals("SciFi", createdCollection?.name)
        assertEquals(88L, insertedBookId)
        assertEquals(101L, insertedCollectionId)
    }
}
