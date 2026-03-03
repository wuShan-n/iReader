package com.ireader.feature.reader.domain.usecase

import com.ireader.core.datastore.reader.ReaderSettingsStore
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.DocumentCapabilities
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveEffectiveConfig @Inject constructor(
    private val settingsStore: ReaderSettingsStore
) {
    operator fun invoke(capabilities: DocumentCapabilities): Flow<RenderConfig> {
        return if (capabilities.fixedLayout) {
            settingsStore.fixedConfig
        } else {
            settingsStore.reflowConfig
        }
    }
}
