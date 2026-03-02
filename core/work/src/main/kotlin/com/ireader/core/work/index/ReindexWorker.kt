package com.ireader.core.work.index

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ireader.core.data.book.BookIndexer
import com.ireader.core.data.book.BookRepo
import com.ireader.core.database.book.IndexState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class ReindexWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val bookRepo: BookRepo,
    private val bookIndexer: BookIndexer
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val bookIds = ReindexWorkerInput.bookIds(inputData)
        if (bookIds.isEmpty()) {
            return@withContext Result.success()
        }

        for (bookId in bookIds) {
            if (bookId <= 0L) {
                continue
            }

            bookRepo.setIndexState(bookId = bookId, state = IndexState.PENDING, error = null)
            runCatching { bookIndexer.reindex(bookId) }
        }

        Result.success()
    }
}
