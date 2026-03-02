package com.ireader.core.work.index

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ireader.core.data.book.BookRepo
import com.ireader.core.database.book.IndexState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class MissingCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val bookRepo: BookRepo
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val books = bookRepo.listAll()
        for (book in books) {
            if (book.canonicalPath.isBlank()) {
                bookRepo.setIndexState(
                    bookId = book.bookId,
                    state = IndexState.MISSING,
                    error = "file missing"
                )
                continue
            }

            val exists = File(book.canonicalPath).exists()
            if (!exists && book.indexState != IndexState.MISSING) {
                bookRepo.setIndexState(
                    bookId = book.bookId,
                    state = IndexState.MISSING,
                    error = "file missing"
                )
            }
        }
        Result.success()
    }
}
