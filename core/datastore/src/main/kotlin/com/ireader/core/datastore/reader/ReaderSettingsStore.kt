package com.ireader.core.datastore.reader

import com.ireader.reader.api.render.RenderConfig
import kotlinx.coroutines.flow.Flow

data class ReaderOpenSettingsSnapshot(
    val reflowConfig: RenderConfig.ReflowText,
    val fixedConfig: RenderConfig.FixedPage,
    val displayPrefs: ReaderDisplayPrefs
)

interface ReaderSettingsStore {
    val reflowConfig: Flow<RenderConfig.ReflowText>
    val fixedConfig: Flow<RenderConfig.FixedPage>
    val displayPrefs: Flow<ReaderDisplayPrefs>

    suspend fun getReflowConfig(): RenderConfig.ReflowText
    suspend fun getFixedConfig(): RenderConfig.FixedPage
    suspend fun getDisplayPrefs(): ReaderDisplayPrefs
    suspend fun getOpenSettingsSnapshot(): ReaderOpenSettingsSnapshot {
        return ReaderOpenSettingsSnapshot(
            reflowConfig = getReflowConfig(),
            fixedConfig = getFixedConfig(),
            displayPrefs = getDisplayPrefs()
        )
    }

    suspend fun setReflowConfig(config: RenderConfig.ReflowText)
    suspend fun setFixedConfig(config: RenderConfig.FixedPage)
    suspend fun setDisplayPrefs(prefs: ReaderDisplayPrefs)
}
