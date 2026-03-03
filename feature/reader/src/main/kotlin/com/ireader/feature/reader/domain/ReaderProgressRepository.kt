package com.ireader.feature.reader.domain

import com.ireader.reader.model.Locator

interface ReaderProgressRepository {
    suspend fun getLastLocator(bookId: Long): Locator?
    suspend fun saveProgress(bookId: Long, locator: Locator, progression: Double)
}

