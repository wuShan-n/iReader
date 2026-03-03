package com.ireader.feature.reader.domain.usecase

import com.ireader.feature.reader.domain.ReaderBookInfo
import com.ireader.feature.reader.domain.ReaderSettingsRepository
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.Locator
import com.ireader.reader.runtime.ReaderRuntime
import com.ireader.reader.runtime.ReaderSessionHandle
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenReaderSession @Inject constructor(
    private val runtime: ReaderRuntime,
    private val settings: ReaderSettingsRepository
) {
    suspend operator fun invoke(
        bookInfo: ReaderBookInfo,
        initialLocator: Locator?,
        password: String?
    ): ReaderResult<ReaderSessionHandle> = withContext(Dispatchers.IO) {
        val options = OpenOptions(
            hintFormat = bookInfo.format,
            password = password
        )

        when (val documentResult = runtime.openDocument(bookInfo.source, options)) {
            is ReaderResult.Err -> documentResult
            is ReaderResult.Ok -> {
                val document = documentResult.value
                val config: RenderConfig = if (document.capabilities.fixedLayout) {
                    settings.getFixedConfig()
                } else {
                    settings.getReflowConfig()
                }

                when (val sessionResult = document.createSession(initialLocator = initialLocator, initialConfig = config)) {
                    is ReaderResult.Ok -> ReaderResult.Ok(
                        ReaderSessionHandle(
                            document = document,
                            session = sessionResult.value
                        )
                    )

                    is ReaderResult.Err -> {
                        runCatching { document.close() }
                        ReaderResult.Err(sessionResult.error)
                    }
                }
            }
        }
    }
}

