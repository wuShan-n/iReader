package com.ireader.feature.reader.presentation

import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.TextLayouterFactory
import com.ireader.reader.runtime.ReaderSessionHandle

internal data class RenderPrereqSnapshot(
    val sessionHandle: ReaderSessionHandle,
    val openEpoch: Long,
    val layoutConstraints: LayoutConstraints,
    val textLayouterFactory: TextLayouterFactory
)

internal class RenderPrereqGate {
    private var sessionHandle: ReaderSessionHandle? = null
    private var sessionOpenEpoch: Long = -1L
    private var layoutConstraints: LayoutConstraints? = null
    private var textLayouterFactory: TextLayouterFactory? = null

    fun attachSession(handle: ReaderSessionHandle, openEpoch: Long) {
        sessionHandle = handle
        sessionOpenEpoch = openEpoch
    }

    fun clearSession() {
        sessionHandle = null
        sessionOpenEpoch = -1L
    }

    fun updateLayout(constraints: LayoutConstraints) {
        layoutConstraints = constraints
    }

    fun currentLayout(): LayoutConstraints? = layoutConstraints

    fun updateLayouter(factory: TextLayouterFactory) {
        textLayouterFactory = factory
    }

    fun currentLayouterFactory(): TextLayouterFactory? = textLayouterFactory

    fun snapshotIfReady(): RenderPrereqSnapshot? {
        val handle = sessionHandle ?: return null
        val layout = layoutConstraints ?: return null
        val layouter = textLayouterFactory ?: return null
        return RenderPrereqSnapshot(
            sessionHandle = handle,
            openEpoch = sessionOpenEpoch,
            layoutConstraints = layout,
            textLayouterFactory = layouter
        )
    }
}
