package com.ireader.engines.txt.internal.render

import com.ireader.engines.common.android.reflow.ReflowTextSource
import com.ireader.engines.common.android.reflow.ReflowTextWindow
import com.ireader.engines.txt.internal.softbreak.SoftBreakIndex
import com.ireader.engines.txt.internal.store.Utf16TextStore

internal class TxtTextSource(
    private val store: Utf16TextStore,
    private val breakIndexProvider: () -> SoftBreakIndex? = { null }
) : ReflowTextSource {
    override val lengthChars: Long
        get() = store.lengthChars

    override fun readString(start: Long, count: Int): String {
        return store.readString(start, count)
    }

    override fun readWindow(start: Long, count: Int): ReflowTextWindow {
        val raw = store.readString(start, count)
        return TxtTextProjector.projectWindow(
            rawText = raw,
            startOffset = start.coerceAtLeast(0L),
            breakIndex = breakIndexProvider()
        )
    }
}
