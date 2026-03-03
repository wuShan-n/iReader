package com.ireader.feature.library.domain.usecase

import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.book.CollectionItem
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveCollectionsUseCase @Inject constructor(
    private val bookRepo: BookRepo
) {
    operator fun invoke(): Flow<List<CollectionItem>> = bookRepo.observeCollections()
}
