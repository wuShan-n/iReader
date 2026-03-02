package com.ireader.core.work.enrich

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ireader.core.data.book.BookIndexer
import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.importing.ImportItemRepo
import com.ireader.core.work.notification.ImportForeground
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@HiltWorker
class EnrichWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val importItemRepo: ImportItemRepo,
    private val bookRepo: BookRepo,
    private val bookIndexer: BookIndexer
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobId = EnrichWorkerInput.jobId(inputData) ?: return@withContext Result.failure()
        val bookIds = importItemRepo.listSucceededBookIds(jobId)
        if (bookIds.isEmpty()) {
            return@withContext Result.success()
        }

        var done = 0
        val total = bookIds.size
        val throttle = ProgressThrottle(minIntervalMs = 500L)

        try {
            updateProgress(done, total, "Enriching…")

            for (bookId in bookIds) {
                currentCoroutineContext().ensureActive()
                val title = bookRepo.getById(bookId)?.title ?: "Enriching…"

                runCatching {
                    bookIndexer.index(bookId)
                }

                done += 1
                if (throttle.shouldUpdate()) {
                    updateProgress(done, total, title)
                }
            }

            throttle.force()
            updateProgress(done, total, "Enrich complete")
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            Result.success()
        }
    }

    private suspend fun updateProgress(done: Int, total: Int, title: String?) {
        runCatching {
            setProgress(EnrichProgress.data(done, total, title))
        }
        runCatching {
            setForeground(ImportForeground.info(applicationContext, done, total, title))
        }
    }
}
