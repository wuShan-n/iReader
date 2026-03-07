package com.ireader.reader.api.engine

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.model.Locator

enum class TextBreakPatchDirection {
    PREVIOUS,
    NEXT
}

enum class TextBreakPatchState {
    HARD_PARAGRAPH,
    SOFT_JOIN,
    SOFT_SPACE,
    PRESERVE,
    UNKNOWN
}

interface TextBreakPatchSupport {
    suspend fun applyBreakPatch(
        locator: Locator,
        direction: TextBreakPatchDirection,
        state: TextBreakPatchState
    ): ReaderResult<RenderPage>

    suspend fun clearBreakPatches(): ReaderResult<RenderPage>
}
