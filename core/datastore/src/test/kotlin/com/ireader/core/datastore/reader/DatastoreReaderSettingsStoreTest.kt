package com.ireader.core.datastore.reader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
            pagePaddingDp = 14f
        )

        store.setReflowConfig(config)

        val prefs = dataStore.data.first()
        assertEquals(21f, prefs[floatPreferencesKey("reader.reflow.fontSizeSp")])
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
