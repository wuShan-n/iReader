package com.ireader.feature.reader.domain

import com.ireader.reader.api.render.RenderConfig
import kotlinx.coroutines.flow.Flow

interface ReaderSettingsRepository {
    val reflowConfig: Flow<RenderConfig.ReflowText>
    val fixedConfig: Flow<RenderConfig.FixedPage>

    suspend fun getReflowConfig(): RenderConfig.ReflowText
    suspend fun getFixedConfig(): RenderConfig.FixedPage

    suspend fun updateReflowConfig(config: RenderConfig.ReflowText)
    suspend fun updateFixedConfig(config: RenderConfig.FixedPage)
}

