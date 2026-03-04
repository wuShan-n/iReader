package com.ireader.core.work.index

import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.ireader.core.work.WorkNames
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WorkBookMaintenanceSchedulerTest {

    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkBookMaintenanceScheduler

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
        scheduler = WorkBookMaintenanceScheduler(workManager)
    }

    @After
    fun tearDown() {
        workManager.cancelAllWork()
    }

    @Test
    fun `enqueueReindex should ignore non-positive ids`() {
        scheduler.enqueueReindex(listOf(0L, -1L))

        val tagged = workManager.getWorkInfosByTag(WorkNames.REINDEX_TAG).get()
        assertTrue(tagged.isEmpty())
    }

    @Test
    fun `enqueueReindex should normalize ids and use stable unique name`() {
        scheduler.enqueueReindex(listOf(4L, 2L, 4L))
        scheduler.enqueueReindex(listOf(2L, 4L))

        val uniqueName = WorkNames.uniqueReindex(listOf(2L, 4L))
        val infos = workManager.getWorkInfosForUniqueWork(uniqueName).get()

        assertEquals(1, infos.size)
        assertTrue(infos.single().tags.contains(WorkNames.REINDEX_TAG))
    }

    @Test
    fun `enqueueMissingCheck should keep a single unique work`() {
        scheduler.enqueueMissingCheck()
        scheduler.enqueueMissingCheck()

        val infos = workManager.getWorkInfosForUniqueWork(WorkNames.MISSING_CHECK_UNIQUE).get()

        assertEquals(1, infos.size)
        assertTrue(infos.single().tags.contains(WorkNames.MISSING_CHECK_TAG))
    }
}
