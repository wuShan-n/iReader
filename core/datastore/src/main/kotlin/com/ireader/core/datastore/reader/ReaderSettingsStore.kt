package com.ireader.core.datastore.reader

import com.ireader.reader.api.render.RenderConfig
import kotlinx.coroutines.flow.Flow

interface ReaderSettingsStore {
    val reflowConfig: Flow<RenderConfig.ReflowText>
    val fixedConfig: Flow<RenderConfig.FixedPage>

    suspend fun getReflowConfig(): RenderConfig.ReflowText
    suspend fun getFixedConfig(): RenderConfig.FixedPage

    suspend fun setReflowConfig(config: RenderConfig.ReflowText)
    suspend fun setFixedConfig(config: RenderConfig.FixedPage)
}
