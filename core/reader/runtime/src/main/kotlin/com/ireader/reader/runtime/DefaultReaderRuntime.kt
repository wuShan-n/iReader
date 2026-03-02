package com.ireader.reader.runtime

import com.ireader.reader.api.engine.EngineRegistry
import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.Locator
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.render.RenderConfig
import com.ireader.core.files.source.DocumentSource
import com.ireader.reader.runtime.error.toReaderError
import com.ireader.reader.runtime.format.BookFormatDetector
import com.ireader.reader.runtime.format.DefaultBookFormatDetector
import com.ireader.reader.runtime.render.RenderDefaults

class DefaultReaderRuntime(
    private val engineRegistry: EngineRegistry,
    private val formatDetector: BookFormatDetector = DefaultBookFormatDetector()
) : ReaderRuntime {

    override suspend fun openDocument(
        source: DocumentSource,
        options: OpenOptions
    ): ReaderResult<ReaderDocument> {
        val formatResult = formatDetector.detect(source, options.hintFormat)
        return when (formatResult) {
            is ReaderResult.Ok -> openWithFormat(source, options, formatResult.value)
            is ReaderResult.Err -> formatResult
        }
    }

    override suspend fun openSession(
        source: DocumentSource,
        options: OpenOptions,
        initialLocator: Locator?,
        initialConfig: RenderConfig?
    ): ReaderResult<ReaderSessionHandle> {
        val docResult = openDocument(source, options)
        return when (docResult) {
            is ReaderResult.Err -> docResult
            is ReaderResult.Ok -> {
                val document = docResult.value
                val config = initialConfig ?: RenderDefaults.configFor(document.capabilities)

                val sessionResult = catchingSuspend { document.createSession(initialLocator, config) }
                when (sessionResult) {
                    is ReaderResult.Ok -> ReaderResult.Ok(ReaderSessionHandle(document, sessionResult.value))
                    is ReaderResult.Err -> {
                        // session 创建失败要关 document，避免泄露
                        runCatching { document.close() }
                        ReaderResult.Err(sessionResult.error)
                    }
                }
            }
        }
    }

    private suspend fun openWithFormat(
        source: DocumentSource,
        options: OpenOptions,
        format: BookFormat
    ): ReaderResult<ReaderDocument> {
        val engine = engineRegistry.engineFor(format)
            ?: return ReaderResult.Err(ReaderError.UnsupportedFormat(detected = format.name))

        // 防御：引擎实现里如果 throw，这里统一收敛成 ReaderError
        return catchingSuspend { engine.open(source, options) }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend inline fun <T> catchingSuspend(
        crossinline block: suspend () -> ReaderResult<T>
    ): ReaderResult<T> {
        return try {
            block()
        } catch (t: Throwable) {
            ReaderResult.Err(t.toReaderError())
        }
    }
}


