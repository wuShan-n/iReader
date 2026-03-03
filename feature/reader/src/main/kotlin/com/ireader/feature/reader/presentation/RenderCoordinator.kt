package com.ireader.feature.reader.presentation

import com.ireader.reader.api.render.LayoutConstraints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class RenderRequest {
    OPEN,
    LAYOUT,
    SETTINGS,
    CONFIG,
    REFRESH
}

internal class RenderCoordinator(
    private val scope: CoroutineScope,
    private val onRender: suspend () -> Unit
) {
    private val navigationMutex = Mutex()
    private var layoutConstraints: LayoutConstraints? = null

    private val requests = MutableSharedFlow<RenderRequest>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    @OptIn(FlowPreview::class)
    private val schedulerJob: Job = scope.launch {
        requests
            .debounce(24L)
            .collectLatest {
                onRender()
            }
    }

    fun updateLayout(constraints: LayoutConstraints) {
        layoutConstraints = constraints
    }

    fun currentLayout(): LayoutConstraints? = layoutConstraints

    fun requestRender(reason: RenderRequest) {
        requests.tryEmit(reason)
    }

    suspend fun <T> withNavigationLock(block: suspend () -> T): T = navigationMutex.withLock {
        block()
    }

    fun cancel() {
        schedulerJob.cancel()
    }
}
