package com.ireader.feature.library.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireader.core.data.book.LibrarySort
import com.ireader.feature.library.domain.usecase.DeleteBookUseCase
import com.ireader.feature.library.domain.usecase.LoadLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val loadLibrary: LoadLibraryUseCase,
    private val deleteBook: DeleteBookUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sortFlow = MutableStateFlow(
        savedStateHandle.get<String>(KEY_SORT)
            ?.let { runCatching { LibrarySort.valueOf(it) }.getOrNull() }
            ?: LibrarySort.RECENTLY_UPDATED
    )

    val uiState: StateFlow<LibraryUiState> = sortFlow
        .flatMapLatest { sort ->
            loadLibrary(sort).map { books ->
                LibraryUiState(books = books, sort = sort)
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            LibraryUiState()
        )

    fun setSort(sort: LibrarySort) {
        sortFlow.value = sort
        savedStateHandle[KEY_SORT] = sort.name
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            deleteBook(bookId)
        }
    }

    private companion object {
        private const val KEY_SORT = "library_sort"
    }
}
