package com.ireader.feature.reader.domain.usecase

import com.ireader.core.datastore.reader.ReaderSettingsStore
import com.ireader.feature.reader.domain.appearance.withReaderAppearance
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.DocumentCapabilities
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ObserveEffectiveConfig @Inject constructor(
    private val settingsStore: ReaderSettingsStore
) {
    operator fun invoke(capabilities: DocumentCapabilities): Flow<RenderConfig> {
        val configFlow: Flow<RenderConfig> = if (capabilities.fixedLayout) {
            settingsStore.fixedConfig.map { it }
        } else {
            settingsStore.reflowConfig.map { it }
        }
        return combine(configFlow, settingsStore.displayPrefs) { config, displayPrefs ->
            config.withReaderAppearance(displayPrefs)
        }.distinctUntilChanged()
    }
}
