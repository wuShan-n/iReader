package com.ireader.core.work.index

import androidx.work.Data

object ReindexWorkerInput {
    private const val KEY_BOOK_IDS = "book_ids"

    fun data(bookIds: LongArray): Data {
        return Data.Builder()
            .putLongArray(KEY_BOOK_IDS, bookIds)
            .build()
    }

    fun bookIds(data: Data): LongArray = data.getLongArray(KEY_BOOK_IDS) ?: longArrayOf()
}
