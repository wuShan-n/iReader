package com.ireader.core.work.index

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ireader.core.data.book.BookMaintenanceScheduler
import com.ireader.core.work.WorkNames
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkBookMaintenanceScheduler @Inject constructor(
    private val workManager: WorkManager
) : BookMaintenanceScheduler {

    override fun enqueueReindex(bookIds: List<Long>) {
        val normalized = bookIds.filter { it > 0L }.distinct()
        if (normalized.isEmpty()) {
            return
        }

        val work = OneTimeWorkRequestBuilder<ReindexWorker>()
            .setInputData(ReindexWorkerInput.data(normalized.toLongArray()))
            .addTag(WorkNames.REINDEX_TAG)
            .build()

        workManager.enqueueUniqueWork(
            WorkNames.uniqueReindex(normalized),
            ExistingWorkPolicy.REPLACE,
            work
        )
    }

    override fun enqueueMissingCheck() {
        val work = OneTimeWorkRequestBuilder<MissingCheckWorker>()
            .addTag(WorkNames.MISSING_CHECK_TAG)
            .build()

        workManager.enqueueUniqueWork(
            WorkNames.MISSING_CHECK_UNIQUE,
            ExistingWorkPolicy.KEEP,
            work
        )
    }
}
