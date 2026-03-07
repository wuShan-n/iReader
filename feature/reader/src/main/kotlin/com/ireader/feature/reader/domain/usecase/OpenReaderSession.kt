package com.ireader.feature.reader.domain.usecase

import com.ireader.core.datastore.reader.ReaderSettingsStore
import com.ireader.core.files.source.DocumentSource
import com.ireader.feature.reader.domain.appearance.withReaderAppearance
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.Locator
import com.ireader.reader.model.BookFormat
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
        val snapshot = settings.getOpenSettingsSnapshot()
        val txtInitialConfig = if (options.hintFormat == BookFormat.TXT) {
            snapshot.reflowConfig.withReaderAppearance(snapshot.displayPrefs)
        } else {
            null
        }
        runtime.openSession(
            source = source,
            options = options,
            initialLocator = initialLocator,
            initialConfig = txtInitialConfig,
            resolveInitialConfig = if (txtInitialConfig == null) {
                { capabilities ->
                    if (capabilities.fixedLayout) {
                        snapshot.fixedConfig.withReaderAppearance(snapshot.displayPrefs)
                    } else {
                        snapshot.reflowConfig.withReaderAppearance(snapshot.displayPrefs)
                    }
                }
            } else {
                null
            }
        )
    }
}
