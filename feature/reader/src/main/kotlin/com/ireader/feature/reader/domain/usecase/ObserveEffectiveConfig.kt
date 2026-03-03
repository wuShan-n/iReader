package com.ireader.feature.reader.domain.usecase

import com.ireader.feature.reader.domain.ReaderSettingsRepository
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.DocumentCapabilities
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveEffectiveConfig @Inject constructor(
    private val settingsRepository: ReaderSettingsRepository
) {
    operator fun invoke(capabilities: DocumentCapabilities): Flow<RenderConfig> {
        return if (capabilities.fixedLayout) {
            settingsRepository.fixedConfig
        } else {
            settingsRepository.reflowConfig
        }
    }
}

