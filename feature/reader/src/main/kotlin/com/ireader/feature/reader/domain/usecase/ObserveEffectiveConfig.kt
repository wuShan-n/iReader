package com.ireader.feature.reader.domain.usecase

import com.ireader.core.data.reader.ReaderPreferencesRepository
import com.ireader.core.datastore.reader.ReaderSettingsStore
import com.ireader.reader.api.engine.DocumentCapabilities
import com.ireader.reader.api.render.RenderConfig
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

class ObserveEffectiveConfig @Inject constructor(
    private val preferencesRepository: ReaderPreferencesRepository
) {
    constructor(settingsStore: ReaderSettingsStore) : this(
        preferencesRepository = ReaderPreferencesRepository(settingsStore)
    )

    operator fun invoke(capabilities: DocumentCapabilities): Flow<RenderConfig> {
        return preferencesRepository.observeEffectiveConfig(capabilities)
            .distinctUntilChanged()
    }
}
