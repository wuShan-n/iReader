package com.ireader.reader.api.render

import androidx.fragment.app.Fragment
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.Locator
import java.io.Closeable
import kotlinx.coroutines.flow.StateFlow

/**
 * Adapter for navigator-first engines (eg. Readium EPUB) which render with a Fragment.
 */
interface ReaderNavigatorAdapter : Closeable {
    fun createFragment(): Fragment

    val locatorFlow: StateFlow<Locator?>

    suspend fun goTo(locator: Locator): ReaderResult<Unit>

    suspend fun submitConfig(config: RenderConfig.ReflowText): ReaderResult<Unit>

    suspend fun next(): ReaderResult<Unit>

    suspend fun prev(): ReaderResult<Unit>
}
