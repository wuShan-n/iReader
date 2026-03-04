package com.ireader.engines.common.android.controller

import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderState
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

abstract class BaseCoroutineReaderController(
    initialState: RenderState,
    dispatcher: CoroutineDispatcher
) : ReaderController {

    protected val mutex: Mutex = Mutex()
    protected val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

    private val closed = AtomicBoolean(false)

    protected val eventsMutable = MutableSharedFlow<ReaderEvent>(extraBufferCapacity = 32)
    override val events: Flow<ReaderEvent> = eventsMutable.asSharedFlow()

    protected val stateMutable = MutableStateFlow(initialState)
    override val state: StateFlow<RenderState> = stateMutable.asStateFlow()

    protected fun launchSafely(
        name: String,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return scope.launch {
            try {
                block()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                onCoroutineError(name, t)
            }
        }
    }

    protected open fun onCoroutineError(name: String, throwable: Throwable) = Unit

    protected open fun onClose() = Unit

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { onClose() }
        scope.cancel()
    }
}
