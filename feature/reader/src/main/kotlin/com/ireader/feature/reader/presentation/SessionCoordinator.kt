package com.ireader.feature.reader.presentation

import com.ireader.reader.model.Locator
import com.ireader.reader.runtime.ReaderSessionHandle
import kotlinx.coroutines.Job

internal class SessionCoordinator {
    private var sessionHandle: ReaderSessionHandle? = null
    private var activeBookId: Long = -1L

    private var progressJob: Job? = null
    private var stateJob: Job? = null
    private var eventJob: Job? = null
    private var settingsJob: Job? = null

    fun currentHandle(): ReaderSessionHandle? = sessionHandle

    fun currentBookId(): Long = activeBookId

    fun attach(bookId: Long, handle: ReaderSessionHandle) {
        activeBookId = bookId
        sessionHandle = handle
    }

    fun clearHandle() {
        activeBookId = -1L
        sessionHandle = null
    }

    fun bindCollectors(
        progressJob: Job?,
        stateJob: Job?,
        eventJob: Job?,
        settingsJob: Job?
    ) {
        cancelCollectors()
        this.progressJob = progressJob
        this.stateJob = stateJob
        this.eventJob = eventJob
        this.settingsJob = settingsJob
    }

    fun cancelCollectors() {
        progressJob?.cancel()
        progressJob = null
        stateJob?.cancel()
        stateJob = null
        eventJob?.cancel()
        eventJob = null
        settingsJob?.cancel()
        settingsJob = null
    }

    suspend fun closeCurrent(
        saveProgress: suspend (bookId: Long, locator: Locator, progression: Double) -> Unit
    ) {
        val current = sessionHandle
        val bookId = activeBookId
        cancelCollectors()
        clearHandle()

        if (current != null && bookId > 0L) {
            runCatching {
                val renderState = current.controller.state.value
                saveProgress(bookId, renderState.locator, renderState.progression.percent)
            }
        }

        if (current != null) {
            runCatching { current.close() }
        }
    }
}
