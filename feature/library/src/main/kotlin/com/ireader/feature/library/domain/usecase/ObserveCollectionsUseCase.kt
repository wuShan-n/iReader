package com.ireader.feature.library.domain.usecase

import com.ireader.core.data.book.BookRepo
import com.ireader.core.database.collection.CollectionEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveCollectionsUseCase @Inject constructor(
    private val bookRepo: BookRepo
) {
    operator fun invoke(): Flow<List<CollectionEntity>> = bookRepo.observeCollections()
}
