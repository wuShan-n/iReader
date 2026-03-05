package com.ireader.feature.reader.presentation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class ReaderUiReducer(
    private val stateStore: MutableStateFlow<ReaderUiState>,
    private val effectStore: MutableSharedFlow<ReaderEffect>
) {
    val state: StateFlow<ReaderUiState> = stateStore.asStateFlow()

    fun update(transform: (ReaderUiState) -> ReaderUiState) {
        stateStore.update(transform)
    }

    fun emit(effect: ReaderEffect) {
        effectStore.tryEmit(effect)
    }

    suspend fun emitGuaranteed(effect: ReaderEffect) {
        effectStore.emit(effect)
    }
}
