package com.ireader.core.work

import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WorkImportSchedulerTest {

    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkImportScheduler

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .setTaskExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        scheduler = WorkImportScheduler(workManager)
    }

    @After
    fun tearDown() {
        workManager.cancelAllWork()
    }

    @Test
    fun `enqueue should add unique work with expected tag`() {
        val jobId = "job-1"

        scheduler.enqueue(jobId)

        val infos = workManager.getWorkInfosForUniqueWork(WorkNames.uniqueForJob(jobId)).get()
        assertEquals(1, infos.size)
        assertTrue(infos.single().tags.contains(WorkNames.tagForJob(jobId)))
    }

    @Test
    fun `enqueue should keep single work for same job id`() {
        val jobId = "job-keep"

        scheduler.enqueue(jobId)
        scheduler.enqueue(jobId)

        val infos = workManager.getWorkInfosForUniqueWork(WorkNames.uniqueForJob(jobId)).get()
        assertEquals(1, infos.size)
    }

    @Test
    fun `cancel should cancel unique work`() = runTest {
        val jobId = "job-cancel"
        scheduler.enqueue(jobId)

        scheduler.cancel(jobId)
        scheduler.cancel("missing-job")

        val infos = workManager.getWorkInfosForUniqueWork(WorkNames.uniqueForJob(jobId)).get()
        assertEquals(1, infos.size)
        assertTrue(infos.single().tags.contains(WorkNames.tagForJob(jobId)))
    }
}
