package com.ireader.core.data.reader

import com.ireader.core.datastore.reader.ReaderDisplayPrefs
import com.ireader.core.datastore.reader.ReaderOpenSettingsSnapshot
import com.ireader.core.datastore.reader.ReaderSettingsStore
import com.ireader.reader.api.engine.DocumentCapabilities
import com.ireader.reader.api.render.RenderConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class ReaderPreferencesRepository @Inject constructor(
    private val settingsStore: ReaderSettingsStore
) {
    val displayPrefs: Flow<ReaderDisplayPrefs> = settingsStore.displayPrefs

    fun observeEffectiveConfig(capabilities: DocumentCapabilities): Flow<RenderConfig> {
        val configFlow: Flow<RenderConfig> = if (capabilities.fixedLayout) {
            settingsStore.fixedConfig.map { it }
        } else {
            settingsStore.reflowConfig.map { it }
        }
        return combine(configFlow, settingsStore.displayPrefs) { config, displayPrefs ->
            config.withReaderAppearance(displayPrefs)
        }
    }

    suspend fun getOpenSettingsSnapshot(): ReaderOpenSettingsSnapshot {
        return settingsStore.getOpenSettingsSnapshot()
    }

    suspend fun saveConfig(config: RenderConfig) {
        when (config) {
            is RenderConfig.FixedPage -> settingsStore.setFixedConfig(config)
            is RenderConfig.ReflowText -> settingsStore.setReflowConfig(config)
        }
    }

    suspend fun updateDisplayPrefs(prefs: ReaderDisplayPrefs) {
        settingsStore.setDisplayPrefs(prefs)
    }
}
