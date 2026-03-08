package com.ireader.feature.reader.domain.usecase

import com.ireader.core.data.reader.ReaderPreferencesRepository
import com.ireader.core.datastore.reader.ReaderBackgroundPreset
import com.ireader.core.datastore.reader.ReaderDisplayPrefs
import com.ireader.core.datastore.reader.ReaderSettingsStore
import com.ireader.reader.api.render.READER_APPEARANCE_BG_ARGB_EXTRA_KEY
import com.ireader.reader.api.render.READER_APPEARANCE_TEXT_ARGB_EXTRA_KEY
import com.ireader.reader.api.render.READER_APPEARANCE_THEME_DARK
import com.ireader.reader.api.render.READER_APPEARANCE_THEME_EXTRA_KEY
import com.ireader.reader.api.render.READER_APPEARANCE_THEME_LIGHT
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.engine.DocumentCapabilities
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveEffectiveConfigTest {

    @Test
    fun `reflow config should update appearance when display preferences change`() = runTest {
        val settingsStore = MutableReaderSettingsStore(
            initialReflow = RenderConfig.ReflowText(fontSizeSp = 22f),
            initialDisplay = ReaderDisplayPrefs(
                nightMode = false,
                backgroundPreset = ReaderBackgroundPreset.SYSTEM
            )
        )
        val useCase = ObserveEffectiveConfig(
            preferencesRepository = ReaderPreferencesRepository(settingsStore)
        )
        val emissions = mutableListOf<RenderConfig>()

        val job = launch {
            useCase(reflowCapabilities())
                .take(2)
                .toList(emissions)
        }
        advanceUntilIdle()
        settingsStore.displayState.value = settingsStore.displayState.value.copy(
            backgroundPreset = ReaderBackgroundPreset.WARM
        )
        advanceUntilIdle()
        job.join()

        val first = emissions[0] as RenderConfig.ReflowText
        val second = emissions[1] as RenderConfig.ReflowText
        assertEquals(0xFFFDF9F3.toInt().toString(), first.extra[READER_APPEARANCE_BG_ARGB_EXTRA_KEY])
        assertEquals(0xFFF3E7CA.toInt().toString(), second.extra[READER_APPEARANCE_BG_ARGB_EXTRA_KEY])
        assertEquals(READER_APPEARANCE_THEME_LIGHT, second.extra[READER_APPEARANCE_THEME_EXTRA_KEY])
    }

    @Test
    fun `fixed config should map dark appearance extras`() = runTest {
        val settingsStore = MutableReaderSettingsStore(
            initialFixed = RenderConfig.FixedPage(zoom = 1.4f),
            initialDisplay = ReaderDisplayPrefs(
                nightMode = true,
                backgroundPreset = ReaderBackgroundPreset.SYSTEM
            )
        )
        val useCase = ObserveEffectiveConfig(
            preferencesRepository = ReaderPreferencesRepository(settingsStore)
        )

        val config = useCase(fixedCapabilities()).take(1).toList().single() as RenderConfig.FixedPage
        assertEquals(0xFF131313.toInt().toString(), config.extra[READER_APPEARANCE_BG_ARGB_EXTRA_KEY])
        assertEquals(0xFFBEB9B0.toInt().toString(), config.extra[READER_APPEARANCE_TEXT_ARGB_EXTRA_KEY])
        assertEquals(READER_APPEARANCE_THEME_DARK, config.extra[READER_APPEARANCE_THEME_EXTRA_KEY])
    }

    private fun fixedCapabilities() = DocumentCapabilities(
        reflowable = false,
        fixedLayout = true,
        outline = false,
        search = false,
        textExtraction = false,
        annotations = false,
        selection = false,
        links = false
    )

    private fun reflowCapabilities() = fixedCapabilities().copy(
        reflowable = true,
        fixedLayout = false
    )
}

private class MutableReaderSettingsStore(
    initialReflow: RenderConfig.ReflowText = RenderConfig.ReflowText(),
    initialFixed: RenderConfig.FixedPage = RenderConfig.FixedPage(),
    initialDisplay: ReaderDisplayPrefs = ReaderDisplayPrefs()
) : ReaderSettingsStore {
    val reflowState = MutableStateFlow(initialReflow)
    val fixedState = MutableStateFlow(initialFixed)
    val displayState = MutableStateFlow(initialDisplay)

    override val reflowConfig: Flow<RenderConfig.ReflowText> = reflowState
    override val fixedConfig: Flow<RenderConfig.FixedPage> = fixedState
    override val displayPrefs: Flow<ReaderDisplayPrefs> = displayState

    override suspend fun getReflowConfig(): RenderConfig.ReflowText = reflowState.value

    override suspend fun getFixedConfig(): RenderConfig.FixedPage = fixedState.value

    override suspend fun getDisplayPrefs(): ReaderDisplayPrefs = displayState.value

    override suspend fun setReflowConfig(config: RenderConfig.ReflowText) {
        reflowState.value = config
    }

    override suspend fun setFixedConfig(config: RenderConfig.FixedPage) {
        fixedState.value = config
    }

    override suspend fun setDisplayPrefs(prefs: ReaderDisplayPrefs) {
        displayState.value = prefs
    }
}
