package com.ireader.feature.reader.domain.impl

import com.ireader.feature.reader.domain.ReaderSettingsRepository
import com.ireader.reader.api.render.RenderConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

@Singleton
class InMemoryReaderSettingsRepository @Inject constructor() : ReaderSettingsRepository {

    private val reflowState = MutableStateFlow(RenderConfig.ReflowText())
    private val fixedState = MutableStateFlow(RenderConfig.FixedPage())

    override val reflowConfig: Flow<RenderConfig.ReflowText> = reflowState
    override val fixedConfig: Flow<RenderConfig.FixedPage> = fixedState

    override suspend fun getReflowConfig(): RenderConfig.ReflowText = reflowState.first()

    override suspend fun getFixedConfig(): RenderConfig.FixedPage = fixedState.first()

    override suspend fun updateReflowConfig(config: RenderConfig.ReflowText) {
        reflowState.value = config
    }

    override suspend fun updateFixedConfig(config: RenderConfig.FixedPage) {
        fixedState.value = config
    }
}

