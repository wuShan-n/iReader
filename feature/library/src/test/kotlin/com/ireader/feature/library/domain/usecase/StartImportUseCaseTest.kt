package com.ireader.feature.library.domain.usecase

import android.net.Uri
import com.ireader.core.files.importing.DuplicateStrategy
import com.ireader.feature.library.testing.FakeImportManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StartImportUseCaseTest {

    @Test
    fun `invoke should use skip strategy by default`() = runTest {
        val manager = FakeImportManager()
        val useCase = StartImportUseCase(importManager = manager)
        val uri = Uri.parse("content://books/1")

        useCase(uris = listOf(uri))

        assertEquals(DuplicateStrategy.SKIP, manager.lastRequest?.duplicateStrategy)
        assertEquals(listOf(uri), manager.lastRequest?.uris)
    }

    @Test
    fun `invoke should forward explicit strategy`() = runTest {
        val manager = FakeImportManager()
        val useCase = StartImportUseCase(importManager = manager)
        val uri = Uri.parse("content://books/2")

        useCase(uris = listOf(uri), strategy = DuplicateStrategy.REPLACE)

        assertEquals(DuplicateStrategy.REPLACE, manager.lastRequest?.duplicateStrategy)
    }
}
