package com.ireader.feature.reader.presentation

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.TextLayouterFactory
import com.ireader.reader.model.Locator
import com.ireader.reader.runtime.ReaderHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class ReaderSessionSnapshot(
    val bookId: Long,
    val handle: ReaderHandle,
    val openEpoch: Long
)

internal data class ReaderViewportSnapshot(
    val session: ReaderSessionSnapshot,
    val layoutConstraints: LayoutConstraints,
    val textLayouterFactory: TextLayouterFactory?
)

internal class ReaderSessionInteractor {
    private val handleStore = MutableStateFlow<ReaderHandle?>(null)

    val handleState: StateFlow<ReaderHandle?> = handleStore.asStateFlow()

    private var sessionHandle: ReaderHandle? = null
    private var activeBookId: Long = -1L
    private var sessionOpenEpoch: Long = -1L
    private var layoutConstraints: LayoutConstraints? = null
    private var textLayouterFactory: TextLayouterFactory? = null
    private var appliedLayoutConstraints: LayoutConstraints? = null
    private var appliedTextLayouterFactoryKey: String? = null

    private var progressJob: Job? = null
    private var stateJob: Job? = null
    private var eventJob: Job? = null
    private var settingsJob: Job? = null

    fun currentHandle(): ReaderHandle? = sessionHandle

    fun currentBookId(): Long = activeBookId

    fun currentOpenEpoch(): Long = sessionOpenEpoch

    fun attach(
        bookId: Long,
        handle: ReaderHandle,
        openEpoch: Long
    ) {
        activeBookId = bookId
        sessionHandle = handle
        sessionOpenEpoch = openEpoch
        appliedLayoutConstraints = null
        appliedTextLayouterFactoryKey = null
        handleStore.value = handle
    }

    fun clearSession() {
        activeBookId = -1L
        sessionHandle = null
        sessionOpenEpoch = -1L
        appliedLayoutConstraints = null
        appliedTextLayouterFactoryKey = null
        handleStore.value = null
    }

    fun updateLayout(constraints: LayoutConstraints) {
        layoutConstraints = constraints
    }

    fun currentLayout(): LayoutConstraints? = layoutConstraints

    fun updateLayouter(factory: TextLayouterFactory) {
        textLayouterFactory = factory
    }

    fun currentLayouterFactory(): TextLayouterFactory? = textLayouterFactory

    fun snapshotIfReady(): ReaderViewportSnapshot? {
        val handle = sessionHandle ?: return null
        val constraints = layoutConstraints ?: return null
        val layouter = textLayouterFactory
        if (handle.capabilities.reflowable && layouter == null) {
            return null
        }
        return ReaderViewportSnapshot(
            session = ReaderSessionSnapshot(
                bookId = activeBookId,
                handle = handle,
                openEpoch = sessionOpenEpoch
            ),
            layoutConstraints = constraints,
            textLayouterFactory = layouter
        )
    }

    suspend fun bindViewportIfReady(
        handle: ReaderHandle,
        withNavigationLock: suspend (suspend () -> ReaderResult<Unit>) -> ReaderResult<Unit>
    ): ReaderResult<Unit>? {
        val snapshot = snapshotIfReady() ?: return null
        if (snapshot.session.handle !== handle) {
            return null
        }
        val environmentKey = snapshot.textLayouterFactory?.environmentKey
        if (appliedLayoutConstraints == snapshot.layoutConstraints &&
            appliedTextLayouterFactoryKey == environmentKey
        ) {
            return ReaderResult.Ok(Unit)
        }
        return when (
            val result = withNavigationLock {
                handle.bindViewport(
                    constraints = snapshot.layoutConstraints,
                    textLayouterFactory = snapshot.textLayouterFactory
                )
            }
        ) {
            is ReaderResult.Ok -> {
                appliedLayoutConstraints = snapshot.layoutConstraints
                appliedTextLayouterFactoryKey = environmentKey
                result
            }

            is ReaderResult.Err -> result
        }
    }

    fun isCurrent(
        handle: ReaderHandle,
        openEpoch: Long
    ): Boolean {
        return sessionHandle === handle && sessionOpenEpoch == openEpoch
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
        clearSession()

        if (current != null && bookId > 0L) {
            runCatching {
                val renderState = current.state.value
                saveProgress(bookId, renderState.locator, renderState.progression.percent)
            }
        }

        if (current != null) {
            runCatching { current.close() }
        }
    }
}
