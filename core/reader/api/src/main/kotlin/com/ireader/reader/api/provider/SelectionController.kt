package com.ireader.reader.api.provider

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.Locator

/**
 * Mutable selection API used by UI gesture layers (drag handles, cross-page expansion).
 *
 * - start/update: drive selection anchors from hit-tested locators
 * - finish: finalize gesture session
 * - clear: clear current selection state
 */
interface SelectionController {

    suspend fun start(locator: Locator): ReaderResult<Unit>

    suspend fun update(locator: Locator): ReaderResult<Unit>

    suspend fun finish(): ReaderResult<Unit>

    suspend fun clear(): ReaderResult<Unit>
}

