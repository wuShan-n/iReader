package com.ireader.feature.library.presentation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireader.core.data.book.LibraryQuery
import com.ireader.core.data.book.LibrarySort
import com.ireader.core.database.book.IndexState
import com.ireader.core.database.book.ReadingStatus
import com.ireader.core.database.importing.ImportStatus
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
import com.ireader.feature.library.domain.usecase.AddToCollectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val loadLibrary: LoadLibraryUseCase,
    private val deleteBookUseCase: DeleteBookUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val updateReadingStatusUseCase: UpdateReadingStatusUseCase,
    private val addToCollectionUseCase: AddToCollectionUseCase,
    private val reindexBookUseCase: ReindexBookUseCase,
    private val relinkBookUseCase: RelinkBookUseCase,
    private val startImportUseCase: StartImportUseCase,
    private val observeImportJobUseCase: ObserveImportJobUseCase,
    private val observeCollectionsUseCase: ObserveCollectionsUseCase,
    private val runMissingCheckUseCase: RunMissingCheckUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sortFlow = MutableStateFlow(
        savedStateHandle.get<String>(KEY_SORT)
            ?.let { runCatching { LibrarySort.valueOf(it) }.getOrNull() }
            ?: LibrarySort.RECENTLY_UPDATED
    )

    private val keywordFlow = MutableStateFlow(savedStateHandle.get<String>(KEY_KEYWORD).orEmpty())
    private val statusesFlow = MutableStateFlow(decodeReadingStatuses(savedStateHandle.get(KEY_STATUSES)))
    private val indexStatesFlow = MutableStateFlow(decodeIndexStates(savedStateHandle.get(KEY_INDEX_STATES)))
    private val onlyFavoritesFlow = MutableStateFlow(savedStateHandle.get<Boolean>(KEY_ONLY_FAVORITES) ?: false)
    private val collectionIdFlow = MutableStateFlow(savedStateHandle.get<Long>(KEY_COLLECTION_ID))

    private val activeImportJobIdFlow = MutableStateFlow<String?>(null)
    private val importStatusTextFlow = MutableStateFlow<String?>(null)
    private var importObservationJob: Job? = null

    private val queryFlow = combine(sortFlow, keywordFlow) { sort, keyword ->
        QueryDraft(sort = sort, keyword = keyword)
    }
        .combine(statusesFlow) { draft, statuses ->
            draft.copy(statuses = statuses)
        }
        .combine(indexStatesFlow) { draft, indexStates ->
            draft.copy(indexStates = indexStates)
        }
        .combine(onlyFavoritesFlow) { draft, onlyFavorites ->
            draft.copy(onlyFavorites = onlyFavorites)
        }
        .combine(collectionIdFlow) { draft, collectionId ->
            LibraryQuery(
                sort = draft.sort,
                keyword = draft.keyword,
                statuses = draft.statuses,
                indexStates = draft.indexStates,
                onlyFavorites = draft.onlyFavorites,
                collectionId = collectionId
            )
        }

    private val booksFlow = queryFlow.flatMapLatest { query -> loadLibrary(query) }
    private val collectionsFlow = observeCollectionsUseCase()

    val uiState: StateFlow<LibraryUiState> = combine(
        queryFlow,
        booksFlow,
        collectionsFlow,
        activeImportJobIdFlow,
        importStatusTextFlow
    ) { query, books, collections, activeJobId, importStatusText ->
        LibraryUiState(
            books = books,
            sort = query.sort,
            keyword = query.keyword.orEmpty(),
            statuses = query.statuses,
            indexStates = query.indexStates,
            onlyFavorites = query.onlyFavorites,
            selectedCollectionId = query.collectionId,
            collections = collections,
            activeImportJobId = activeJobId,
            importStatusText = importStatusText
        )
    }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            LibraryUiState()
        )

    init {
        runMissingCheckUseCase()
    }

    fun startImport(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val jobId = runCatching { startImportUseCase(uris) }
                .getOrElse { throwable ->
                    importStatusTextFlow.value = throwable.message ?: "导入任务创建失败"
                    null
                }
                ?: return@launch
            activeImportJobIdFlow.value = jobId
            observeImportJobUpdates(jobId)
        }
    }

    fun setSort(sort: LibrarySort) {
        sortFlow.value = sort
        savedStateHandle[KEY_SORT] = sort.name
    }

    fun setKeyword(keyword: String) {
        keywordFlow.value = keyword
        savedStateHandle[KEY_KEYWORD] = keyword
    }

    fun setOnlyFavorites(onlyFavorites: Boolean) {
        onlyFavoritesFlow.value = onlyFavorites
        savedStateHandle[KEY_ONLY_FAVORITES] = onlyFavorites
    }

    fun setCollectionFilter(collectionId: Long?) {
        collectionIdFlow.value = collectionId
        savedStateHandle[KEY_COLLECTION_ID] = collectionId
    }

    fun setReadingStatusFilter(status: ReadingStatus, enabled: Boolean) {
        statusesFlow.value = statusesFlow.value.toggle(status, enabled)
        savedStateHandle[KEY_STATUSES] = encodeEnumSet(statusesFlow.value)
    }

    fun setIndexStateFilter(state: IndexState, enabled: Boolean) {
        indexStatesFlow.value = indexStatesFlow.value.toggle(state, enabled)
        savedStateHandle[KEY_INDEX_STATES] = encodeEnumSet(indexStatesFlow.value)
    }

    fun toggleFavorite(bookId: Long, current: Boolean) {
        viewModelScope.launch { toggleFavoriteUseCase(bookId, current) }
    }

    fun setReadingStatus(bookId: Long, status: ReadingStatus) {
        viewModelScope.launch { updateReadingStatusUseCase(bookId, status) }
    }

    fun addToCollection(bookId: Long, collectionName: String) {
        if (collectionName.isBlank()) return
        viewModelScope.launch { addToCollectionUseCase(bookId, collectionName) }
    }

    fun reindexBook(bookId: Long) {
        viewModelScope.launch { reindexBookUseCase(bookId) }
    }

    fun relinkBook(bookId: Long, uri: Uri) {
        viewModelScope.launch {
            runCatching { relinkBookUseCase(bookId, uri) }
                .onFailure { throwable ->
                    importStatusTextFlow.value = throwable.message ?: "重定位失败"
                }
        }
    }

    fun deleteBook(bookId: Long) {
        viewModelScope.launch {
            deleteBookUseCase(bookId)
        }
    }

    fun dismissImportStatus() {
        importStatusTextFlow.value = null
    }

    private fun observeImportJobUpdates(jobId: String) {
        importObservationJob?.cancel()
        importObservationJob = viewModelScope.launch {
            observeImportJobUseCase(jobId).collect { state ->
                val currentTitle = state.currentTitle?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                importStatusTextFlow.value = "${state.status} ${state.done}/${state.total}$currentTitle"
                if (state.status.isTerminal()) {
                    activeImportJobIdFlow.value = null
                }
            }
        }
    }

    private fun ImportStatus.isTerminal(): Boolean {
        return this == ImportStatus.SUCCEEDED || this == ImportStatus.FAILED || this == ImportStatus.CANCELLED
    }

    private fun <T> Set<T>.toggle(value: T, enabled: Boolean): Set<T> {
        return if (enabled) this + value else this - value
    }

    private fun <T : Enum<T>> encodeEnumSet(values: Set<T>): String {
        return values.map { it.name }.sorted().joinToString(separator = ",")
    }

    private fun decodeReadingStatuses(raw: String?): Set<ReadingStatus> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split(',')
            .mapNotNull { token -> runCatching { ReadingStatus.valueOf(token) }.getOrNull() }
            .toSet()
    }

    private fun decodeIndexStates(raw: String?): Set<IndexState> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split(',')
            .mapNotNull { token -> runCatching { IndexState.valueOf(token) }.getOrNull() }
            .toSet()
    }

    private companion object {
        private const val KEY_SORT = "library_sort"
        private const val KEY_KEYWORD = "library_keyword"
        private const val KEY_STATUSES = "library_statuses"
        private const val KEY_INDEX_STATES = "library_index_states"
        private const val KEY_ONLY_FAVORITES = "library_only_favorites"
        private const val KEY_COLLECTION_ID = "library_collection_id"
    }

    private data class QueryDraft(
        val sort: LibrarySort,
        val keyword: String,
        val statuses: Set<ReadingStatus> = emptySet(),
        val indexStates: Set<IndexState> = emptySet(),
        val onlyFavorites: Boolean = false
    )
}
