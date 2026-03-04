package com.ireader.core.datastore.reader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ireader.reader.api.render.BreakStrategyMode
import com.ireader.reader.api.render.HyphenationMode
import com.ireader.reader.api.render.PageInsetMode
import com.ireader.reader.api.render.PageTurnMode
import com.ireader.reader.api.render.PAGE_TURN_EXTRA_KEY
import com.ireader.reader.api.render.PAGE_TURN_STYLE_EXTRA_KEY
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.TextAlignMode
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
            val paragraphIndentEm = floatPreferencesKey("reader.reflow.paragraphIndentEm")
            val pagePaddingDp = floatPreferencesKey("reader.reflow.pagePaddingDp")
            val fontFamilyName = stringPreferencesKey("reader.reflow.fontFamilyName")
            val textAlign = stringPreferencesKey("reader.reflow.textAlign")
            val breakStrategy = stringPreferencesKey("reader.reflow.breakStrategy")
            val hyphenationMode = stringPreferencesKey("reader.reflow.hyphenationMode")
            val includeFontPadding = booleanPreferencesKey("reader.reflow.includeFontPadding")
            val cjkLineBreakStrict = booleanPreferencesKey("reader.reflow.cjkLineBreakStrict")
            val hangingPunctuation = booleanPreferencesKey("reader.reflow.hangingPunctuation")
            val pageInsetMode = stringPreferencesKey("reader.reflow.pageInsetMode")
            val pageTurnMode = stringPreferencesKey("reader.reflow.pageTurnMode")
            val pageTurnStyle = stringPreferencesKey("reader.reflow.pageTurnStyle")
            val respectPublisherStyles = booleanPreferencesKey("reader.reflow.respectPublisherStyles")

            // Backward-compat key. Removed after all users migrate to hyphenationMode.
            val legacyHyphenation = booleanPreferencesKey("reader.reflow.hyphenation")
        }

        object Fixed {
            val fitMode = stringPreferencesKey("reader.fixed.fitMode")
            val zoom = floatPreferencesKey("reader.fixed.zoom")
            val rotationDegrees = intPreferencesKey("reader.fixed.rotationDegrees")
        }

        object Display {
            val brightness = floatPreferencesKey("reader.display.brightness")
            val useSystemBrightness = booleanPreferencesKey("reader.display.useSystemBrightness")
            val eyeProtection = booleanPreferencesKey("reader.display.eyeProtection")
            val nightMode = booleanPreferencesKey("reader.display.nightMode")
            val backgroundPreset = stringPreferencesKey("reader.display.backgroundPreset")
            val showReadingProgress = booleanPreferencesKey("reader.display.showReadingProgress")
            val fullScreenMode = booleanPreferencesKey("reader.display.fullScreenMode")
            val volumeKeyPagingEnabled = booleanPreferencesKey("reader.display.volumeKeyPagingEnabled")
            val tapZonePreset = stringPreferencesKey("reader.display.tapZonePreset")
            val preventAccidentalTurn = booleanPreferencesKey("reader.display.preventAccidentalTurn")
        }
    }

    private val defaultReflow = RenderConfig.ReflowText(
        fontSizeSp = 20f,
        lineHeightMult = 1.85f,
        paragraphSpacingDp = 10f,
        paragraphIndentEm = 2.0f,
        pagePaddingDp = 20f,
        fontFamilyName = "serif",
        textAlign = TextAlignMode.JUSTIFY,
        breakStrategy = BreakStrategyMode.BALANCED,
        hyphenationMode = HyphenationMode.NORMAL,
        includeFontPadding = false,
        cjkLineBreakStrict = true,
        hangingPunctuation = false,
        pageInsetMode = PageInsetMode.RELAXED,
        respectPublisherStyles = false,
        extra = mapOf(
            PAGE_TURN_EXTRA_KEY to PageTurnMode.COVER_HORIZONTAL.storageValue,
            PAGE_TURN_STYLE_EXTRA_KEY to STYLE_COVER_OVERLAY
        )
    )
    private val defaultFixed = RenderConfig.FixedPage()
    private val defaultDisplay = ReaderDisplayPrefs()

    override val reflowConfig: Flow<RenderConfig.ReflowText> =
        dataStore.data.map { prefs ->
            val textAlign = prefs[Keys.Reflow.textAlign]
                ?.let(::parseTextAlignMode)
                ?: defaultReflow.textAlign
            val breakStrategy = prefs[Keys.Reflow.breakStrategy]
                ?.let(::parseBreakStrategyMode)
                ?: defaultReflow.breakStrategy
            val hyphenationMode = prefs[Keys.Reflow.hyphenationMode]
                ?.let(::parseHyphenationMode)
                ?: prefs[Keys.Reflow.legacyHyphenation]
                    ?.let { if (it) HyphenationMode.NORMAL else HyphenationMode.NONE }
                ?: defaultReflow.hyphenationMode
            val includeFontPadding = prefs[Keys.Reflow.includeFontPadding]
                ?: defaultReflow.includeFontPadding
            val pageInsetMode = prefs[Keys.Reflow.pageInsetMode]
                ?.let(::parsePageInsetMode)
                ?: defaultReflow.pageInsetMode
            val pageTurnMode = PageTurnMode.fromStorageValue(
                prefs[Keys.Reflow.pageTurnMode]
            )
            val pageTurnStyle = normalizePageTurnStyleRaw(
                raw = prefs[Keys.Reflow.pageTurnStyle]
            )

            defaultReflow.copy(
                fontSizeSp = prefs[Keys.Reflow.fontSizeSp] ?: defaultReflow.fontSizeSp,
                lineHeightMult = prefs[Keys.Reflow.lineHeightMult] ?: defaultReflow.lineHeightMult,
                paragraphSpacingDp = prefs[Keys.Reflow.paragraphSpacingDp] ?: defaultReflow.paragraphSpacingDp,
                paragraphIndentEm = prefs[Keys.Reflow.paragraphIndentEm] ?: defaultReflow.paragraphIndentEm,
                pagePaddingDp = prefs[Keys.Reflow.pagePaddingDp] ?: defaultReflow.pagePaddingDp,
                fontFamilyName = prefs[Keys.Reflow.fontFamilyName] ?: defaultReflow.fontFamilyName,
                textAlign = textAlign,
                breakStrategy = breakStrategy,
                hyphenationMode = hyphenationMode,
                includeFontPadding = includeFontPadding,
                cjkLineBreakStrict = prefs[Keys.Reflow.cjkLineBreakStrict] ?: defaultReflow.cjkLineBreakStrict,
                hangingPunctuation = prefs[Keys.Reflow.hangingPunctuation] ?: defaultReflow.hangingPunctuation,
                pageInsetMode = pageInsetMode,
                respectPublisherStyles = prefs[Keys.Reflow.respectPublisherStyles]
                    ?: defaultReflow.respectPublisherStyles,
                extra = defaultReflow.extra +
                    (PAGE_TURN_EXTRA_KEY to pageTurnMode.storageValue) +
                    (PAGE_TURN_STYLE_EXTRA_KEY to pageTurnStyle)
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

    override val displayPrefs: Flow<ReaderDisplayPrefs> =
        dataStore.data.map { prefs ->
            ReaderDisplayPrefs(
                brightness = (prefs[Keys.Display.brightness] ?: defaultDisplay.brightness)
                    .coerceIn(0f, 1f),
                useSystemBrightness = prefs[Keys.Display.useSystemBrightness]
                    ?: defaultDisplay.useSystemBrightness,
                eyeProtection = prefs[Keys.Display.eyeProtection]
                    ?: defaultDisplay.eyeProtection,
                nightMode = prefs[Keys.Display.nightMode] ?: defaultDisplay.nightMode,
                backgroundPreset = ReaderBackgroundPreset.fromStorageValue(
                    prefs[Keys.Display.backgroundPreset]
                ),
                showReadingProgress = prefs[Keys.Display.showReadingProgress]
                    ?: defaultDisplay.showReadingProgress,
                fullScreenMode = prefs[Keys.Display.fullScreenMode]
                    ?: defaultDisplay.fullScreenMode,
                volumeKeyPagingEnabled = prefs[Keys.Display.volumeKeyPagingEnabled]
                    ?: defaultDisplay.volumeKeyPagingEnabled,
                tapZonePreset = TapZonePreset.fromStorageValue(
                    prefs[Keys.Display.tapZonePreset]
                ),
                preventAccidentalTurn = prefs[Keys.Display.preventAccidentalTurn]
                    ?: defaultDisplay.preventAccidentalTurn
            )
        }.distinctUntilChanged()

    override suspend fun getReflowConfig(): RenderConfig.ReflowText = reflowConfig.first()

    override suspend fun getFixedConfig(): RenderConfig.FixedPage = fixedConfig.first()
    override suspend fun getDisplayPrefs(): ReaderDisplayPrefs = displayPrefs.first()

    override suspend fun setReflowConfig(config: RenderConfig.ReflowText) {
        dataStore.edit { prefs ->
            prefs[Keys.Reflow.fontSizeSp] = config.fontSizeSp
            prefs[Keys.Reflow.lineHeightMult] = config.lineHeightMult
            prefs[Keys.Reflow.paragraphSpacingDp] = config.paragraphSpacingDp
            prefs[Keys.Reflow.paragraphIndentEm] = config.paragraphIndentEm
            prefs[Keys.Reflow.pagePaddingDp] = config.pagePaddingDp
            val fontFamilyName = config.fontFamilyName
            if (fontFamilyName.isNullOrBlank()) {
                prefs.remove(Keys.Reflow.fontFamilyName)
            } else {
                prefs[Keys.Reflow.fontFamilyName] = fontFamilyName
            }
            prefs[Keys.Reflow.textAlign] = config.textAlign.name
            prefs[Keys.Reflow.breakStrategy] = config.breakStrategy.name
            prefs[Keys.Reflow.hyphenationMode] = config.hyphenationMode.name
            prefs[Keys.Reflow.includeFontPadding] = config.includeFontPadding
            prefs[Keys.Reflow.cjkLineBreakStrict] = config.cjkLineBreakStrict
            prefs[Keys.Reflow.hangingPunctuation] = config.hangingPunctuation
            prefs[Keys.Reflow.pageInsetMode] = config.pageInsetMode.name
            prefs[Keys.Reflow.respectPublisherStyles] = config.respectPublisherStyles
            val pageTurnMode = PageTurnMode.fromStorageValue(config.extra[PAGE_TURN_EXTRA_KEY])
            val pageTurnStyle = normalizePageTurnStyleRaw(
                raw = config.extra[PAGE_TURN_STYLE_EXTRA_KEY]
            )
            prefs[Keys.Reflow.pageTurnMode] = pageTurnMode.storageValue
            prefs[Keys.Reflow.pageTurnStyle] = pageTurnStyle
            prefs[Keys.Reflow.legacyHyphenation] = config.hyphenationMode != HyphenationMode.NONE
        }
    }

    override suspend fun setFixedConfig(config: RenderConfig.FixedPage) {
        dataStore.edit { prefs ->
            prefs[Keys.Fixed.fitMode] = config.fitMode.name
            prefs[Keys.Fixed.zoom] = config.zoom
            prefs[Keys.Fixed.rotationDegrees] = config.rotationDegrees
        }
    }

    override suspend fun setDisplayPrefs(prefs: ReaderDisplayPrefs) {
        dataStore.edit { mutablePrefs ->
            mutablePrefs[Keys.Display.brightness] = prefs.brightness.coerceIn(0f, 1f)
            mutablePrefs[Keys.Display.useSystemBrightness] = prefs.useSystemBrightness
            mutablePrefs[Keys.Display.eyeProtection] = prefs.eyeProtection
            mutablePrefs[Keys.Display.nightMode] = prefs.nightMode
            mutablePrefs[Keys.Display.backgroundPreset] = prefs.backgroundPreset.storageValue
            mutablePrefs[Keys.Display.showReadingProgress] = prefs.showReadingProgress
            mutablePrefs[Keys.Display.fullScreenMode] = prefs.fullScreenMode
            mutablePrefs[Keys.Display.volumeKeyPagingEnabled] = prefs.volumeKeyPagingEnabled
            mutablePrefs[Keys.Display.tapZonePreset] = prefs.tapZonePreset.storageValue
            mutablePrefs[Keys.Display.preventAccidentalTurn] = prefs.preventAccidentalTurn
        }
    }

    private fun parseTextAlignMode(raw: String): TextAlignMode {
        return runCatching { TextAlignMode.valueOf(raw) }
            .getOrElse { defaultReflow.textAlign }
    }

    private fun parseBreakStrategyMode(raw: String): BreakStrategyMode {
        return runCatching { BreakStrategyMode.valueOf(raw) }
            .getOrElse { defaultReflow.breakStrategy }
    }

    private fun parseHyphenationMode(raw: String): HyphenationMode {
        return runCatching { HyphenationMode.valueOf(raw) }
            .getOrElse { defaultReflow.hyphenationMode }
    }

    private fun parsePageInsetMode(raw: String): PageInsetMode {
        return runCatching { PageInsetMode.valueOf(raw) }
            .getOrElse { defaultReflow.pageInsetMode }
    }

    private fun normalizePageTurnStyleRaw(raw: String?): String {
        val canonical = when (raw) {
            STYLE_SIMULATION -> STYLE_SIMULATION
            STYLE_COVER_OVERLAY,
            PageTurnMode.COVER_HORIZONTAL.storageValue -> STYLE_COVER_OVERLAY
            STYLE_NO_ANIMATION -> STYLE_NO_ANIMATION
            else -> defaultPageTurnStyleRaw()
        }
        return canonical
    }

    private fun defaultPageTurnStyleRaw(): String {
        return STYLE_COVER_OVERLAY
    }

    private companion object {
        const val STYLE_SIMULATION = "simulation"
        const val STYLE_COVER_OVERLAY = "cover_overlay"
        const val STYLE_NO_ANIMATION = "no_animation"
    }
}
