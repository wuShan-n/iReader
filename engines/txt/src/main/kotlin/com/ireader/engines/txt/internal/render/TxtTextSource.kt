package com.ireader.engines.txt.internal.render

import com.ireader.engines.common.android.reflow.ReflowTextSource
import com.ireader.engines.txt.internal.store.Utf16TextStore

internal class TxtTextSource(
    private val store: Utf16TextStore
) : ReflowTextSource {
    override val lengthChars: Long
        get() = store.lengthChars

    override fun readString(start: Long, count: Int): String {
        return store.readString(start, count)
    }
}

