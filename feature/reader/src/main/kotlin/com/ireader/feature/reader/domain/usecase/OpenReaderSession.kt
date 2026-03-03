package com.ireader.feature.reader.domain.usecase

import com.ireader.core.datastore.reader.ReaderSettingsStore
import com.ireader.core.files.source.DocumentSource
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
    private val settings: ReaderSettingsStore
) {
    suspend operator fun invoke(
        source: DocumentSource,
        options: OpenOptions,
        initialLocator: Locator?,
    ): ReaderResult<ReaderSessionHandle> = withContext(Dispatchers.IO) {
        when (val documentResult = runtime.openDocument(source, options)) {
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
