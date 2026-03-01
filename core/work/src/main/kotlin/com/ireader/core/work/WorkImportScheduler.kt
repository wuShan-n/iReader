package com.ireader.core.work

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ireader.core.files.importing.ImportWorkScheduler
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkImportScheduler @Inject constructor(
    private val workManager: WorkManager
) : ImportWorkScheduler {
    override fun enqueue(jobId: String) {
        val work = OneTimeWorkRequestBuilder<ImportWorker>()
            .setInputData(ImportWorker.input(jobId))
            .addTag(WorkNames.tagForJob(jobId))
            .build()

        workManager.enqueueUniqueWork(
            WorkNames.uniqueForJob(jobId),
            ExistingWorkPolicy.KEEP,
            work
        )
    }

    override suspend fun cancel(jobId: String) {
        workManager.cancelUniqueWork(WorkNames.uniqueForJob(jobId))
    }
}
