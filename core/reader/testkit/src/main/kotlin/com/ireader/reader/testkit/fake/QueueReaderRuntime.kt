package com.ireader.reader.testkit.fake

import com.ireader.reader.api.engine.DocumentCapabilities
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.DocumentSource
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.Locator
import com.ireader.reader.runtime.BookProbeResult
import com.ireader.reader.runtime.ReaderHandle
import com.ireader.reader.runtime.ReaderRuntime

class QueueReaderRuntime(
    vararg sessionHandles: ReaderHandle
) : ReaderRuntime {
    private val sessions = ArrayDeque(sessionHandles.toList())

    override suspend fun openDocument(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<ReaderDocument> {
        return ReaderResult.Err(ReaderError.Internal("unused"))
    }

    override suspend fun openSession(
        source: DocumentSource,
        options: OpenOptions,
        initialLocator: Locator?,
        initialConfig: RenderConfig?,
        resolveInitialConfig: (suspend (DocumentCapabilities) -> RenderConfig)?
    ): ReaderResult<ReaderHandle> {
        if (sessions.isEmpty()) {
            return ReaderResult.Err(ReaderError.NotFound())
        }
        return ReaderResult.Ok(sessions.removeFirst())
    }

    override suspend fun probe(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<BookProbeResult> {
        return ReaderResult.Err(ReaderError.Internal("unused"))
    }
}
