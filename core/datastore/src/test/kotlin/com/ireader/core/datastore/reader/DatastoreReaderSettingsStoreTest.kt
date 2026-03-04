package com.ireader.core.datastore.reader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ireader.reader.api.render.HyphenationMode
import com.ireader.reader.api.render.PAGE_TURN_EXTRA_KEY
import com.ireader.reader.api.render.PAGE_TURN_STYLE_EXTRA_KEY
import com.ireader.reader.api.render.RenderConfig
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DatastoreReaderSettingsStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    @Test
    fun `setReflow should write camel-case keys`() = runTest {
        val dataStore = createDataStore(
            scope = this,
            testFile = File(temporaryFolder.root, "reader_settings.preferences_pb")
        )
        val store = DatastoreReaderSettingsStore(dataStore)
        val config = RenderConfig.ReflowText(
            fontSizeSp = 21f,
            lineHeightMult = 1.7f,
            paragraphSpacingDp = 8f,
            paragraphIndentEm = 2f,
            pagePaddingDp = 14f,
            respectPublisherStyles = true,
            hyphenationMode = HyphenationMode.FULL
        )

        store.setReflowConfig(config)

        val prefs = dataStore.data.first()
        assertEquals(21f, prefs[floatPreferencesKey("reader.reflow.fontSizeSp")])
        assertEquals(2f, prefs[floatPreferencesKey("reader.reflow.paragraphIndentEm")])
        assertEquals("FULL", prefs[stringPreferencesKey("reader.reflow.hyphenationMode")])
        assertEquals(true, prefs[booleanPreferencesKey("reader.reflow.respectPublisherStyles")])
        assertNull(prefs[floatPreferencesKey("reader.reflow.font_size_sp")])
    }

    @Test
    fun `invalid fit mode should fallback to default`() = runTest {
        val dataStore = createDataStore(
            scope = this,
            testFile = File(temporaryFolder.root, "reader_settings_fit.preferences_pb")
        )
        val store = DatastoreReaderSettingsStore(dataStore)

        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("reader.fixed.fitMode")] = "INVALID"
        }

        val config = store.getFixedConfig()
        assertEquals(RenderConfig.FixedPage().fitMode, config.fitMode)
    }

    @Test
    fun `setReflow should persist cover page turn mode and recover in flow`() = runTest {
        val dataStore = createDataStore(
            scope = this,
            testFile = File(temporaryFolder.root, "reader_settings_page_turn.preferences_pb")
        )
        val store = DatastoreReaderSettingsStore(dataStore)
        val config = RenderConfig.ReflowText(
            extra = mapOf(
                PAGE_TURN_EXTRA_KEY to "cover_horizontal"
            )
        )

        store.setReflowConfig(config)

        val prefs = dataStore.data.first()
        assertEquals("cover_horizontal", prefs[stringPreferencesKey("reader.reflow.pageTurnMode")])
        assertEquals("cover_overlay", prefs[stringPreferencesKey("reader.reflow.pageTurnStyle")])
        assertEquals("cover_horizontal", store.getReflowConfig().extra[PAGE_TURN_EXTRA_KEY])
        assertEquals("cover_overlay", store.getReflowConfig().extra[PAGE_TURN_STYLE_EXTRA_KEY])
    }

    @Test
    fun `setReflow should persist canonical page turn style and recover in flow`() = runTest {
        val dataStore = createDataStore(
            scope = this,
            testFile = File(temporaryFolder.root, "reader_settings_page_turn_style.preferences_pb")
        )
        val store = DatastoreReaderSettingsStore(dataStore)
        val config = RenderConfig.ReflowText(
            extra = mapOf(
                PAGE_TURN_EXTRA_KEY to "cover_horizontal",
                PAGE_TURN_STYLE_EXTRA_KEY to "no_animation"
            )
        )

        store.setReflowConfig(config)

        val prefs = dataStore.data.first()
        assertEquals("cover_horizontal", prefs[stringPreferencesKey("reader.reflow.pageTurnMode")])
        assertEquals("no_animation", prefs[stringPreferencesKey("reader.reflow.pageTurnStyle")])
        assertEquals("cover_horizontal", store.getReflowConfig().extra[PAGE_TURN_EXTRA_KEY])
        assertEquals("no_animation", store.getReflowConfig().extra[PAGE_TURN_STYLE_EXTRA_KEY])
    }

    @Test
    fun `legacy page turn mode without style should fallback to default style`() = runTest {
        val dataStore = createDataStore(
            scope = this,
            testFile = File(temporaryFolder.root, "reader_settings_page_turn_legacy.preferences_pb")
        )
        val store = DatastoreReaderSettingsStore(dataStore)

        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("reader.reflow.pageTurnMode")] = "cover_horizontal"
        }

        val config = store.getReflowConfig()
        assertEquals("cover_horizontal", config.extra[PAGE_TURN_EXTRA_KEY])
        assertEquals("cover_overlay", config.extra[PAGE_TURN_STYLE_EXTRA_KEY])
    }

    @Test
    fun `display prefs should round-trip in datastore`() = runTest {
        val dataStore = createDataStore(
            scope = this,
            testFile = File(temporaryFolder.root, "reader_display.preferences_pb")
        )
        val store = DatastoreReaderSettingsStore(dataStore)
        val prefs = ReaderDisplayPrefs(
            brightness = 0.72f,
            useSystemBrightness = false,
            eyeProtection = true,
            nightMode = true,
            backgroundPreset = ReaderBackgroundPreset.NAVY,
            showReadingProgress = false,
            fullScreenMode = false,
            volumeKeyPagingEnabled = false,
            tapZonePreset = TapZonePreset.LEFT_HAND,
            preventAccidentalTurn = false
        )

        store.setDisplayPrefs(prefs)

        assertEquals(prefs, store.getDisplayPrefs())
    }

    private fun createDataStore(scope: TestScope, testFile: File): DataStore<Preferences> {
        if (testFile.exists()) {
            check(testFile.delete()) { "Unable to delete existing test datastore file: $testFile" }
        }
        return PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { testFile }
        )
    }
}
