package com.ireader.feature.library.presentation

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.SavedStateHandle
import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.book.IndexState
import com.ireader.core.data.book.ProgressRepo
import com.ireader.core.data.book.ReadingStatus
import com.ireader.core.files.importing.ImportJobState
import com.ireader.core.files.importing.ImportJobStatus
import com.ireader.core.files.permission.UriPermissionGateway
import com.ireader.core.files.permission.UriPermissionResult
import com.ireader.core.files.source.DocumentSource
import com.ireader.core.files.source.UriDocumentSourceFactory
import com.ireader.core.files.storage.BookStorage
import com.ireader.core.testing.MainDispatcherRule
import com.ireader.feature.library.domain.usecase.AddToCollectionUseCase
import com.ireader.feature.library.domain.usecase.DeleteBookUseCase
import com.ireader.feature.library.domain.usecase.LoadLibraryUseCase
import com.ireader.feature.library.domain.usecase.ObserveCollectionsUseCase
import com.ireader.feature.library.domain.usecase.ObserveImportJobUseCase
import com.ireader.feature.library.domain.usecase.ReindexBookUseCase
import com.ireader.feature.library.domain.usecase.RelinkBookUseCase
import com.ireader.feature.library.domain.usecase.RunMissingCheckUseCase
import com.ireader.feature.library.domain.usecase.StartImportUseCase
import com.ireader.feature.library.domain.usecase.ToggleFavoriteUseCase
import com.ireader.feature.library.domain.usecase.UpdateReadingStatusUseCase
import com.ireader.feature.library.testing.FakeBookMaintenanceScheduler
import com.ireader.feature.library.testing.FakeImportManager
import com.ireader.feature.library.testing.MutableImportJobFlow
import com.ireader.feature.library.testing.fakeBookCollectionDao
import com.ireader.feature.library.testing.fakeBookDao
import com.ireader.feature.library.testing.fakeCollectionDao
import com.ireader.feature.library.testing.fakeProgressDao
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `init should enqueue missing check once`() {
        val fixture = newFixture()

        assertEquals(1, fixture.scheduler.missingCheckCount)
    }

    @Test
    fun `setters should update ui state and saved state`() = runTest {
        val fixture = newFixture()
        val collectJob = startCollecting(fixture.viewModel)

        fixture.viewModel.setSort(com.ireader.core.data.book.LibrarySort.TITLE_AZ)
        fixture.viewModel.setKeyword("hello")
        fixture.viewModel.setOnlyFavorites(true)
        fixture.viewModel.setCollectionFilter(7L)
        fixture.viewModel.setReadingStatusFilter(ReadingStatus.READING, true)
        fixture.viewModel.setIndexStateFilter(IndexState.MISSING, true)
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertEquals(com.ireader.core.data.book.LibrarySort.TITLE_AZ, state.sort)
        assertEquals("hello", state.keyword)
        assertEquals(true, state.onlyFavorites)
        assertEquals(7L, state.selectedCollectionId)
        assertEquals(setOf(ReadingStatus.READING), state.statuses)
        assertEquals(setOf(IndexState.MISSING), state.indexStates)
        assertEquals("TITLE_AZ", fixture.savedStateHandle.get<String>("library_sort"))
        assertEquals("hello", fixture.savedStateHandle.get<String>("library_keyword"))
        assertEquals(true, fixture.savedStateHandle.get<Boolean>("library_only_favorites"))
        assertEquals(7L, fixture.savedStateHandle.get<Long>("library_collection_id"))
        assertEquals("READING", fixture.savedStateHandle.get<String>("library_statuses"))
        assertEquals("MISSING", fixture.savedStateHandle.get<String>("library_index_states"))

        collectJob.cancel()
    }

    @Test
    fun `startImport should observe progress and clear active job on terminal status`() = runTest {
        val fixture = newFixture()
        val jobId = "job-42"
        val flow = MutableImportJobFlow(
            ImportJobState(
                jobId = jobId,
                status = ImportJobStatus.RUNNING,
                total = 10,
                done = 2,
                currentTitle = "chapter 1",
                errorMessage = null
            )
        )
        fixture.importManager.nextJobId = jobId
        fixture.importManager.flows[jobId] = flow.flow
        val collectJob = startCollecting(fixture.viewModel)

        fixture.viewModel.startImport(uris = listOf(Uri.parse("content://books/1")))
        advanceUntilIdle()

        assertEquals(jobId, fixture.viewModel.uiState.value.activeImportJobId)
        assertEquals("RUNNING 2/10 · chapter 1", fixture.viewModel.uiState.value.importStatusText)

        flow.emit(
            ImportJobState(
                jobId = jobId,
                status = ImportJobStatus.SUCCEEDED,
                total = 10,
                done = 10,
                currentTitle = null,
                errorMessage = null
            )
        )
        advanceUntilIdle()

        assertNull(fixture.viewModel.uiState.value.activeImportJobId)
        assertEquals("SUCCEEDED 10/10", fixture.viewModel.uiState.value.importStatusText)

        collectJob.cancel()
    }

    @Test
    fun `startImport failure should expose message and dismiss should clear it`() = runTest {
        val fixture = newFixture()
        val collectJob = startCollecting(fixture.viewModel)
        fixture.importManager.enqueueError = IllegalStateException("boom")

        fixture.viewModel.startImport(uris = listOf(Uri.parse("content://books/2")))
        advanceUntilIdle()

        assertNull(fixture.viewModel.uiState.value.activeImportJobId)
        assertEquals("boom", fixture.viewModel.uiState.value.importStatusText)

        fixture.viewModel.dismissImportStatus()
        assertNull(fixture.viewModel.uiState.value.importStatusText)

        collectJob.cancel()
    }

    private fun TestScope.startCollecting(viewModel: LibraryViewModel): Job {
        return backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
    }

    private fun newFixture(): LibraryFixture {
        val bookRepo = BookRepo(
            bookDao = fakeBookDao(),
            collectionDao = fakeCollectionDao(),
            bookCollectionDao = fakeBookCollectionDao()
        )
        val progressRepo = ProgressRepo(fakeProgressDao())
        val scheduler = FakeBookMaintenanceScheduler()
        val importManager = FakeImportManager()
        val savedStateHandle = SavedStateHandle()
        val storage = BookStorage(context)
        val permissionGateway = AlwaysGrantedPermissionGateway()
        val sourceFactory = EmptySourceFactory()
        val viewModel = LibraryViewModel(
            loadLibrary = LoadLibraryUseCase(bookRepo),
            deleteBookUseCase = DeleteBookUseCase(bookRepo, progressRepo, storage),
            toggleFavoriteUseCase = ToggleFavoriteUseCase(bookRepo),
            updateReadingStatusUseCase = UpdateReadingStatusUseCase(bookRepo),
            addToCollectionUseCase = AddToCollectionUseCase(bookRepo),
            reindexBookUseCase = ReindexBookUseCase(bookRepo, scheduler),
            relinkBookUseCase = RelinkBookUseCase(
                permissionStore = permissionGateway,
                sourceFactory = sourceFactory,
                bookRepo = bookRepo,
                storage = storage,
                scheduler = scheduler
            ),
            startImportUseCase = StartImportUseCase(importManager),
            observeImportJobUseCase = ObserveImportJobUseCase(importManager),
            observeCollectionsUseCase = ObserveCollectionsUseCase(bookRepo),
            runMissingCheckUseCase = RunMissingCheckUseCase(scheduler),
            savedStateHandle = savedStateHandle
        )
        return LibraryFixture(
            viewModel = viewModel,
            scheduler = scheduler,
            importManager = importManager,
            savedStateHandle = savedStateHandle
        )
    }

    private data class LibraryFixture(
        val viewModel: LibraryViewModel,
        val scheduler: FakeBookMaintenanceScheduler,
        val importManager: FakeImportManager,
        val savedStateHandle: SavedStateHandle
    )
}

private class AlwaysGrantedPermissionGateway : UriPermissionGateway {
    override fun takePersistableRead(uri: Uri): UriPermissionResult = UriPermissionResult(granted = true)
    override fun hasPersistedRead(uri: Uri): Boolean = true
}

private class EmptySourceFactory : UriDocumentSourceFactory {
    override fun create(uri: Uri): DocumentSource {
        return object : DocumentSource {
            override val uri: Uri = uri
            override val displayName: String? = null
            override val mimeType: String? = null
            override val sizeBytes: Long? = null
            override suspend fun openInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))
            override suspend fun openFileDescriptor(mode: String): ParcelFileDescriptor? = null
        }
    }
}
