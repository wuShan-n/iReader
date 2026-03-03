package com.ireader.core.datastore.reader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ireader.reader.api.render.RenderConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class DatastoreReaderSettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ReaderSettingsStore {

    private object Keys {
        object Reflow {
            val fontSizeSp = floatPreferencesKey("reader.reflow.fontSizeSp")
            val lineHeightMult = floatPreferencesKey("reader.reflow.lineHeightMult")
            val paragraphSpacingDp = floatPreferencesKey("reader.reflow.paragraphSpacingDp")
            val pagePaddingDp = floatPreferencesKey("reader.reflow.pagePaddingDp")
            val fontFamilyName = stringPreferencesKey("reader.reflow.fontFamilyName")
            val hyphenation = booleanPreferencesKey("reader.reflow.hyphenation")
        }

        object Fixed {
            val fitMode = stringPreferencesKey("reader.fixed.fitMode")
            val zoom = floatPreferencesKey("reader.fixed.zoom")
            val rotationDegrees = intPreferencesKey("reader.fixed.rotationDegrees")
        }
    }

    private val defaultReflow = RenderConfig.ReflowText()
    private val defaultFixed = RenderConfig.FixedPage()

    override val reflowConfig: Flow<RenderConfig.ReflowText> =
        dataStore.data.map { prefs ->
            defaultReflow.copy(
                fontSizeSp = prefs[Keys.Reflow.fontSizeSp] ?: defaultReflow.fontSizeSp,
                lineHeightMult = prefs[Keys.Reflow.lineHeightMult] ?: defaultReflow.lineHeightMult,
                paragraphSpacingDp = prefs[Keys.Reflow.paragraphSpacingDp] ?: defaultReflow.paragraphSpacingDp,
                pagePaddingDp = prefs[Keys.Reflow.pagePaddingDp] ?: defaultReflow.pagePaddingDp,
                fontFamilyName = prefs[Keys.Reflow.fontFamilyName] ?: defaultReflow.fontFamilyName,
                hyphenation = prefs[Keys.Reflow.hyphenation] ?: defaultReflow.hyphenation
            )
        }.distinctUntilChanged()

    override val fixedConfig: Flow<RenderConfig.FixedPage> =
        dataStore.data.map { prefs ->
            val fitName = prefs[Keys.Fixed.fitMode] ?: defaultFixed.fitMode.name
            val fitMode = runCatching { RenderConfig.FitMode.valueOf(fitName) }
                .getOrElse { defaultFixed.fitMode }

            defaultFixed.copy(
                fitMode = fitMode,
                zoom = prefs[Keys.Fixed.zoom] ?: defaultFixed.zoom,
                rotationDegrees = prefs[Keys.Fixed.rotationDegrees] ?: defaultFixed.rotationDegrees
            )
        }.distinctUntilChanged()

    override suspend fun getReflowConfig(): RenderConfig.ReflowText = reflowConfig.first()

    override suspend fun getFixedConfig(): RenderConfig.FixedPage = fixedConfig.first()

    override suspend fun setReflowConfig(config: RenderConfig.ReflowText) {
        dataStore.edit { prefs ->
            prefs[Keys.Reflow.fontSizeSp] = config.fontSizeSp
            prefs[Keys.Reflow.lineHeightMult] = config.lineHeightMult
            prefs[Keys.Reflow.paragraphSpacingDp] = config.paragraphSpacingDp
            prefs[Keys.Reflow.pagePaddingDp] = config.pagePaddingDp
            val fontFamilyName = config.fontFamilyName
            if (fontFamilyName.isNullOrBlank()) {
                prefs.remove(Keys.Reflow.fontFamilyName)
            } else {
                prefs[Keys.Reflow.fontFamilyName] = fontFamilyName
            }
            prefs[Keys.Reflow.hyphenation] = config.hyphenation
        }
    }

    override suspend fun setFixedConfig(config: RenderConfig.FixedPage) {
        dataStore.edit { prefs ->
            prefs[Keys.Fixed.fitMode] = config.fitMode.name
            prefs[Keys.Fixed.zoom] = config.zoom
            prefs[Keys.Fixed.rotationDegrees] = config.rotationDegrees
        }
    }
}
