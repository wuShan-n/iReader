package com.ireader.feature.reader.domain.usecase

import com.ireader.core.data.reader.ReaderPreferencesRepository
import com.ireader.core.data.reader.withReaderAppearance
import com.ireader.core.datastore.reader.ReaderSettingsStore
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.DocumentSource
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.Locator
import com.ireader.reader.model.BookFormat
import com.ireader.reader.runtime.ReaderHandle
import com.ireader.reader.runtime.ReaderRuntime
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenReaderSession @Inject constructor(
    val runtime: ReaderRuntime,
    val preferencesRepository: ReaderPreferencesRepository
) {
    constructor(
        runtime: ReaderRuntime,
        settings: ReaderSettingsStore
    ) : this(
        runtime = runtime,
        preferencesRepository = ReaderPreferencesRepository(settings)
    )

    suspend operator fun invoke(
        source: DocumentSource,
        options: OpenOptions,
        initialLocator: Locator?,
    ): ReaderResult<ReaderHandle> = withContext(Dispatchers.IO) {
        val snapshot = preferencesRepository.getOpenSettingsSnapshot()
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
