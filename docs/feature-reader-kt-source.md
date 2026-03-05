# feature/reader Kotlin Source (non-test)

以下内容来自 `feature/reader/src/main`，不包含测试代码。

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\di\ReaderFeatureModule.kt

```kotlin
package com.ireader.feature.reader.di

import com.ireader.feature.reader.presentation.ReaderUiErrorMapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReaderFeatureModule {

    @Provides
    @Singleton
    fun provideReaderUiErrorMapper(): ReaderUiErrorMapper = ReaderUiErrorMapper()
}
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\domain\usecase\ObserveEffectiveConfig.kt

```kotlin
package com.ireader.feature.reader.domain.usecase

import com.ireader.core.datastore.reader.ReaderSettingsStore
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.DocumentCapabilities
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveEffectiveConfig @Inject constructor(
    private val settingsStore: ReaderSettingsStore
) {
    operator fun invoke(capabilities: DocumentCapabilities): Flow<RenderConfig> {
        return if (capabilities.fixedLayout) {
            settingsStore.fixedConfig
        } else {
            settingsStore.reflowConfig
        }
    }
}
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\domain\usecase\OpenReaderSession.kt

```kotlin
package com.ireader.feature.reader.domain.usecase

import com.ireader.core.datastore.reader.ReaderSettingsStore
import com.ireader.core.files.source.DocumentSource
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.Locator
import com.ireader.reader.runtime.ReaderRuntime
import com.ireader.reader.runtime.ReaderSessionHandle
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenReaderSession @Inject constructor(
    private val runtime: ReaderRuntime,
    private val settings: ReaderSettingsStore
) {
    suspend operator fun invoke(
        source: DocumentSource,
        options: OpenOptions,
        initialLocator: Locator?,
    ): ReaderResult<ReaderSessionHandle> = withContext(Dispatchers.IO) {
        runtime.openSession(
            source = source,
            options = options,
            initialLocator = initialLocator,
            resolveInitialConfig = { capabilities ->
                if (capabilities.fixedLayout) {
                    settings.getFixedConfig()
                } else {
                    settings.getReflowConfig()
                }
            }
        )
    }
}
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\domain\usecase\SaveReadingProgress.kt

```kotlin
package com.ireader.feature.reader.domain.usecase

import com.ireader.core.data.book.ProgressRepo
import com.ireader.core.data.book.LocatorCodec
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorExtraKeys
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SaveReadingProgress @Inject constructor(
    private val progressRepo: ProgressRepo,
    private val locatorCodec: LocatorCodec
) {
    suspend operator fun invoke(bookId: Long, locator: Locator, progression: Double) {
        withContext(Dispatchers.IO) {
            progressRepo.upsert(
                bookId = bookId,
                locatorJson = locatorCodec.encode(locator),
                progression = progression.coerceIn(0.0, 1.0),
                updatedAtEpochMs = System.currentTimeMillis(),
                pageAnchorProfile = locator.extras[LocatorExtraKeys.REFLOW_PAGE_PROFILE],
                pageAnchorsJson = locator.extras[LocatorExtraKeys.REFLOW_PAGE_ANCHORS]
            )
        }
    }
}
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\navigation\ReaderNavGraph.kt

```kotlin
package com.ireader.feature.reader.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ireader.feature.reader.ui.ReaderScreen

fun NavGraphBuilder.readerNavGraph(
    onBack: () -> Unit,
    onOpenAnnotations: (Long) -> Unit
) {
    composable(
        route = ReaderRoute.pattern,
        arguments = ReaderRoute.arguments
    ) { backStackEntry ->
        ReaderScreen(
            bookId = ReaderRoute.bookId(backStackEntry),
            locatorArg = ReaderRoute.locatorArg(backStackEntry),
            onBack = onBack,
            onOpenAnnotations = onOpenAnnotations
        )
    }
}
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\navigation\ReaderRoute.kt

```kotlin
package com.ireader.feature.reader.navigation

import android.net.Uri
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.ireader.core.navigation.AppRoutes

object ReaderRoute {
    const val argBookId: String = AppRoutes.ARG_BOOK_ID
    const val argLocator: String = AppRoutes.ARG_LOCATOR
    const val route: String = "reader"
    const val pattern: String = "$route/{$argBookId}?$argLocator={$argLocator}"

    val arguments = listOf(
        navArgument(argBookId) {
            type = NavType.LongType
        },
        navArgument(argLocator) {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        }
    )

    fun create(bookId: Long, locator: String? = null): String {
        val encodedLocator = locator?.let(Uri::encode)
        return AppRoutes.reader(bookId = bookId, locator = encodedLocator)
    }

    fun bookId(entry: NavBackStackEntry): Long =
        entry.arguments?.getLong(argBookId) ?: error("Missing $argBookId")

    fun locatorArg(entry: NavBackStackEntry): String? =
        entry.arguments?.getString(argLocator)?.let(Uri::decode)
}
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\presentation\PageTurnSupport.kt

```kotlin
package com.ireader.feature.reader.presentation

import com.ireader.reader.api.render.PAGE_TURN_EXTRA_KEY
import com.ireader.reader.api.render.PAGE_TURN_STYLE_EXTRA_KEY
import com.ireader.reader.api.render.PageTurnMode
import com.ireader.reader.api.render.RenderConfig

enum class PageTurnDirection {
    NEXT,
    PREV
}

data class PageTurnTransition(
    val sequence: Long = 0L,
    val direction: PageTurnDirection = PageTurnDirection.NEXT
) {
    fun next(direction: PageTurnDirection): PageTurnTransition {
        return copy(sequence = sequence + 1L, direction = direction)
    }
}

fun RenderConfig.ReflowText.pageTurnMode(): PageTurnMode {
    return parsePageTurnMode(extra[PAGE_TURN_EXTRA_KEY])
}

fun RenderConfig.ReflowText.withPageTurnMode(mode: PageTurnMode): RenderConfig.ReflowText {
    val style = defaultPageTurnStyle(mode = mode)
    return copy(
        extra = extra +
            (PAGE_TURN_EXTRA_KEY to mode.storageValue) +
            (PAGE_TURN_STYLE_EXTRA_KEY to style.storageValue)
    )
}

enum class PageTurnStyle(
    val storageValue: String,
    val mode: PageTurnMode
) {
    SIMULATION("simulation", PageTurnMode.COVER_HORIZONTAL),
    COVER_OVERLAY("cover_overlay", PageTurnMode.COVER_HORIZONTAL),
    NO_ANIMATION("no_animation", PageTurnMode.COVER_HORIZONTAL)
}

enum class PageTurnAnimationKind {
    COVER_OVERLAY,
    SIMULATION,
    NONE
}

fun RenderConfig.ReflowText.pageTurnStyle(): PageTurnStyle {
    val mode = pageTurnMode()
    return parsePageTurnStyle(
        raw = extra[PAGE_TURN_STYLE_EXTRA_KEY],
        mode = mode
    )
}

fun RenderConfig.ReflowText.withPageTurnStyle(style: PageTurnStyle): RenderConfig.ReflowText {
    return copy(
        extra = extra +
            (PAGE_TURN_EXTRA_KEY to style.mode.storageValue) +
            (PAGE_TURN_STYLE_EXTRA_KEY to style.storageValue)
    )
}

fun defaultPageTurnStyle(mode: PageTurnMode): PageTurnStyle {
    return PageTurnStyle.COVER_OVERLAY
}

fun parsePageTurnStyle(
    raw: String?,
    mode: PageTurnMode
): PageTurnStyle {
    val parsed = when (raw) {
        PageTurnStyle.SIMULATION.storageValue -> PageTurnStyle.SIMULATION
        PageTurnStyle.COVER_OVERLAY.storageValue -> PageTurnStyle.COVER_OVERLAY
        PageTurnStyle.NO_ANIMATION.storageValue -> PageTurnStyle.NO_ANIMATION
        else -> defaultPageTurnStyle(mode = mode)
    }
    return if (parsed.mode == mode) parsed else PageTurnStyle.COVER_OVERLAY
}

fun resolvePageTurnAnimationKind(
    mode: PageTurnMode,
    style: PageTurnStyle
): PageTurnAnimationKind {
    return when (mode) {
        PageTurnMode.COVER_HORIZONTAL -> {
            when (style) {
                PageTurnStyle.NO_ANIMATION -> PageTurnAnimationKind.NONE
                PageTurnStyle.SIMULATION -> PageTurnAnimationKind.SIMULATION
                PageTurnStyle.COVER_OVERLAY -> PageTurnAnimationKind.COVER_OVERLAY
            }
        }
    }
}

fun parsePageTurnMode(raw: String?): PageTurnMode {
    return PageTurnMode.fromStorageValue(raw)
}

fun PageTurnMode.displayLabel(): String {
    return when (this) {
        PageTurnMode.COVER_HORIZONTAL -> "左右覆盖"
    }
}
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\presentation\ReaderAnnotationDraftFactory.kt

```kotlin
package com.ireader.feature.reader.presentation

import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.model.LocatorSchemes
import com.ireader.reader.model.NormalizedRect
import com.ireader.reader.model.annotation.AnnotationAnchor
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationType

internal object ReaderAnnotationDraftFactory {

    fun create(
        selection: SelectionProvider.Selection?,
        fallbackLocator: Locator?
    ): ReaderResult<AnnotationDraft> {
        val anchor = selection?.toAnchor()
            ?: fallbackLocator?.toFallbackAnchor()
            ?: return ReaderResult.Err(
                ReaderError.Internal("Cannot create annotation without a valid locator")
            )

        val content = selection?.selectedText
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return ReaderResult.Ok(
            AnnotationDraft(
                type = AnnotationType.NOTE,
                anchor = anchor,
                content = content
            )
        )
    }

    private fun SelectionProvider.Selection.toAnchor(): AnnotationAnchor {
        val startLocator = start
        val endLocator = end
        if (startLocator != null && endLocator != null) {
            return AnnotationAnchor.ReflowRange(
                LocatorRange(
                    start = startLocator,
                    end = endLocator,
                    extras = extras
                )
            )
        }

        return if (locator.scheme == LocatorSchemes.PDF_PAGE) {
            AnnotationAnchor.FixedRects(
                page = locator,
                rects = selectionRects()
            )
        } else {
            AnnotationAnchor.ReflowRange(LocatorRange(start = locator, end = locator))
        }
    }

    private fun Locator.toFallbackAnchor(): AnnotationAnchor {
        return if (scheme == LocatorSchemes.PDF_PAGE) {
            AnnotationAnchor.FixedRects(page = this, rects = emptyList())
        } else {
            AnnotationAnchor.ReflowRange(LocatorRange(start = this, end = this))
        }
    }

    private fun SelectionProvider.Selection.selectionRects(): List<NormalizedRect> {
        if (rects.isNotEmpty()) return rects
        return listOfNotNull(bounds)
    }
}
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\presentation\ReaderEffect.kt

```kotlin
package com.ireader.feature.reader.presentation

sealed interface ReaderEffect {
    data class Snackbar(val message: UiText) : ReaderEffect
    data object Back : ReaderEffect
    data class OpenAnnotations(val bookId: Long) : ReaderEffect
    data class OpenExternalUrl(val url: String) : ReaderEffect
    data class ShareText(val text: String) : ReaderEffect
}
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\presentation\ReaderGestureInterpreter.kt

```kotlin
package com.ireader.feature.reader.presentation

import com.ireader.core.datastore.reader.ReaderDisplayPrefs
import com.ireader.core.datastore.reader.TapZonePreset
import com.ireader.reader.api.render.PageTurnMode
import kotlin.math.max

internal enum class ReaderTapAction {
    PREV,
    NEXT,
    CENTER,
    NONE
}

internal class ReaderGestureInterpreter {

    fun resolveTapAction(
        xPx: Float,
        viewportWidthPx: Int,
        prefs: ReaderDisplayPrefs
    ): ReaderTapAction {
        val width = viewportWidthPx.coerceAtLeast(1).toFloat()
        val x = xPx.coerceIn(0f, width)
        val edgeGuard = if (prefs.preventAccidentalTurn) width * 0.025f else 0f
        if (x <= edgeGuard || x >= width - edgeGuard) {
            return ReaderTapAction.NONE
        }

        val profile = tapZoneProfile(prefs.tapZonePreset)
        val leftEdge = width * profile.leftRatio
        val rightEdge = width * profile.rightRatio
        return when {
            x <= leftEdge -> profile.leftAction
            x >= rightEdge -> profile.rightAction
            else -> ReaderTapAction.CENTER
        }
    }

    fun resolveDragDirection(
        axis: GestureAxis,
        deltaPx: Float,
        viewportMainAxisPx: Int,
        pageTurnMode: PageTurnMode,
        prefs: ReaderDisplayPrefs
    ): PageTurnDirection? {
        if (!isMatchingAxis(axis = axis, pageTurnMode = pageTurnMode)) {
            return null
        }
        val threshold = dragThresholdPx(
            pageTurnMode = pageTurnMode,
            viewportMainAxisPx = viewportMainAxisPx,
            preventAccidentalTurn = prefs.preventAccidentalTurn
        )
        return when {
            deltaPx <= -threshold -> PageTurnDirection.NEXT
            deltaPx >= threshold -> PageTurnDirection.PREV
            else -> null
        }
    }

    private fun isMatchingAxis(axis: GestureAxis, pageTurnMode: PageTurnMode): Boolean {
        return when (pageTurnMode) {
            PageTurnMode.COVER_HORIZONTAL -> axis == GestureAxis.HORIZONTAL
        }
    }

    private fun dragThresholdPx(
        pageTurnMode: PageTurnMode,
        viewportMainAxisPx: Int,
        preventAccidentalTurn: Boolean
    ): Float {
        val viewport = viewportMainAxisPx.coerceAtLeast(1).toFloat()
        return when (pageTurnMode) {
            PageTurnMode.COVER_HORIZONTAL -> {
                val ratio = if (preventAccidentalTurn) 0.16f else 0.12f
                val minPx = if (preventAccidentalTurn) 68f else 52f
                max(viewport * ratio, minPx)
            }
        }
    }

    private fun tapZoneProfile(preset: TapZonePreset): TapZoneProfile {
        return when (preset) {
            TapZonePreset.CLASSIC_3_ZONE -> TapZoneProfile(
                leftRatio = 0.30f,
                rightRatio = 0.70f,
                leftAction = ReaderTapAction.PREV,
                rightAction = ReaderTapAction.NEXT
            )

            TapZonePreset.SAFE_CENTER -> TapZoneProfile(
                leftRatio = 0.22f,
                rightRatio = 0.78f,
                leftAction = ReaderTapAction.PREV,
                rightAction = ReaderTapAction.NEXT
            )

            TapZonePreset.LEFT_HAND -> TapZoneProfile(
                leftRatio = 0.28f,
                rightRatio = 0.58f,
                leftAction = ReaderTapAction.NEXT,
                rightAction = ReaderTapAction.PREV
            )

            TapZonePreset.RIGHT_HAND -> TapZoneProfile(
                leftRatio = 0.42f,
                rightRatio = 0.72f,
                leftAction = ReaderTapAction.PREV,
                rightAction = ReaderTapAction.NEXT
            )
        }
    }

    private data class TapZoneProfile(
        val leftRatio: Float,
        val rightRatio: Float,
        val leftAction: ReaderTapAction,
        val rightAction: ReaderTapAction
    )
}
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\presentation\ReaderHardwareKeyBridge.kt

```kotlin
package com.ireader.feature.reader.presentation

object ReaderHardwareKeyBridge {
    @Volatile
    private var volumeKeyListener: ((keyCode: Int, action: Int) -> Boolean)? = null

    fun setVolumeKeyListener(listener: ((keyCode: Int, action: Int) -> Boolean)?) {
        volumeKeyListener = listener
    }

    fun dispatchVolumeKey(keyCode: Int, action: Int): Boolean {
        return volumeKeyListener?.invoke(keyCode, action) == true
    }
}
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\presentation\ReaderIntent.kt

```kotlin
package com.ireader.feature.reader.presentation

import com.ireader.core.datastore.reader.ReaderBackgroundPreset
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.Locator

enum class GestureAxis {
    HORIZONTAL,
    VERTICAL
}

sealed interface ReaderIntent {
    data class Start(val bookId: Long, val locatorArg: String?) : ReaderIntent
    data object RetryOpen : ReaderIntent
    data class SubmitPassword(val password: String) : ReaderIntent
    data object CancelPassword : ReaderIntent

    data class LayoutChanged(val constraints: LayoutConstraints) : ReaderIntent
    data object RefreshPage : ReaderIntent

    data object ToggleChrome : ReaderIntent
    data object ToggleImmersiveChrome : ReaderIntent
    data object BackPressed : ReaderIntent
    data class HandleTap(
        val xPx: Float,
        val yPx: Float,
        val viewportWidthPx: Int,
        val viewportHeightPx: Int
    ) : ReaderIntent
    data class HandleDragEnd(
        val axis: GestureAxis,
        val deltaPx: Float,
        val viewportMainAxisPx: Int
    ) : ReaderIntent
    data object OpenAnnotations : ReaderIntent
    data object OpenToc : ReaderIntent
    data object OpenMenu : ReaderIntent
    data class ToggleDockTab(val tab: ReaderDockTab) : ReaderIntent
    data object CloseDockPanel : ReaderIntent
    data class SetMenuTab(val tab: ReaderMenuTab) : ReaderIntent
    data object OpenSearch : ReaderIntent
    data object OpenBrightness : ReaderIntent
    data object OpenSettings : ReaderIntent
    data class OpenSettingsSub(val sheet: ReaderSheet) : ReaderIntent
    data object OpenReaderMore : ReaderIntent
    data object OpenFullSettings : ReaderIntent
    data object ShareBook : ReaderIntent
    data object CreateAnnotation : ReaderIntent
    data object ToggleNightMode : ReaderIntent
    data class UpdateBrightness(val value: Float) : ReaderIntent
    data class SetUseSystemBrightness(val enabled: Boolean) : ReaderIntent
    data class SetEyeProtection(val enabled: Boolean) : ReaderIntent
    data class SelectBackground(val preset: ReaderBackgroundPreset) : ReaderIntent
    data class SetReadingProgressVisible(val visible: Boolean) : ReaderIntent
    data class SetFullScreenMode(val enabled: Boolean) : ReaderIntent
    data class SetVolumeKeyPaging(val enabled: Boolean) : ReaderIntent
    data object CloseSheet : ReaderIntent
    data object BackInSheetHierarchy : ReaderIntent

    data object Next : ReaderIntent
    data object Prev : ReaderIntent
    data class GoTo(val locator: Locator) : ReaderIntent
    data class GoToProgress(val percent: Double) : ReaderIntent
    data class ActivateLink(val link: DocumentLink) : ReaderIntent
    data class SelectionStart(val locator: Locator) : ReaderIntent
    data class SelectionUpdate(val locator: Locator) : ReaderIntent
    data object SelectionFinish : ReaderIntent
    data object ClearSelection : ReaderIntent

    data class SearchQueryChanged(val query: String) : ReaderIntent
    data object ExecuteSearch : ReaderIntent

    data class UpdateConfig(val config: RenderConfig, val persist: Boolean) : ReaderIntent
}
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\presentation\ReaderInteractionTracker.kt

```kotlin
package com.ireader.feature.reader.presentation

internal sealed interface ReaderInteractionEvent {
    data object TapPrev : ReaderInteractionEvent
    data object TapNext : ReaderInteractionEvent
    data object CenterTapToggleChrome : ReaderInteractionEvent
    data object DragPrev : ReaderInteractionEvent
    data object DragNext : ReaderInteractionEvent
    data object UndoPageTurn : ReaderInteractionEvent
    data object ClosePanelByTap : ReaderInteractionEvent
}

internal interface ReaderInteractionTracker {
    fun track(event: ReaderInteractionEvent)

    object None : ReaderInteractionTracker {
        override fun track(event: ReaderInteractionEvent) = Unit
    }
}
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\presentation\ReaderUiError.kt

```kotlin
package com.ireader.feature.reader.presentation

enum class ReaderErrorAction {
    Retry,
    Back
}

data class ReaderUiError(
    val message: UiText,
    val actionLabel: UiText? = null,
    val action: ReaderErrorAction? = null,
    val debugCode: String? = null
)

```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\presentation\ReaderUiErrorMapper.kt

```kotlin
package com.ireader.feature.reader.presentation

import com.ireader.reader.api.error.ReaderError
import javax.inject.Inject

class ReaderUiErrorMapper @Inject constructor() {

    fun map(error: ReaderError): ReaderUiError {
        return when (error) {
            is ReaderError.UnsupportedFormat -> ReaderUiError(
                message = UiText.Dynamic("Unsupported format: ${error.detected ?: "unknown"}"),
                actionLabel = UiText.Dynamic("Back"),
                action = ReaderErrorAction.Back,
                debugCode = error.code
            )

            is ReaderError.NotFound -> ReaderUiError(
                message = UiText.Dynamic("The book file is missing"),
                actionLabel = UiText.Dynamic("Back"),
                action = ReaderErrorAction.Back,
                debugCode = error.code
            )

            is ReaderError.PermissionDenied -> ReaderUiError(
                message = UiText.Dynamic("File permission denied"),
                actionLabel = UiText.Dynamic("Retry"),
                action = ReaderErrorAction.Retry,
                debugCode = error.code
            )

            is ReaderError.InvalidPassword -> ReaderUiError(
                message = UiText.Dynamic("A password is required"),
                actionLabel = UiText.Dynamic("Retry"),
                action = ReaderErrorAction.Retry,
                debugCode = error.code
            )

            is ReaderError.CorruptOrInvalid -> ReaderUiError(
                message = UiText.Dynamic("The file is corrupt or invalid"),
                actionLabel = UiText.Dynamic("Back"),
                action = ReaderErrorAction.Back,
                debugCode = error.code
            )

            is ReaderError.DrmRestricted -> ReaderUiError(
                message = UiText.Dynamic("This file is DRM protected"),
                actionLabel = UiText.Dynamic("Back"),
                action = ReaderErrorAction.Back,
                debugCode = error.code
            )

            is ReaderError.Io -> ReaderUiError(
                message = UiText.Dynamic("I/O error while reading"),
                actionLabel = UiText.Dynamic("Retry"),
                action = ReaderErrorAction.Retry,
                debugCode = error.code
            )

            is ReaderError.Cancelled -> ReaderUiError(
                message = UiText.Dynamic("Operation cancelled"),
                debugCode = error.code
            )

            is ReaderError.Internal -> ReaderUiError(
                message = UiText.Dynamic(error.message ?: "Internal reader error"),
                actionLabel = UiText.Dynamic("Retry"),
                action = ReaderErrorAction.Retry,
                debugCode = error.code
            )
        }
    }
}

```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\presentation\ReaderUiReducer.kt

```kotlin
package com.ireader.feature.reader.presentation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class ReaderUiReducer(
    private val stateStore: MutableStateFlow<ReaderUiState>,
    private val effectStore: MutableSharedFlow<ReaderEffect>
) {
    val state: StateFlow<ReaderUiState> = stateStore.asStateFlow()

    fun update(transform: (ReaderUiState) -> ReaderUiState) {
        stateStore.update(transform)
    }

    fun emit(effect: ReaderEffect) {
        effectStore.tryEmit(effect)
    }
}

```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\presentation\ReaderUiState.kt

```kotlin
package com.ireader.feature.reader.presentation

import com.ireader.core.datastore.reader.ReaderDisplayPrefs
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.PageTurnMode
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderState
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.model.DocumentCapabilities

enum class ReaderSheet {
    None,
    Toc,
    Search,
    Brightness,
    Settings,
    SettingsFont,
    SettingsSpacing,
    SettingsPageTurn,
    SettingsMoreBackground,
    ReaderMore,
    FullSettings
}

enum class ReaderMenuTab {
    Toc,
    Notes,
    Bookmarks
}

enum class ReaderDockTab {
    Menu,
    Brightness,
    Settings
}

sealed interface ReaderLayerState {
    data object Reading : ReaderLayerState
    data class Dock(val tab: ReaderDockTab) : ReaderLayerState
    data class Sheet(val sheet: ReaderSheet) : ReaderLayerState
    data object FullSettings : ReaderLayerState
}

data class ReaderOverlayState(
    val showTopBar: Boolean = true,
    val showBottomBar: Boolean = true,
    val showGestureHint: Boolean = true
)

data class PasswordPrompt(
    val reason: UiText = UiText.Dynamic("This file requires a password"),
    val lastTried: String? = null
)

data class TocItem(
    val title: String,
    val locatorEncoded: String,
    val depth: Int
)

data class TocState(
    val isLoading: Boolean = false,
    val items: List<TocItem> = emptyList(),
    val error: UiText? = null
)

data class SearchResultItem(
    val title: String?,
    val excerpt: String,
    val locatorEncoded: String
)

data class SearchState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<SearchResultItem> = emptyList(),
    val error: UiText? = null
)

data class ReaderUiState(
    val bookId: Long = -1L,
    val title: String? = null,
    val isOpening: Boolean = false,
    val isRenderingFinal: Boolean = false,
    val chromeVisible: Boolean = false,
    val layerState: ReaderLayerState = ReaderLayerState.Reading,
    val activeMenuTab: ReaderMenuTab = ReaderMenuTab.Toc,
    val capabilities: DocumentCapabilities? = null,
    val renderState: RenderState? = null,
    val page: RenderPage? = null,
    val controller: ReaderController? = null,
    val resources: ResourceProvider? = null,
    val toc: TocState = TocState(),
    val search: SearchState = SearchState(),
    val currentConfig: RenderConfig? = null,
    val pageTurnMode: PageTurnMode = PageTurnMode.COVER_HORIZONTAL,
    val pageTransition: PageTurnTransition = PageTurnTransition(),
    val displayPrefs: ReaderDisplayPrefs = ReaderDisplayPrefs(),
    val isNightMode: Boolean = false,
    val passwordPrompt: PasswordPrompt? = null,
    val error: ReaderUiError? = null
) {
    val sheet: ReaderSheet
        get() = when (val current = layerState) {
            is ReaderLayerState.Sheet -> current.sheet
            ReaderLayerState.FullSettings -> ReaderSheet.FullSettings
            else -> ReaderSheet.None
        }

    val activeDockTab: ReaderDockTab?
        get() = (layerState as? ReaderLayerState.Dock)?.tab

    val overlayState: ReaderOverlayState
        get() {
            val chromeShown = chromeVisible && layerState != ReaderLayerState.FullSettings
            return ReaderOverlayState(
                showTopBar = chromeShown,
                showBottomBar = chromeShown,
                showGestureHint = !chromeVisible && layerState == ReaderLayerState.Reading
            )
        }
}
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\presentation\ReaderViewModel.kt

```kotlin
package com.ireader.feature.reader.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.book.IndexState
import com.ireader.core.data.book.LocatorCodec
import com.ireader.core.data.book.ProgressRepo
import com.ireader.core.datastore.reader.ReaderDisplayPrefs
import com.ireader.core.datastore.reader.ReaderSettingsStore
import com.ireader.core.files.source.BookSourceResolver
import com.ireader.feature.reader.domain.usecase.ObserveEffectiveConfig
import com.ireader.feature.reader.domain.usecase.OpenReaderSession
import com.ireader.feature.reader.domain.usecase.SaveReadingProgress
import com.ireader.feature.reader.web.ExternalLinkPolicy
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.provider.SelectionController
import com.ireader.reader.api.provider.SearchOptions
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.InvalidateReason
import com.ireader.reader.api.render.PageTurnMode
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.model.LinkTarget
import com.ireader.reader.model.OutlineNode
import com.ireader.reader.runtime.ReaderSessionHandle
import com.ireader.reader.runtime.flow.asReaderResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(FlowPreview::class)
class ReaderViewModel @Inject constructor(
    private val bookRepo: BookRepo,
    private val progressRepo: ProgressRepo,
    private val settingsStore: ReaderSettingsStore,
    private val sourceResolver: BookSourceResolver,
    private val locatorCodec: LocatorCodec,
    private val openReaderSession: OpenReaderSession,
    private val observeEffectiveConfig: ObserveEffectiveConfig,
    private val saveReadingProgress: SaveReadingProgress,
    private val errorMapper: ReaderUiErrorMapper
) : ViewModel() {

    private val stateStore = MutableStateFlow(ReaderUiState())
    val state = stateStore.asStateFlow()

    private val effectStore = MutableSharedFlow<ReaderEffect>(extraBufferCapacity = 16)
    val effects = effectStore.asSharedFlow()

    private val ui = ReaderUiReducer(stateStore = stateStore, effectStore = effectStore)
    private val session = SessionCoordinator()
    private val render = RenderCoordinator(scope = viewModelScope) {
        renderCurrentPageImmediate()
    }
    private val gestureInterpreter = ReaderGestureInterpreter()
    private val interactionTracker: ReaderInteractionTracker = ReaderInteractionTracker.None

    private val intents = Channel<ReaderIntent>(capacity = Channel.UNLIMITED)
    private var currentStartArgs: StartArgs? = null
    private var searchJob: Job? = null
    private var pendingUndoTurn: PendingUndoTurn? = null
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        viewModelScope.launch {
            for (intent in intents) {
                handleIntent(intent)
            }
        }
        viewModelScope.launch {
            settingsStore.displayPrefs
                .distinctUntilChanged()
                .collect { prefs ->
                    ui.update { current ->
                        current.copy(
                            displayPrefs = prefs,
                            isNightMode = prefs.nightMode,
                            chromeVisible = current.chromeVisible
                        )
                    }
                }
        }
    }

    fun dispatch(intent: ReaderIntent) {
        intents.trySend(intent)
    }

    fun openLocator(encoded: String) {
        val locator = locatorCodec.decode(encoded) ?: return
        dispatch(ReaderIntent.GoTo(locator))
    }

    private suspend fun handleIntent(intent: ReaderIntent) {
        when (intent) {
            is ReaderIntent.Start -> {
                val args = StartArgs(intent.bookId, intent.locatorArg)
                currentStartArgs = args
                open(args, password = null)
            }

            ReaderIntent.RetryOpen -> {
                val args = currentStartArgs ?: return
                open(args, password = null)
            }

            is ReaderIntent.SubmitPassword -> {
                val args = currentStartArgs ?: return
                open(args, password = intent.password)
            }

            ReaderIntent.CancelPassword -> {
                ui.update { it.copy(passwordPrompt = null) }
                ui.emit(ReaderEffect.Back)
            }

            is ReaderIntent.LayoutChanged -> applyLayout(intent.constraints)
            ReaderIntent.RefreshPage -> render.requestRender(RenderRequest.REFRESH)
            ReaderIntent.ToggleChrome,
            ReaderIntent.ToggleImmersiveChrome -> toggleImmersiveChrome()
            ReaderIntent.BackPressed,
            ReaderIntent.BackInSheetHierarchy -> handleBackPressed()
            is ReaderIntent.HandleTap -> handleTap(intent)
            is ReaderIntent.HandleDragEnd -> handleDragEnd(intent)

            ReaderIntent.OpenAnnotations -> {
                val bookId = ui.state.value.bookId
                if (bookId > 0L) {
                    ui.emit(ReaderEffect.OpenAnnotations(bookId))
                }
            }

            ReaderIntent.OpenToc,
            ReaderIntent.OpenMenu -> {
                val opened = toggleDockTab(ReaderDockTab.Menu)
                if (opened) loadTocIfNeeded()
            }
            is ReaderIntent.ToggleDockTab -> {
                val opened = toggleDockTab(intent.tab)
                if (opened && intent.tab == ReaderDockTab.Menu) {
                    loadTocIfNeeded()
                }
            }
            ReaderIntent.CloseDockPanel -> closeLayerToReading()
            is ReaderIntent.SetMenuTab -> {
                ui.update { it.copy(activeMenuTab = intent.tab) }
                if (intent.tab == ReaderMenuTab.Toc) {
                    loadTocIfNeeded()
                }
            }

            ReaderIntent.OpenSearch -> openSheet(ReaderSheet.Search)
            ReaderIntent.OpenBrightness -> openDock(ReaderDockTab.Brightness)
            ReaderIntent.OpenSettings -> openDock(ReaderDockTab.Settings)
            is ReaderIntent.OpenSettingsSub -> openSubSheet(intent.sheet)
            ReaderIntent.OpenReaderMore -> openSheet(ReaderSheet.ReaderMore)
            ReaderIntent.OpenFullSettings -> openFullSettings()
            ReaderIntent.ShareBook -> ui.emit(ReaderEffect.ShareText(buildShareText(ui.state.value)))
            ReaderIntent.CreateAnnotation -> createAnnotation()
            ReaderIntent.ToggleNightMode -> updateDisplayPrefs { prefs ->
                prefs.copy(nightMode = !prefs.nightMode)
            }
            is ReaderIntent.UpdateBrightness -> updateDisplayPrefs { prefs ->
                prefs.copy(brightness = intent.value.coerceIn(0f, 1f))
            }
            is ReaderIntent.SetUseSystemBrightness -> updateDisplayPrefs { prefs ->
                prefs.copy(useSystemBrightness = intent.enabled)
            }
            is ReaderIntent.SetEyeProtection -> updateDisplayPrefs { prefs ->
                prefs.copy(eyeProtection = intent.enabled)
            }
            is ReaderIntent.SelectBackground -> updateDisplayPrefs { prefs ->
                prefs.copy(backgroundPreset = intent.preset)
            }
            is ReaderIntent.SetReadingProgressVisible -> updateDisplayPrefs { prefs ->
                prefs.copy(showReadingProgress = intent.visible)
            }
            is ReaderIntent.SetFullScreenMode -> updateDisplayPrefs { prefs ->
                prefs.copy(fullScreenMode = intent.enabled)
            }
            is ReaderIntent.SetVolumeKeyPaging -> updateDisplayPrefs { prefs ->
                prefs.copy(volumeKeyPagingEnabled = intent.enabled)
            }
            ReaderIntent.CloseSheet -> closeLayerToReading()

            ReaderIntent.Next -> navigate(direction = PageTurnDirection.NEXT) { controller, policy ->
                pendingUndoTurn = null
                controller.next(policy)
            }
            ReaderIntent.Prev -> navigate(direction = PageTurnDirection.PREV) { controller, policy ->
                pendingUndoTurn = null
                controller.prev(policy)
            }
            is ReaderIntent.GoTo -> navigate { controller, policy ->
                pendingUndoTurn = null
                controller.goTo(intent.locator, policy)
            }
            is ReaderIntent.GoToProgress -> navigate { controller, policy ->
                pendingUndoTurn = null
                controller.goToProgress(intent.percent, policy)
            }
            is ReaderIntent.ActivateLink -> handleLink(intent.link.target)
            is ReaderIntent.SelectionStart -> updateSelection { controller ->
                controller.start(intent.locator)
            }
            is ReaderIntent.SelectionUpdate -> updateSelection { controller ->
                controller.update(intent.locator)
            }
            ReaderIntent.SelectionFinish -> updateSelection { controller ->
                controller.finish()
            }
            ReaderIntent.ClearSelection -> updateSelection { controller ->
                controller.clear()
            }

            is ReaderIntent.SearchQueryChanged -> {
                ui.update { current ->
                    current.copy(search = current.search.copy(query = intent.query))
                }
            }

            ReaderIntent.ExecuteSearch -> executeSearch()

            is ReaderIntent.UpdateConfig -> applyConfig(
                config = intent.config,
                persist = intent.persist
            )
        }
    }

    private suspend fun open(args: StartArgs, password: String?) {
        closeSession()
        searchJob?.cancel()
        searchJob = null
        pendingUndoTurn = null

        ui.update {
            it.copy(
                bookId = args.bookId,
                isOpening = true,
                title = null,
                layerState = ReaderLayerState.Reading,
                chromeVisible = false,
                page = null,
                controller = null,
                resources = null,
                capabilities = null,
                renderState = null,
                currentConfig = null,
                passwordPrompt = null,
                error = null,
                activeMenuTab = ReaderMenuTab.Toc,
                toc = TocState(),
                search = SearchState()
            )
        }

        val book = bookRepo.getRecordById(args.bookId)
        if (book == null) {
            ui.update {
                it.copy(
                    isOpening = false,
                    error = ReaderUiError(
                        message = UiText.Dynamic("Book not found"),
                        actionLabel = UiText.Dynamic("Back"),
                        action = ReaderErrorAction.Back
                    )
                )
            }
            return
        }

        val source = sourceResolver.resolve(book)
        if (source == null) {
            bookRepo.setIndexState(book.bookId, IndexState.MISSING, "File not found")
            ui.update {
                it.copy(
                    isOpening = false,
                    error = ReaderUiError(
                        message = UiText.Dynamic("The book file is missing"),
                        actionLabel = UiText.Dynamic("Back"),
                        action = ReaderErrorAction.Back
                    )
                )
            }
            return
        }

        val routeLocator = args.locatorArg?.let(locatorCodec::decode)
        val historyLocator = runCatching {
            progressRepo.getByBookId(book.bookId)?.locatorJson?.let(locatorCodec::decode)
        }.getOrNull()
        val initialLocator = routeLocator ?: historyLocator

        when (
            val result = openReaderSession(
                source = source,
                options = OpenOptions(
                    hintFormat = book.format,
                    password = password
                ),
                initialLocator = initialLocator
            )
        ) {
            is ReaderResult.Err -> {
                handleOpenError(result.error, password)
            }

            is ReaderResult.Ok -> {
                session.attach(bookId = book.bookId, handle = result.value)
                bookRepo.touchLastOpened(book.bookId)

                ui.update {
                    it.copy(
                        isOpening = false,
                        title = book.title?.takeIf(String::isNotBlank) ?: book.fileName,
                        controller = result.value.controller,
                        resources = result.value.resources,
                        capabilities = result.value.document.capabilities,
                        currentConfig = result.value.controller.state.value.config,
                        pageTurnMode = resolvePageTurnMode(result.value.controller.state.value.config),
                        passwordPrompt = null,
                        error = null
                    )
                }

                startSessionCollectors(result.value, book.bookId)
                val constraints = render.currentLayout()
                if (constraints != null) {
                    applyLayout(constraints)
                } else {
                    render.requestRender(RenderRequest.OPEN)
                }
            }
        }
    }

    private suspend fun handleOpenError(error: ReaderError, lastTriedPassword: String?) {
        if (error is ReaderError.InvalidPassword) {
            ui.update {
                it.copy(
                    isOpening = false,
                    passwordPrompt = PasswordPrompt(lastTried = lastTriedPassword),
                    error = null
                )
            }
            return
        }

        ui.update {
            it.copy(
                isOpening = false,
                passwordPrompt = null,
                error = errorMapper.map(error)
            )
        }
    }

    private fun startSessionCollectors(sessionHandle: ReaderSessionHandle, bookId: Long) {
        val stateJob = viewModelScope.launch {
            sessionHandle.controller.state.collect { renderState ->
                ui.update {
                    it.copy(
                        renderState = renderState,
                        currentConfig = renderState.config,
                        pageTurnMode = resolvePageTurnMode(renderState.config),
                        title = renderState.titleInView ?: it.title
                    )
                }
            }
        }

        val progressJob = viewModelScope.launch {
            sessionHandle.controller.state
                .map { renderState -> renderState.locator to renderState.progression.percent }
                .distinctUntilChanged()
                .debounce(800L)
                .collect { (locator, progression) ->
                    runCatching { saveReadingProgress(bookId, locator, progression) }
                }
        }

        val eventJob = viewModelScope.launch {
            sessionHandle.controller.events
                .filterIsInstance<ReaderEvent.Error>()
                .collect {
                    ui.emit(ReaderEffect.Snackbar(UiText.Dynamic("Render error")))
                }
        }

        val settingsJob = viewModelScope.launch {
            observeEffectiveConfig(sessionHandle.document.capabilities)
                .distinctUntilChanged()
                .collect { config ->
                    when (sessionHandle.controller.setConfig(config)) {
                        is ReaderResult.Ok -> {
                            ui.update {
                                it.copy(
                                    currentConfig = config,
                                    pageTurnMode = resolvePageTurnMode(config)
                                )
                            }
                            render.requestRender(RenderRequest.SETTINGS)
                        }

                        is ReaderResult.Err -> {
                            ui.emit(ReaderEffect.Snackbar(UiText.Dynamic("Failed to apply settings")))
                        }
                    }
                }
        }

        session.bindCollectors(
            progressJob = progressJob,
            stateJob = stateJob,
            eventJob = eventJob,
            settingsJob = settingsJob
        )
    }

    private suspend fun applyLayout(constraints: LayoutConstraints) {
        render.updateLayout(constraints)
        val sessionHandle = session.currentHandle() ?: return
        when (val result = sessionHandle.controller.setLayoutConstraints(constraints)) {
            is ReaderResult.Ok -> render.requestRender(RenderRequest.LAYOUT)
            is ReaderResult.Err -> ui.update { it.copy(error = errorMapper.map(result.error)) }
        }
    }

    private suspend fun applyConfig(config: RenderConfig, persist: Boolean) {
        if (persist) {
            runCatching {
                when (config) {
                    is RenderConfig.FixedPage -> settingsStore.setFixedConfig(config)
                    is RenderConfig.ReflowText -> settingsStore.setReflowConfig(config)
                }
            }.onFailure {
                ui.emit(ReaderEffect.Snackbar(UiText.Dynamic("Failed to save settings")))
            }
        }

        val sessionHandle = session.currentHandle() ?: run {
            ui.update {
                it.copy(
                    currentConfig = config,
                    pageTurnMode = resolvePageTurnMode(config)
                )
            }
            return
        }

        when (val result = sessionHandle.controller.setConfig(config)) {
            is ReaderResult.Err -> ui.update { it.copy(error = errorMapper.map(result.error)) }
            is ReaderResult.Ok -> {
                ui.update {
                    it.copy(
                        currentConfig = config,
                        pageTurnMode = resolvePageTurnMode(config)
                    )
                }
                render.requestRender(RenderRequest.CONFIG)
            }
        }
    }

    private suspend fun renderCurrentPageImmediate() {
        val sessionHandle = session.currentHandle() ?: return
        if (render.currentLayout() == null) return
        val controller = sessionHandle.controller
        val twoPass = sessionHandle.document.capabilities.fixedLayout

        if (!twoPass) {
            applyRenderResult(controller.render())
            return
        }

        renderWithFinalPass(
            controller = controller,
            draft = controller.render(RenderPolicy(quality = RenderPolicy.Quality.DRAFT))
        )
    }

    private suspend fun navigate(
        direction: PageTurnDirection? = null,
        block: suspend (ReaderController, RenderPolicy) -> ReaderResult<RenderPage>
    ): Boolean {
        val sessionHandle = session.currentHandle() ?: return false
        var success = false
        render.withNavigationLock {
            val fixed = sessionHandle.document.capabilities.fixedLayout
            val actionPolicy = if (fixed) {
                RenderPolicy(quality = RenderPolicy.Quality.DRAFT)
            } else {
                RenderPolicy.Default
            }

            when (val result = block(sessionHandle.controller, actionPolicy)) {
                is ReaderResult.Err -> {
                    ui.update { it.copy(error = errorMapper.map(result.error)) }
                    success = false
                }
                is ReaderResult.Ok -> if (fixed) {
                    renderWithFinalPass(
                        controller = sessionHandle.controller,
                        draft = result,
                        direction = direction
                    )
                    success = true
                } else {
                    applyRenderResult(result, direction = direction)
                    success = true
                }
            }

        }
        return success
    }

    private fun applyRenderResult(
        result: ReaderResult<RenderPage>,
        direction: PageTurnDirection? = null
    ) {
        when (result) {
            is ReaderResult.Ok -> replacePage(result.value, direction)
            is ReaderResult.Err -> ui.update { it.copy(error = errorMapper.map(result.error)) }
        }
    }

    private suspend fun renderWithFinalPass(
        controller: ReaderController,
        draft: ReaderResult<RenderPage>,
        direction: PageTurnDirection? = null
    ) {
        when (draft) {
            is ReaderResult.Ok -> replacePage(draft.value, direction)
            is ReaderResult.Err -> {
                ui.update { it.copy(error = errorMapper.map(draft.error), isRenderingFinal = false) }
                return
            }
        }

        ui.update { it.copy(isRenderingFinal = true) }
        when (val final = controller.render(RenderPolicy(quality = RenderPolicy.Quality.FINAL))) {
            is ReaderResult.Ok -> {
                replacePage(final.value, direction = null)
                ui.update { it.copy(isRenderingFinal = false) }
            }

            is ReaderResult.Err -> ui.update {
                it.copy(
                    error = errorMapper.map(final.error),
                    isRenderingFinal = false
                )
            }
        }
    }

    private fun resolvePageTurnMode(config: RenderConfig?): PageTurnMode {
        val reflow = config as? RenderConfig.ReflowText ?: return PageTurnMode.COVER_HORIZONTAL
        return reflow.pageTurnMode()
    }

    private suspend fun updateDisplayPrefs(
        transform: (ReaderDisplayPrefs) -> ReaderDisplayPrefs
    ) {
        val current = ui.state.value.displayPrefs
        val updated = transform(current)
        if (updated == current) return

        runCatching {
            settingsStore.setDisplayPrefs(updated)
        }.onFailure {
            ui.emit(ReaderEffect.Snackbar(UiText.Dynamic("Failed to save display settings")))
            return
        }

        ui.update { state ->
            state.copy(
                displayPrefs = updated,
                isNightMode = updated.nightMode,
                chromeVisible = state.chromeVisible
            )
        }
    }

    private fun buildShareText(state: ReaderUiState): String {
        val title = state.title?.takeIf { it.isNotBlank() } ?: "正在阅读"
        val progression = state.renderState?.progression?.percent
            ?.coerceIn(0.0, 1.0)
            ?.let { "${(it * 100).toInt()}%" }
        return if (progression == null) {
            title
        } else {
            "$title · $progression"
        }
    }

    private suspend fun updateSelection(
        block: suspend (SelectionController) -> ReaderResult<Unit>
    ) {
        val sessionHandle = session.currentHandle() ?: return
        val selectionController = sessionHandle.selectionController ?: return
        when (block(selectionController)) {
            is ReaderResult.Ok -> Unit
            is ReaderResult.Err -> ui.emit(
                ReaderEffect.Snackbar(UiText.Dynamic("选区操作失败"))
            )
        }
    }

    private suspend fun createAnnotation() {
        val sessionHandle = session.currentHandle() ?: return
        val annotationProvider = sessionHandle.annotations
        if (annotationProvider == null) {
            ui.emit(ReaderEffect.Snackbar(UiText.Dynamic("当前文档不支持批注")))
            return
        }

        val selection = when (val selectionProvider = sessionHandle.selection) {
            null -> null
            else -> {
                when (val result = selectionProvider.currentSelection()) {
                    is ReaderResult.Ok -> result.value
                    is ReaderResult.Err -> {
                        ui.emit(ReaderEffect.Snackbar(UiText.Dynamic("获取选区失败，已使用当前位置创建批注")))
                        null
                    }
                }
            }
        }

        val draft = when (
            val result = ReaderAnnotationDraftFactory.create(
                selection = selection,
                fallbackLocator = ui.state.value.renderState?.locator
            )
        ) {
            is ReaderResult.Ok -> result.value
            is ReaderResult.Err -> {
                ui.emit(ReaderEffect.Snackbar(UiText.Dynamic("无法创建批注：缺少定位信息")))
                return
            }
        }

        when (annotationProvider.create(draft)) {
            is ReaderResult.Err -> {
                ui.emit(ReaderEffect.Snackbar(UiText.Dynamic("创建批注失败")))
            }

            is ReaderResult.Ok -> {
                sessionHandle.selection?.clearSelection()
                sessionHandle.selectionController?.clear()
                when (sessionHandle.controller.invalidate(InvalidateReason.CONTENT_CHANGED)) {
                    is ReaderResult.Ok -> Unit
                    is ReaderResult.Err -> ui.emit(
                        ReaderEffect.Snackbar(UiText.Dynamic("批注已保存，但页面刷新失败"))
                    )
                }
                ui.emit(ReaderEffect.Snackbar(UiText.Dynamic("已添加批注")))
            }
        }
    }

    private suspend fun handleLink(target: LinkTarget) {
        when (target) {
            is LinkTarget.Internal -> {
                navigate { controller, policy ->
                    controller.goTo(target.locator, policy)
                }
            }

            is LinkTarget.External -> {
                val decision = ExternalLinkPolicy.evaluate(target.url)
                if (decision is ExternalLinkPolicy.Decision.Allow) {
                    ui.emit(ReaderEffect.OpenExternalUrl(decision.url))
                } else {
                    ui.emit(
                        ReaderEffect.Snackbar(
                            UiText.Dynamic("Blocked unsafe external link")
                        )
                    )
                }
            }
        }
    }

    private suspend fun loadTocIfNeeded() {
        val sessionHandle = session.currentHandle() ?: return
        val outline = sessionHandle.outline
        if (outline == null) {
            ui.update {
                it.copy(
                    toc = TocState(
                        isLoading = false,
                        items = emptyList(),
                        error = UiText.Dynamic("Outline is not available")
                    )
                )
            }
            return
        }
        if (ui.state.value.toc.items.isNotEmpty()) return

        ui.update { it.copy(toc = it.toc.copy(isLoading = true, error = null)) }
        when (val result = outline.getOutline()) {
            is ReaderResult.Err -> {
                ui.update {
                    it.copy(
                        toc = TocState(
                            isLoading = false,
                            items = emptyList(),
                            error = errorMapper.map(result.error).message
                        )
                    )
                }
            }

            is ReaderResult.Ok -> {
                val flat = flattenOutlineIterative(result.value)
                ui.update { it.copy(toc = TocState(isLoading = false, items = flat)) }
            }
        }
    }

    private suspend fun executeSearch() {
        val sessionHandle = session.currentHandle() ?: return
        val query = ui.state.value.search.query.trim()
        if (query.isBlank()) {
            ui.update { it.copy(search = it.search.copy(results = emptyList(), error = null)) }
            return
        }

        val provider = sessionHandle.search
        if (provider == null) {
            ui.update {
                it.copy(
                    search = it.search.copy(
                        isSearching = false,
                        results = emptyList(),
                        error = UiText.Dynamic("Search is not available")
                    )
                )
            }
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            ui.update {
                it.copy(
                    search = it.search.copy(
                        isSearching = true,
                        results = emptyList(),
                        error = null
                    )
                )
            }

            val accumulator = SearchResultAccumulator { batch ->
                ui.update { current ->
                    current.copy(
                        search = current.search.copy(
                            results = current.search.results + batch
                        )
                    )
                }
            }

            try {
                provider.search(
                    query = query,
                    options = SearchOptions(maxHits = 300)
                )
                    .asReaderResult()
                    .collect { result ->
                        when (result) {
                            is ReaderResult.Err -> {
                                ui.update {
                                    it.copy(
                                        search = it.search.copy(
                                            isSearching = false,
                                            error = errorMapper.map(result.error).message
                                        )
                                    )
                                }
                            }

                            is ReaderResult.Ok -> {
                                val hit = result.value
                                accumulator.add(
                                    SearchResultItem(
                                        title = hit.sectionTitle,
                                        excerpt = hit.excerpt,
                                        locatorEncoded = locatorCodec.encode(hit.range.start)
                                    )
                                )
                            }
                        }
                    }
            } catch (ce: CancellationException) {
                throw ce
            } finally {
                accumulator.flush()
                ui.update {
                    it.copy(
                        search = it.search.copy(isSearching = false)
                    )
                }
            }
        }
    }

    private fun flattenOutlineIterative(nodes: List<OutlineNode>): List<TocItem> {
        if (nodes.isEmpty()) return emptyList()
        val out = ArrayList<TocItem>(nodes.size)
        val stack = ArrayDeque<OutlineEntry>(nodes.size)
        for (index in nodes.lastIndex downTo 0) {
            stack.addLast(OutlineEntry(nodes[index], depth = 0))
        }

        while (stack.isNotEmpty()) {
            val (node, depth) = stack.removeLast()
            out += TocItem(
                title = node.title.ifBlank { "(untitled)" },
                locatorEncoded = locatorCodec.encode(node.locator),
                depth = depth
            )

            val children = node.children
            for (index in children.lastIndex downTo 0) {
                stack.addLast(OutlineEntry(children[index], depth + 1))
            }
        }
        return out
    }

    private suspend fun closeSession() {
        searchJob?.cancel()
        searchJob = null
        pendingUndoTurn = null
        releasePageContent(ui.state.value.page)
        ui.update { it.copy(page = null, isRenderingFinal = false) }
        session.closeCurrent { bookId, locator, progression ->
            saveReadingProgress(
                bookId = bookId,
                locator = locator,
                progression = progression
            )
        }
    }

    private fun toggleImmersiveChrome() {
        ui.update { current ->
            current.copy(chromeVisible = !current.chromeVisible)
        }
    }

    private fun openDock(tab: ReaderDockTab) {
        ui.update {
            it.copy(layerState = ReaderLayerState.Dock(tab))
        }
    }

    private fun openSheet(sheet: ReaderSheet) {
        ui.update { current ->
            when (sheet) {
                ReaderSheet.None -> current.copy(layerState = ReaderLayerState.Reading)
                ReaderSheet.FullSettings -> current.copy(layerState = ReaderLayerState.FullSettings)
                else -> current.copy(layerState = ReaderLayerState.Sheet(sheet))
            }
        }
    }

    private fun openFullSettings() {
        ui.update {
            it.copy(layerState = ReaderLayerState.FullSettings)
        }
    }

    private fun toggleDockTab(tab: ReaderDockTab): Boolean {
        val opened = ui.state.value.layerState != ReaderLayerState.Dock(tab)
        ui.update { current ->
            current.copy(
                layerState = if (opened) {
                    ReaderLayerState.Dock(tab)
                } else {
                    ReaderLayerState.Reading
                }
            )
        }
        return opened
    }

    private fun closeLayerToReading() {
        ui.update {
            it.copy(layerState = ReaderLayerState.Reading)
        }
    }

    private fun handleBackPressed() {
        val current = ui.state.value
        when (val layer = current.layerState) {
            ReaderLayerState.Reading -> {
                if (!current.chromeVisible) {
                    ui.update { it.copy(chromeVisible = true) }
                } else {
                    ui.emit(ReaderEffect.Back)
                }
            }

            is ReaderLayerState.Dock -> {
                ui.update { it.copy(layerState = ReaderLayerState.Reading) }
            }

            is ReaderLayerState.Sheet -> {
                val nextLayer = if (layer.sheet.isSettingsSubSheet()) {
                    ReaderLayerState.Dock(ReaderDockTab.Settings)
                } else {
                    ReaderLayerState.Reading
                }
                ui.update { it.copy(layerState = nextLayer) }
            }

            ReaderLayerState.FullSettings -> {
                ui.update { it.copy(layerState = ReaderLayerState.Dock(ReaderDockTab.Settings)) }
            }
        }
    }

    private suspend fun handleTap(intent: ReaderIntent.HandleTap) {
        val current = ui.state.value
        when (current.layerState) {
            is ReaderLayerState.Dock,
            is ReaderLayerState.Sheet -> {
                interactionTracker.track(ReaderInteractionEvent.ClosePanelByTap)
                closeLayerToReading()
                return
            }

            ReaderLayerState.FullSettings -> return
            ReaderLayerState.Reading -> Unit
        }

        if (current.chromeVisible) {
            ui.update { it.copy(chromeVisible = false) }
            return
        }

        if (current.displayPrefs.preventAccidentalTurn) {
            val height = intent.viewportHeightPx.coerceAtLeast(1).toFloat()
            val y = intent.yPx.coerceIn(0f, height)
            val verticalGuard = height * 0.04f
            if (y <= verticalGuard || y >= height - verticalGuard) {
                return
            }
        }

        when (
            gestureInterpreter.resolveTapAction(
                xPx = intent.xPx,
                viewportWidthPx = intent.viewportWidthPx,
                prefs = current.displayPrefs
            )
        ) {
            ReaderTapAction.PREV -> performGestureTurn(
                direction = PageTurnDirection.PREV,
                event = ReaderInteractionEvent.TapPrev
            )

            ReaderTapAction.NEXT -> performGestureTurn(
                direction = PageTurnDirection.NEXT,
                event = ReaderInteractionEvent.TapNext
            )

            ReaderTapAction.CENTER -> {
                if (tryUndoPageTurn()) return
                ui.update { it.copy(chromeVisible = true) }
                interactionTracker.track(ReaderInteractionEvent.CenterTapToggleChrome)
            }

            ReaderTapAction.NONE -> Unit
        }
    }

    private suspend fun handleDragEnd(intent: ReaderIntent.HandleDragEnd) {
        val current = ui.state.value
        if (current.layerState != ReaderLayerState.Reading) return
        val direction = gestureInterpreter.resolveDragDirection(
            axis = intent.axis,
            deltaPx = intent.deltaPx,
            viewportMainAxisPx = intent.viewportMainAxisPx,
            pageTurnMode = current.pageTurnMode,
            prefs = current.displayPrefs
        ) ?: return

        val event = when (direction) {
            PageTurnDirection.NEXT -> ReaderInteractionEvent.DragNext
            PageTurnDirection.PREV -> ReaderInteractionEvent.DragPrev
        }
        performGestureTurn(direction = direction, event = event)
    }

    private suspend fun performGestureTurn(
        direction: PageTurnDirection,
        event: ReaderInteractionEvent
    ) {
        val succeeded = when (direction) {
            PageTurnDirection.NEXT -> navigate(direction = direction) { controller, policy ->
                controller.next(policy)
            }

            PageTurnDirection.PREV -> navigate(direction = direction) { controller, policy ->
                controller.prev(policy)
            }
        }
        if (!succeeded) return
        interactionTracker.track(event)

        val prefs = ui.state.value.displayPrefs
        if (!prefs.preventAccidentalTurn) {
            pendingUndoTurn = null
            return
        }
        pendingUndoTurn = PendingUndoTurn(
            direction = direction,
            expiresAtMs = System.currentTimeMillis() + UNDO_WINDOW_MS
        )
    }

    private suspend fun tryUndoPageTurn(): Boolean {
        val pending = pendingUndoTurn ?: return false
        if (System.currentTimeMillis() > pending.expiresAtMs) {
            pendingUndoTurn = null
            return false
        }
        pendingUndoTurn = null
        val direction = when (pending.direction) {
            PageTurnDirection.NEXT -> PageTurnDirection.PREV
            PageTurnDirection.PREV -> PageTurnDirection.NEXT
        }
        val succeeded = when (direction) {
            PageTurnDirection.NEXT -> navigate(direction = direction) { controller, policy ->
                controller.next(policy)
            }

            PageTurnDirection.PREV -> navigate(direction = direction) { controller, policy ->
                controller.prev(policy)
            }
        }
        if (!succeeded) return false
        interactionTracker.track(ReaderInteractionEvent.UndoPageTurn)
        return true
    }

    private fun openSubSheet(sheet: ReaderSheet) {
        if (!sheet.isSettingsSubSheet()) return
        openSheet(sheet)
    }

    override fun onCleared() {
        render.cancel()
        releasePageContent(ui.state.value.page)
        cleanupScope.launch {
            closeSession()
        }
        super.onCleared()
    }

    private fun replacePage(
        nextPage: RenderPage,
        direction: PageTurnDirection?
    ) {
        val previous = ui.state.value.page
        if (previous != null && previous !== nextPage) {
            releasePageContent(previous)
        }
        ui.update { current ->
            val turnDirection = direction
            val shouldAnimate = turnDirection != null && current.page?.id != nextPage.id
            current.copy(
                page = nextPage,
                error = null,
                pageTransition = if (shouldAnimate) {
                    current.pageTransition.next(turnDirection)
                } else {
                    current.pageTransition
                }
            )
        }
    }

    private fun releasePageContent(page: RenderPage?) {
        when (val content = page?.content) {
            is RenderContent.BitmapPage -> {
                if (!content.bitmap.isRecycled) {
                    content.bitmap.recycle()
                }
            }

            is RenderContent.Tiles -> {
                runCatching { content.tileProvider.close() }
            }

            else -> Unit
        }
    }

    private data class StartArgs(
        val bookId: Long,
        val locatorArg: String?
    )

    private data class OutlineEntry(
        val node: OutlineNode,
        val depth: Int
    )

    private data class PendingUndoTurn(
        val direction: PageTurnDirection,
        val expiresAtMs: Long
    )

    private companion object {
        const val UNDO_WINDOW_MS = 800L
    }
}

private fun ReaderSheet.isSettingsSubSheet(): Boolean {
    return this == ReaderSheet.SettingsFont ||
        this == ReaderSheet.SettingsSpacing ||
        this == ReaderSheet.SettingsPageTurn ||
        this == ReaderSheet.SettingsMoreBackground
}
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\presentation\RenderCoordinator.kt

```kotlin
package com.ireader.feature.reader.presentation

import com.ireader.reader.api.render.LayoutConstraints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class RenderRequest {
    OPEN,
    LAYOUT,
    SETTINGS,
    CONFIG,
    REFRESH
}

internal class RenderCoordinator(
    private val scope: CoroutineScope,
    private val onRender: suspend () -> Unit
) {
    private val navigationMutex = Mutex()
    private var layoutConstraints: LayoutConstraints? = null

    private val requests = MutableSharedFlow<RenderRequest>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    @OptIn(FlowPreview::class)
    private val schedulerJob: Job = scope.launch {
        requests
            .debounce(24L)
            .collectLatest {
                onRender()
            }
    }

    fun updateLayout(constraints: LayoutConstraints) {
        layoutConstraints = constraints
    }

    fun currentLayout(): LayoutConstraints? = layoutConstraints

    fun requestRender(reason: RenderRequest) {
        requests.tryEmit(reason)
    }

    suspend fun <T> withNavigationLock(block: suspend () -> T): T = navigationMutex.withLock {
        block()
    }

    fun cancel() {
        schedulerJob.cancel()
    }
}
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\presentation\SearchResultAccumulator.kt

```kotlin
package com.ireader.feature.reader.presentation

internal class SearchResultAccumulator(
    private val flushBatchSize: Int = 20,
    private val flushIntervalMs: Long = 120,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val onFlush: (List<SearchResultItem>) -> Unit
) {
    private val pending = ArrayList<SearchResultItem>(flushBatchSize)
    private var lastFlushAtMs: Long = 0L

    fun add(item: SearchResultItem) {
        pending += item
        val now = nowMs()
        if (pending.size >= flushBatchSize || now - lastFlushAtMs >= flushIntervalMs) {
            flush(now)
        }
    }

    fun flush() {
        flush(nowMs())
    }

    private fun flush(now: Long) {
        if (pending.isEmpty()) return
        onFlush(pending.toList())
        pending.clear()
        lastFlushAtMs = now
    }
}

```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\presentation\SessionCoordinator.kt

```kotlin
package com.ireader.feature.reader.presentation

import com.ireader.reader.model.Locator
import com.ireader.reader.runtime.ReaderSessionHandle
import kotlinx.coroutines.Job

internal class SessionCoordinator {
    private var sessionHandle: ReaderSessionHandle? = null
    private var activeBookId: Long = -1L

    private var progressJob: Job? = null
    private var stateJob: Job? = null
    private var eventJob: Job? = null
    private var settingsJob: Job? = null

    fun currentHandle(): ReaderSessionHandle? = sessionHandle

    fun currentBookId(): Long = activeBookId

    fun attach(bookId: Long, handle: ReaderSessionHandle) {
        activeBookId = bookId
        sessionHandle = handle
    }

    fun clearHandle() {
        activeBookId = -1L
        sessionHandle = null
    }

    fun bindCollectors(
        progressJob: Job?,
        stateJob: Job?,
        eventJob: Job?,
        settingsJob: Job?
    ) {
        cancelCollectors()
        this.progressJob = progressJob
        this.stateJob = stateJob
        this.eventJob = eventJob
        this.settingsJob = settingsJob
    }

    fun cancelCollectors() {
        progressJob?.cancel()
        progressJob = null
        stateJob?.cancel()
        stateJob = null
        eventJob?.cancel()
        eventJob = null
        settingsJob?.cancel()
        settingsJob = null
    }

    suspend fun closeCurrent(
        saveProgress: suspend (bookId: Long, locator: Locator, progression: Double) -> Unit
    ) {
        val current = sessionHandle
        val bookId = activeBookId
        cancelCollectors()
        clearHandle()

        if (current != null && bookId > 0L) {
            runCatching {
                val renderState = current.controller.state.value
                saveProgress(bookId, renderState.locator, renderState.progression.percent)
            }
        }

        if (current != null) {
            runCatching { current.close() }
        }
    }
}
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\presentation\UiText.kt

```kotlin
package com.ireader.feature.reader.presentation

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

sealed interface UiText {
    data class Dynamic(val value: String) : UiText
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
}

@Composable
fun UiText.asString(): String =
    when (this) {
        is UiText.Dynamic -> value
        is UiText.Res -> stringResource(id, *args.toTypedArray())
    }

```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\ui\components\BitmapPage.kt

```kotlin
package com.ireader.feature.reader.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.ireader.reader.api.render.RenderContent

@Composable
fun BitmapPage(content: RenderContent.BitmapPage, modifier: Modifier = Modifier) {
    Image(
        bitmap = content.bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}

```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\ui\components\ErrorPane.kt

```kotlin
package com.ireader.feature.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ireader.feature.reader.presentation.ReaderErrorAction
import com.ireader.feature.reader.presentation.ReaderUiError
import com.ireader.feature.reader.presentation.asString

@Composable
fun ErrorPane(
    error: ReaderUiError,
    onAction: (ReaderErrorAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = error.message.asString(), color = Color.White)
        val action = error.action
        if (action != null) {
            TextButton(onClick = { onAction(action) }) {
                Text(text = error.actionLabel?.asString() ?: action.name)
            }
        }
    }
}

```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\ui\components\PageRenderer.kt

```kotlin
package com.ireader.feature.reader.ui.components

import android.widget.TextView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.ireader.feature.reader.presentation.GestureAxis
import com.ireader.feature.reader.presentation.PageTurnAnimationKind
import com.ireader.feature.reader.presentation.PageTurnDirection
import com.ireader.feature.reader.presentation.ReaderUiState
import com.ireader.feature.reader.presentation.defaultPageTurnStyle
import com.ireader.feature.reader.presentation.pageTurnStyle
import com.ireader.feature.reader.presentation.resolvePageTurnAnimationKind
import com.ireader.feature.reader.ui.ReaderSurface
import com.ireader.reader.api.render.PageTurnMode
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.TextMapping
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.Locator

@Composable
fun PageRenderer(
    state: ReaderUiState,
    textColor: Color,
    backgroundColor: Color,
    onBackgroundTap: (Offset, IntSize) -> Unit,
    onDragEnd: (axis: GestureAxis, deltaPx: Float, viewportMainAxisPx: Int) -> Unit,
    onLinkActivated: (DocumentLink) -> Unit,
    onSelectionStart: (Locator) -> Unit,
    onSelectionUpdate: (Locator) -> Unit,
    onSelectionFinish: () -> Unit,
    onSelectionClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val page = state.page
    if (page == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No page")
        }
        return
    }

    val content = page.content
    val containerModifier = if (content is RenderContent.Tiles || content is RenderContent.Text) {
        modifier.fillMaxSize()
    } else {
        modifier
            .fillMaxSize()
            .pointerInput(page.id.value) {
                detectTapGestures(onTap = { tap ->
                    onSelectionClear()
                    onBackgroundTap(tap, size)
                })
            }
    }

    Box(modifier = containerModifier) {
        when (content) {
            is RenderContent.Text -> AnimatedTextPage(
                page = page,
                state = state,
                textColor = textColor,
                backgroundColor = backgroundColor,
                onBackgroundTap = onBackgroundTap,
                onDragEnd = onDragEnd,
                onLinkActivated = onLinkActivated,
                onSelectionStart = onSelectionStart,
                onSelectionUpdate = onSelectionUpdate,
                onSelectionFinish = onSelectionFinish,
                onSelectionClear = onSelectionClear,
                modifier = Modifier.fillMaxSize()
            )

            is RenderContent.BitmapPage -> BitmapPage(content = content, modifier = Modifier.fillMaxSize())

            is RenderContent.Tiles -> TilesPage(
                pageId = page.id.value,
                content = content,
                links = page.links,
                decorations = page.decorations,
                pageLocator = page.locator,
                onBackgroundTap = onBackgroundTap,
                onLinkActivated = onLinkActivated,
                onSelectionStart = onSelectionStart,
                onSelectionFinish = onSelectionFinish,
                onSelectionClear = onSelectionClear,
                modifier = Modifier.fillMaxSize()
            )

            RenderContent.Embedded -> {
                val controller = state.controller
                if (controller == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No controller bound")
                    }
                } else {
                    ReaderSurface(
                        controller = controller,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedTextPage(
    page: RenderPage,
    state: ReaderUiState,
    textColor: Color,
    backgroundColor: Color,
    onBackgroundTap: (Offset, IntSize) -> Unit,
    onDragEnd: (axis: GestureAxis, deltaPx: Float, viewportMainAxisPx: Int) -> Unit,
    onLinkActivated: (DocumentLink) -> Unit,
    onSelectionStart: (Locator) -> Unit,
    onSelectionUpdate: (Locator) -> Unit,
    onSelectionFinish: () -> Unit,
    onSelectionClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mode = state.pageTurnMode
    val reflowConfig = state.currentConfig as? RenderConfig.ReflowText
    val style = reflowConfig?.pageTurnStyle() ?: defaultPageTurnStyle(mode = mode)
    val target = AnimatedTextTarget(
        page = page,
        transition = state.pageTransition
    )
    AnimatedContent(
        targetState = target,
        transitionSpec = {
            if (initialState.transition.sequence == targetState.transition.sequence) {
                fadeIn(animationSpec = tween(0)) togetherWith fadeOut(animationSpec = tween(0))
            } else {
                buildPageTransform(
                    mode = mode,
                    style = style,
                    direction = targetState.transition.direction
                )
            }
        },
        label = "txt-page-turn",
        modifier = modifier
    ) { animatedTarget ->
        val targetPage = animatedTarget.page
        val targetContent = targetPage.content as? RenderContent.Text
        if (targetContent == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Unsupported page content")
            }
            return@AnimatedContent
        }

        var textViewRef by remember(targetPage.id.value) { mutableStateOf<TextView?>(null) }
        val linkHits = remember(targetPage.id.value, targetPage.links, targetContent.mapping) {
            val mapping = targetContent.mapping
            targetPage.links.mapNotNull { link ->
                val range = link.range ?: return@mapNotNull null
                val charRange = mapping?.charRangeFor(range) ?: return@mapNotNull null
                if (charRange.isEmpty()) return@mapNotNull null
                TextLinkHit(link = link, range = charRange)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            TextPage(
                content = targetContent,
                links = targetPage.links,
                decorations = targetPage.decorations,
                reflowConfig = reflowConfig,
                textColor = textColor,
                backgroundColor = backgroundColor,
                onTextViewBound = { textView -> textViewRef = textView },
                modifier = Modifier.fillMaxSize()
            )
            TextGestureOverlay(
                pageId = targetPage.id.value,
                mode = mode,
                onTap = { tap, size ->
                    val link = hitTestTextLink(
                        tap = tap,
                        textView = textViewRef,
                        links = linkHits
                    )
                    if (link != null) {
                        onLinkActivated(link)
                    } else {
                        onBackgroundTap(tap, size)
                    }
                },
                onDragEnd = onDragEnd,
                resolveLocatorAt = { tap ->
                    hitTestTextLocator(
                        tap = tap,
                        textView = textViewRef,
                        mapping = targetContent.mapping
                    )
                },
                onSelectionStart = onSelectionStart,
                onSelectionUpdate = onSelectionUpdate,
                onSelectionFinish = onSelectionFinish,
                onSelectionClear = onSelectionClear,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private data class AnimatedTextTarget(
    val page: RenderPage,
    val transition: com.ireader.feature.reader.presentation.PageTurnTransition
)

@Composable
private fun TextGestureOverlay(
    pageId: String,
    mode: PageTurnMode,
    onTap: (Offset, IntSize) -> Unit,
    onDragEnd: (axis: GestureAxis, deltaPx: Float, viewportMainAxisPx: Int) -> Unit,
    resolveLocatorAt: (Offset) -> Locator?,
    onSelectionStart: (Locator) -> Unit,
    onSelectionUpdate: (Locator) -> Unit,
    onSelectionFinish: () -> Unit,
    onSelectionClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragDeltaPx by remember(pageId, mode) { mutableFloatStateOf(0f) }

    val dragModifier = when (mode) {
        PageTurnMode.COVER_HORIZONTAL -> Modifier.pointerInput(pageId, mode) {
            detectHorizontalDragGestures(
                onDragStart = { dragDeltaPx = 0f },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    dragDeltaPx += amount
                },
                onDragCancel = { dragDeltaPx = 0f },
                onDragEnd = {
                    onDragEnd(
                        GestureAxis.HORIZONTAL,
                        dragDeltaPx,
                        size.width
                    )
                    dragDeltaPx = 0f
                }
            )
        }
    }

    Box(
        modifier = modifier
            .pointerInput(pageId) {
                detectTapGestures(
                    onTap = { tap ->
                        onSelectionClear()
                        onTap(tap, size)
                    },
                    onLongPress = { tap ->
                        val locator = resolveLocatorAt(tap) ?: return@detectTapGestures
                        onSelectionStart(locator)
                        onSelectionFinish()
                    }
                )
            }
            .pointerInput(pageId) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        val locator = resolveLocatorAt(position) ?: return@detectDragGesturesAfterLongPress
                        onSelectionStart(locator)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val locator = resolveLocatorAt(change.position)
                            ?: return@detectDragGesturesAfterLongPress
                        onSelectionUpdate(locator)
                    },
                    onDragEnd = onSelectionFinish,
                    onDragCancel = onSelectionFinish
                )
            }
            .then(dragModifier)
    )
}

private data class TextLinkHit(
    val link: DocumentLink,
    val range: IntRange
)

private fun hitTestTextLink(
    tap: Offset,
    textView: TextView?,
    links: List<TextLinkHit>
): DocumentLink? {
    val charOffset = hitTestTextCharOffset(tap = tap, textView = textView) ?: return null
    if (links.isEmpty()) return null
    val match = links.firstOrNull { hit -> charOffset in hit.range } ?: return null
    return match.link
}

private fun hitTestTextLocator(
    tap: Offset,
    textView: TextView?,
    mapping: TextMapping?
): Locator? {
    val charOffset = hitTestTextCharOffset(tap = tap, textView = textView) ?: return null
    return mapping?.locatorAt(charOffset)
}

private fun hitTestTextCharOffset(
    tap: Offset,
    textView: TextView?
): Int? {
    val view = textView ?: return null
    val layout = view.layout ?: return null

    val localX = tap.x - view.totalPaddingLeft + view.scrollX
    val localY = tap.y - view.totalPaddingTop + view.scrollY
    if (localX < 0f || localY < 0f) {
        return null
    }
    if (layout.height <= 0) {
        return null
    }
    val line = layout.getLineForVertical(localY.toInt().coerceAtMost(layout.height - 1))
    val lineLeft = layout.getLineLeft(line)
    val lineRight = layout.getLineRight(line)
    if (localX < lineLeft || localX > lineRight) {
        return null
    }
    return layout.getOffsetForHorizontal(line, localX)
}

private fun buildPageTransform(
    mode: PageTurnMode,
    style: com.ireader.feature.reader.presentation.PageTurnStyle,
    direction: PageTurnDirection
): ContentTransform {
    val forward = direction == PageTurnDirection.NEXT
    val durationMs = 220
    return when (resolvePageTurnAnimationKind(mode = mode, style = style)) {
        PageTurnAnimationKind.COVER_OVERLAY -> {
            val enter = fadeIn(
                animationSpec = tween(durationMs)
            ) + scaleIn(
                initialScale = 0.99f,
                animationSpec = tween(durationMs)
            )
            (enter togetherWith ExitTransition.None).apply {
                targetContentZIndex = 1f
            }
        }

        PageTurnAnimationKind.SIMULATION -> {
            val enter = slideInHorizontally(
                animationSpec = tween(durationMs)
            ) { full -> if (forward) full else -full } +
                fadeIn(animationSpec = tween(durationMs / 2)) +
                scaleIn(
                    initialScale = 0.96f,
                    animationSpec = tween(durationMs)
                )
            (enter togetherWith ExitTransition.None).apply {
                targetContentZIndex = 1.1f
            }
        }

        PageTurnAnimationKind.NONE -> {
            EnterTransition.None togetherWith ExitTransition.None
        }
    }
}
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\ui\components\PasswordDialog.kt

```kotlin
package com.ireader.feature.reader.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ireader.feature.reader.presentation.PasswordPrompt
import com.ireader.feature.reader.presentation.asString

@Composable
fun PasswordDialog(
    prompt: PasswordPrompt,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var password by remember { mutableStateOf(prompt.lastTried.orEmpty()) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Password required") },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(prompt.reason.asString()) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(password) },
                enabled = password.isNotBlank()
            ) {
                Text("Open")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\ui\components\SearchSheet.kt

```kotlin
package com.ireader.feature.reader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ireader.core.designsystem.PrototypeIcons
import com.ireader.core.designsystem.ReaderTokens
import com.ireader.feature.reader.presentation.SearchState
import com.ireader.feature.reader.presentation.asString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(
    state: SearchState,
    isNightMode: Boolean,
    onClose: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClickResult: (locatorEncoded: String) -> Unit
) {
    var query by remember(state.query) { mutableStateOf(state.query) }
    val container = if (isNightMode) ReaderTokens.Palette.ReaderPanelElevatedNight else ReaderTokens.Palette.PrototypeSurface
    val card = if (isNightMode) Color(0xFF2B2B2B) else Color.White
    val border = if (isNightMode) Color(0xFF3A3A3A) else ReaderTokens.Palette.PrototypeBorder
    val titleColor = if (isNightMode) Color(0xFFE5E5E5) else ReaderTokens.Palette.PrototypeTextPrimary
    val subColor = if (isNightMode) ReaderTokens.Palette.SecondaryTextNight else ReaderTokens.Palette.PrototypeTextTertiary

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = container
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("全文搜索", style = MaterialTheme.typography.titleMedium, color = titleColor)
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onQueryChange(it)
                },
                label = { Text("关键词") },
                leadingIcon = {
                    PrototypeIcons.Search(modifier = Modifier.padding(start = 2.dp), tint = subColor)
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = card,
                    unfocusedContainerColor = card,
                    focusedBorderColor = ReaderTokens.Palette.PrototypeBlue,
                    unfocusedBorderColor = border,
                    focusedLabelColor = ReaderTokens.Palette.PrototypeBlue,
                    unfocusedLabelColor = subColor
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onSearch,
                    enabled = query.isNotBlank() && !state.isSearching
                ) {
                    Text(if (state.isSearching) "搜索中..." else "搜索")
                }
            }

            if (state.error != null) {
                Text(
                    text = state.error.asString(),
                    color = ReaderTokens.Palette.PrototypeDanger,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            HorizontalDivider(color = border)
            when {
                state.isSearching -> {
                    CircularProgressIndicator(modifier = Modifier.padding(vertical = 8.dp))
                }

                query.isBlank() -> {
                    Text(
                        text = "输入关键词开始搜索",
                        style = MaterialTheme.typography.bodySmall,
                        color = subColor
                    )
                }

                state.results.isEmpty() && state.error == null -> {
                    Text(
                        text = "未找到匹配结果",
                        style = MaterialTheme.typography.bodySmall,
                        color = subColor
                    )
                }

                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.results) { item ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = card,
                            border = androidx.compose.foundation.BorderStroke(
                                width = ReaderTokens.Border.Hairline,
                                color = border
                            )
                        ) {
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onClickResult(item.locatorEncoded) }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (!item.title.isNullOrBlank()) {
                                        Text(item.title, color = titleColor)
                                    }
                                    Text(item.excerpt, color = subColor)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\ui\components\SettingsSheet.kt

```kotlin
package com.ireader.feature.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ireader.core.datastore.reader.ReaderBackgroundPreset
import com.ireader.core.designsystem.PrototypeIcons
import com.ireader.reader.api.render.BreakStrategyMode
import com.ireader.reader.api.render.HyphenationMode
import com.ireader.core.designsystem.ReaderTokens
import com.ireader.reader.api.render.PageInsetMode
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.TextAlignMode
import com.ireader.feature.reader.presentation.PageTurnStyle
import com.ireader.feature.reader.presentation.pageTurnStyle
import com.ireader.feature.reader.presentation.withPageTurnStyle
import com.ireader.reader.model.DocumentCapabilities
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ReaderSettingsPanel {
    Font,
    Spacing,
    PageTurn,
    MoreBackground
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    panel: ReaderSettingsPanel,
    capabilities: DocumentCapabilities?,
    config: RenderConfig?,
    isNightMode: Boolean,
    backgroundPreset: ReaderBackgroundPreset,
    onClose: () -> Unit,
    onBackToMain: () -> Unit,
    onSelectBackground: (ReaderBackgroundPreset) -> Unit,
    onApply: (RenderConfig, persist: Boolean) -> Unit
) {
    val container = if (isNightMode) ReaderTokens.Palette.ReaderPanelElevatedNight else ReaderTokens.Palette.PrototypeSurface

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = container
    ) {
        if (capabilities?.fixedLayout == true) {
            FixedLayoutSettings(
                config = config as? RenderConfig.FixedPage ?: RenderConfig.FixedPage(),
                isNightMode = isNightMode,
                onApply = onApply
            )
            return@ModalBottomSheet
        }

        val current = config as? RenderConfig.ReflowText ?: RenderConfig.ReflowText()
        when (panel) {
            ReaderSettingsPanel.Font -> FontPanel(
                current = current,
                isNightMode = isNightMode,
                onBack = onBackToMain,
                onApply = onApply
            )

            ReaderSettingsPanel.Spacing -> SpacingPanel(
                current = current,
                isNightMode = isNightMode,
                onBack = onBackToMain,
                onApply = onApply
            )

            ReaderSettingsPanel.PageTurn -> PageTurnPanel(
                current = current,
                isNightMode = isNightMode,
                onBack = onBackToMain,
                onApply = onApply
            )

            ReaderSettingsPanel.MoreBackground -> MoreBackgroundPanel(
                isNightMode = isNightMode,
                selectedPreset = backgroundPreset,
                onBack = onBackToMain,
                onSelectBackground = onSelectBackground
            )
        }
    }
}

@Composable
private fun FontPanel(
    current: RenderConfig.ReflowText,
    isNightMode: Boolean,
    onBack: () -> Unit,
    onApply: (RenderConfig, persist: Boolean) -> Unit
) {
    val fonts = listOf("系统字体", "思源宋体", "霞鹜文楷", "方正新楷体")
    var persist by remember { mutableStateOf(true) }
    var livePreview by remember { mutableStateOf(true) }
    var selectedFamily by remember(current.fontFamilyName) { mutableStateOf(current.fontFamilyName) }
    val textColor = if (isNightMode) Color(0xFFEAE7E1) else Color(0xFF1D1B17)
    val cardBg = if (isNightMode) Color(0xFF2A2A2A) else Color(0xFFF3EFE7)
    val unselectedBorder = if (isNightMode) Color(0x2EFFFFFF) else ReaderTokens.Palette.PrototypeBorder

    fun currentDraft(): RenderConfig.ReflowText {
        return current.copy(fontFamilyName = selectedFamily)
    }
    fun previewIfEnabled() {
        if (livePreview) {
            onApply(currentDraft(), false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight(0.72f)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SheetHeader(title = "字体设置", onBack = onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(fonts.size) { index ->
                val name = fonts[index]
                val selected = selectedFamily == name || (index == 0 && selectedFamily == null)
                Box(
                    modifier = Modifier
                        .height(70.dp)
                        .background(
                            if (selected) {
                                if (isNightMode) ReaderTokens.Palette.PrototypeBlue.copy(alpha = 0.2f) else ReaderTokens.Palette.PrototypeBlueSoft
                            } else {
                                cardBg
                            },
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) ReaderTokens.Palette.PrototypeBlue else unselectedBorder,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    TextButton(
                        onClick = {
                            selectedFamily = if (index == 0) null else name
                            previewIfEnabled()
                        }
                    ) {
                        Text(name, color = textColor)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("实时预览", color = textColor)
            Switch(
                checked = livePreview,
                onCheckedChange = {
                    livePreview = it
                    previewIfEnabled()
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(
                    checked = persist,
                    onCheckedChange = { persist = it }
                )
                Text("保存为默认", color = textColor)
            }
            Button(onClick = { onApply(currentDraft(), persist) }) {
                Text("应用")
            }
        }
    }
}

@Composable
private fun SpacingPanel(
    current: RenderConfig.ReflowText,
    isNightMode: Boolean,
    onBack: () -> Unit,
    onApply: (RenderConfig, persist: Boolean) -> Unit
) {
    val defaults = remember {
        RenderConfig.ReflowText(
            lineHeightMult = 1.85f,
            paragraphSpacingDp = 10f,
            paragraphIndentEm = 2.0f,
            pagePaddingDp = 20f,
            textAlign = TextAlignMode.JUSTIFY,
            breakStrategy = BreakStrategyMode.BALANCED,
            hyphenationMode = HyphenationMode.NORMAL,
            includeFontPadding = false,
            pageInsetMode = PageInsetMode.RELAXED
        )
    }
    data class SpacingPreset(
        val label: String,
        val lineHeight: Float,
        val paragraph: Float,
        val indent: Float,
        val padding: Float
    )
    val spacingPresets = remember {
        listOf(
            SpacingPreset(label = "紧凑", lineHeight = 1.45f, paragraph = 4f, indent = 1.6f, padding = 14f),
            SpacingPreset(label = "默认", lineHeight = 1.85f, paragraph = 10f, indent = 2.0f, padding = 20f),
            SpacingPreset(label = "宽松", lineHeight = 2.1f, paragraph = 14f, indent = 2.2f, padding = 24f)
        )
    }
    var persist by remember { mutableStateOf(true) }
    var livePreview by remember { mutableStateOf(true) }
    var lineHeight by remember(current.lineHeightMult) { mutableFloatStateOf(current.lineHeightMult) }
    var paragraph by remember(current.paragraphSpacingDp) { mutableFloatStateOf(current.paragraphSpacingDp) }
    var paragraphIndent by remember(current.paragraphIndentEm) { mutableFloatStateOf(current.paragraphIndentEm) }
    var padding by remember(current.pagePaddingDp) { mutableFloatStateOf(current.pagePaddingDp) }
    var textAlign by remember(current.textAlign) { mutableStateOf(current.textAlign) }
    var breakStrategy by remember(current.breakStrategy) { mutableStateOf(current.breakStrategy) }
    var hyphenationMode by remember(current.hyphenationMode) { mutableStateOf(current.hyphenationMode) }
    var includeFontPadding by remember(current.includeFontPadding) { mutableStateOf(current.includeFontPadding) }
    var pageInsetMode by remember(current.pageInsetMode) { mutableStateOf(current.pageInsetMode) }
    var respectPublisherStyles by remember(current.respectPublisherStyles) { mutableStateOf(current.respectPublisherStyles) }
    val textColor = if (isNightMode) Color(0xFFE9E5DE) else Color(0xFF1E1C18)
    val subColor = if (isNightMode) Color(0xFF9C978E) else Color(0xFF6F6A62)

    fun draftConfig(): RenderConfig.ReflowText {
        return current.copy(
            lineHeightMult = lineHeight,
            paragraphSpacingDp = paragraph,
            paragraphIndentEm = paragraphIndent,
            pagePaddingDp = padding,
            textAlign = textAlign,
            breakStrategy = breakStrategy,
            hyphenationMode = hyphenationMode,
            includeFontPadding = includeFontPadding,
            pageInsetMode = pageInsetMode,
            respectPublisherStyles = respectPublisherStyles
        )
    }
    fun previewIfEnabled() {
        if (livePreview) {
            onApply(draftConfig(), false)
        }
    }

    fun resetToDefaults() {
        lineHeight = defaults.lineHeightMult
        paragraph = defaults.paragraphSpacingDp
        paragraphIndent = defaults.paragraphIndentEm
        padding = defaults.pagePaddingDp
        textAlign = defaults.textAlign
        breakStrategy = defaults.breakStrategy
        hyphenationMode = defaults.hyphenationMode
        includeFontPadding = defaults.includeFontPadding
        pageInsetMode = defaults.pageInsetMode
        respectPublisherStyles = defaults.respectPublisherStyles
        previewIfEnabled()
    }

    fun applyPreset(preset: SpacingPreset) {
        lineHeight = preset.lineHeight
        paragraph = preset.paragraph
        paragraphIndent = preset.indent
        padding = preset.padding
        previewIfEnabled()
    }

    fun isPresetSelected(preset: SpacingPreset): Boolean {
        return abs(lineHeight - preset.lineHeight) < 0.02f &&
            abs(paragraph - preset.paragraph) < 0.5f &&
            abs(paragraphIndent - preset.indent) < 0.05f &&
            abs(padding - preset.padding) < 0.5f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SheetHeader(
            title = "间距设置",
            onBack = onBack,
            actionText = "恢复默认",
            onActionClick = ::resetToDefaults
        )
        Text("预设", color = textColor)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            spacingPresets.forEach { preset ->
                ChoiceButton(
                    label = preset.label,
                    selected = isPresetSelected(preset),
                    isNightMode = isNightMode,
                    onClick = { applyPreset(preset) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("尊重出版方样式（EPUB）", color = textColor)
                Text(
                    "开启后，行距/段距/对齐/缩进等可能不生效",
                    color = subColor,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = respectPublisherStyles,
                onCheckedChange = {
                    respectPublisherStyles = it
                    previewIfEnabled()
                }
            )
        }
        SettingSliderRow(
            label = "行间距",
            valueLabel = "%.2f".format(lineHeight),
            value = lineHeight,
            range = 1.1f..2.4f,
            textColor = textColor,
            onChange = {
                lineHeight = it
                previewIfEnabled()
            }
        )
        SettingSliderRow(
            label = "首行缩进",
            valueLabel = "${"%.1f".format(paragraphIndent)}em",
            value = paragraphIndent,
            range = 0f..3.2f,
            textColor = textColor,
            onChange = {
                paragraphIndent = it
                previewIfEnabled()
            }
        )
        SettingSliderRow(
            label = "段间距",
            valueLabel = "${paragraph.roundToInt()}dp",
            value = paragraph,
            range = 0f..24f,
            textColor = textColor,
            onChange = {
                paragraph = it
                previewIfEnabled()
            }
        )
        SettingSliderRow(
            label = "左右边距",
            valueLabel = "${padding.roundToInt()}dp",
            value = padding,
            range = 8f..42f,
            textColor = textColor,
            onChange = {
                padding = it
                previewIfEnabled()
            }
        )

        Text("对齐方式", color = textColor)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceButton(
                label = "左对齐",
                selected = textAlign == TextAlignMode.START,
                isNightMode = isNightMode,
                onClick = {
                    textAlign = TextAlignMode.START
                    previewIfEnabled()
                }
            )
            ChoiceButton(
                label = "两端对齐",
                selected = textAlign == TextAlignMode.JUSTIFY,
                isNightMode = isNightMode,
                onClick = {
                    textAlign = TextAlignMode.JUSTIFY
                    previewIfEnabled()
                }
            )
        }

        Text("断行策略", color = textColor)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceButton(
                label = "快速",
                selected = breakStrategy == BreakStrategyMode.SIMPLE,
                isNightMode = isNightMode,
                onClick = {
                    breakStrategy = BreakStrategyMode.SIMPLE
                    previewIfEnabled()
                }
            )
            ChoiceButton(
                label = "平衡",
                selected = breakStrategy == BreakStrategyMode.BALANCED,
                isNightMode = isNightMode,
                onClick = {
                    breakStrategy = BreakStrategyMode.BALANCED
                    previewIfEnabled()
                }
            )
            ChoiceButton(
                label = "高质量",
                selected = breakStrategy == BreakStrategyMode.HIGH_QUALITY,
                isNightMode = isNightMode,
                onClick = {
                    breakStrategy = BreakStrategyMode.HIGH_QUALITY
                    previewIfEnabled()
                }
            )
        }

        Text("断词", color = textColor)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceButton(
                label = "关闭",
                selected = hyphenationMode == HyphenationMode.NONE,
                isNightMode = isNightMode,
                onClick = {
                    hyphenationMode = HyphenationMode.NONE
                    previewIfEnabled()
                }
            )
            ChoiceButton(
                label = "普通",
                selected = hyphenationMode == HyphenationMode.NORMAL,
                isNightMode = isNightMode,
                onClick = {
                    hyphenationMode = HyphenationMode.NORMAL
                    previewIfEnabled()
                }
            )
            ChoiceButton(
                label = "增强",
                selected = hyphenationMode == HyphenationMode.FULL,
                isNightMode = isNightMode,
                onClick = {
                    hyphenationMode = HyphenationMode.FULL
                    previewIfEnabled()
                }
            )
        }

        Text("页面留白", color = textColor)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceButton(
                label = "舒适",
                selected = pageInsetMode == PageInsetMode.RELAXED,
                isNightMode = isNightMode,
                onClick = {
                    pageInsetMode = PageInsetMode.RELAXED
                    previewIfEnabled()
                }
            )
            ChoiceButton(
                label = "紧凑",
                selected = pageInsetMode == PageInsetMode.COMPACT,
                isNightMode = isNightMode,
                onClick = {
                    pageInsetMode = PageInsetMode.COMPACT
                    previewIfEnabled()
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("字体安全留白", color = textColor)
            Switch(
                checked = includeFontPadding,
                onCheckedChange = {
                    includeFontPadding = it
                    previewIfEnabled()
                }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("实时预览", color = textColor)
            Switch(
                checked = livePreview,
                onCheckedChange = {
                    livePreview = it
                    previewIfEnabled()
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(
                    checked = persist,
                    onCheckedChange = { persist = it }
                )
                Text("保存为默认", color = textColor)
            }
            Button(onClick = { onApply(draftConfig(), persist) }) {
                Text("应用")
            }
        }
    }
}

@Composable
private fun PageTurnPanel(
    current: RenderConfig.ReflowText,
    isNightMode: Boolean,
    onBack: () -> Unit,
    onApply: (RenderConfig, persist: Boolean) -> Unit
) {
    data class PageTurnOption(
        val label: String,
        val style: PageTurnStyle
    )
    val options = remember {
        listOf(
            PageTurnOption("仿真翻页", PageTurnStyle.SIMULATION),
            PageTurnOption("左右覆盖", PageTurnStyle.COVER_OVERLAY),
            PageTurnOption("无动效", PageTurnStyle.NO_ANIMATION)
        )
    }
    var persist by remember { mutableStateOf(true) }
    var livePreview by remember { mutableStateOf(true) }
    var selectedStyle by remember(current.extra) {
        mutableStateOf(current.pageTurnStyle())
    }
    fun draftConfig(): RenderConfig.ReflowText {
        return current.withPageTurnStyle(selectedStyle)
    }
    fun previewIfEnabled() {
        if (livePreview) {
            onApply(draftConfig(), false)
        }
    }

    val optionBg = if (isNightMode) Color(0xFF2B2B2B) else Color(0xFFF4F5F6)
    val optionBorder = if (isNightMode) Color(0x2EFFFFFF) else ReaderTokens.Palette.PrototypeBorder
    val textColor = if (isNightMode) Color(0xFFE9E5DE) else Color(0xFF1E1C18)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SheetHeader(title = "翻页方式", onBack = onBack)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            options.forEach { option ->
                val isSelected = option.style == selectedStyle
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(width = 66.dp, height = 86.dp)
                            .background(optionBg, RoundedCornerShape(8.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) ReaderTokens.Palette.PrototypeBlue else optionBorder,
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        PageTurnGlyph(
                            label = option.label,
                            tint = if (isSelected) ReaderTokens.Palette.PrototypeBlue else ReaderTokens.Palette.PrototypeTextTertiary
                        )
                    }
                    TextButton(
                        onClick = {
                            selectedStyle = option.style
                            previewIfEnabled()
                        }
                    ) {
                        Text(
                            option.label,
                            color = if (isSelected) ReaderTokens.Palette.PrototypeBlue else textColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("实时预览", color = textColor)
            Switch(
                checked = livePreview,
                onCheckedChange = {
                    livePreview = it
                    previewIfEnabled()
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(
                    checked = persist,
                    onCheckedChange = { persist = it }
                )
                Text("保存为默认", color = textColor)
            }
            Button(onClick = { onApply(draftConfig(), persist) }) {
                Text("应用")
            }
        }
    }
}

@Composable
private fun PageTurnGlyph(
    label: String,
    tint: Color,
    modifier: Modifier = Modifier.size(32.dp)
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.08f
        when (label) {
            "仿真翻页" -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.18f, size.height * 0.12f),
                    size = Size(size.width * 0.58f, size.height * 0.76f),
                    cornerRadius = CornerRadius(size.minDimension * 0.06f),
                    style = Stroke(width = stroke)
                )
                val fold = Path().apply {
                    moveTo(size.width * 0.62f, size.height * 0.44f)
                    lineTo(size.width * 0.84f, size.height * 0.6f)
                    lineTo(size.width * 0.62f, size.height * 0.6f)
                }
                drawPath(path = fold, color = tint, style = Stroke(width = stroke, join = StrokeJoin.Round))
            }

            "左右覆盖" -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.12f, size.height * 0.14f),
                    size = Size(size.width * 0.52f, size.height * 0.72f),
                    cornerRadius = CornerRadius(size.minDimension * 0.05f),
                    style = Stroke(width = stroke)
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.34f, size.height * 0.14f),
                    size = Size(size.width * 0.52f, size.height * 0.72f),
                    cornerRadius = CornerRadius(size.minDimension * 0.05f),
                    style = Stroke(width = stroke)
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.54f, size.height * 0.64f),
                    end = Offset(size.width * 0.46f, size.height * 0.54f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.46f, size.height * 0.54f),
                    end = Offset(size.width * 0.54f, size.height * 0.44f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }

            else -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.16f, size.height * 0.12f),
                    size = Size(size.width * 0.68f, size.height * 0.76f),
                    cornerRadius = CornerRadius(size.minDimension * 0.05f),
                    style = Stroke(width = stroke)
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.3f, size.height * 0.36f),
                    end = Offset(size.width * 0.7f, size.height * 0.36f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.3f, size.height * 0.52f),
                    end = Offset(size.width * 0.7f, size.height * 0.52f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.3f, size.height * 0.68f),
                    end = Offset(size.width * 0.58f, size.height * 0.68f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun MoreBackgroundPanel(
    isNightMode: Boolean,
    selectedPreset: ReaderBackgroundPreset,
    onBack: () -> Unit,
    onSelectBackground: (ReaderBackgroundPreset) -> Unit
) {
    val backgrounds = listOf(
        ReaderBackgroundPreset.SYSTEM to if (isNightMode) Color(0xFF131313) else Color(0xFFFDF9F3),
        ReaderBackgroundPreset.PAPER to Color(0xFFFDF9F3),
        ReaderBackgroundPreset.WARM to Color(0xFFF3E7CA),
        ReaderBackgroundPreset.GREEN to Color(0xFFCCE0D1),
        ReaderBackgroundPreset.DARK to Color(0xFF2B2B2B),
        ReaderBackgroundPreset.NAVY to Color(0xFF1A1F2B)
    )
    val textColor = if (isNightMode) Color(0xFFE9E5DE) else Color(0xFF1E1C18)
    val unselectedBorder = if (isNightMode) Color(0x2EFFFFFF) else ReaderTokens.Palette.PrototypeBorder
    Column(
        modifier = Modifier
            .fillMaxHeight(0.65f)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SheetHeader(title = "更多背景", onBack = onBack)
        Text("背景色", fontWeight = FontWeight.Medium, color = textColor)
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height(120.dp)
        ) {
            items(backgrounds.size) { index ->
                val (preset, color) = backgrounds[index]
                val selected = preset == selectedPreset
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(color, CircleShape)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) {
                                ReaderTokens.Palette.PrototypeBlue
                            } else {
                                unselectedBorder
                            },
                            shape = CircleShape
                        )
                        .padding(1.dp)
                        .clickable { onSelectBackground(preset) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(color, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun FixedLayoutSettings(
    config: RenderConfig.FixedPage,
    isNightMode: Boolean,
    onApply: (RenderConfig, persist: Boolean) -> Unit
) {
    var persist by remember { mutableStateOf(true) }
    var zoom by remember(config.zoom) { mutableFloatStateOf(config.zoom) }
    var rotation by remember(config.rotationDegrees) { mutableIntStateOf(config.rotationDegrees) }
    var fitMode by remember(config.fitMode) { mutableStateOf(config.fitMode) }
    val textColor = if (isNightMode) Color(0xFFE9E5DE) else Color(0xFF1E1C18)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("版式设置", color = textColor)
        Text("缩放 ${"%.2f".format(zoom)}", color = textColor)
        Slider(
            value = zoom,
            valueRange = 0.6f..4f,
            onValueChange = {
                zoom = it
                onApply(config.copy(zoom = it, rotationDegrees = rotation, fitMode = fitMode), false)
            }
        )

        Text("旋转", color = textColor)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 90, 180, 270).forEach { degree ->
                OutlinedButton(onClick = {
                    rotation = degree
                    onApply(config.copy(zoom = zoom, rotationDegrees = degree, fitMode = fitMode), false)
                }) {
                    Text("$degree")
                }
            }
        }

        Text("适配", color = textColor)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RenderConfig.FitMode.entries.forEach { mode ->
                OutlinedButton(onClick = {
                    fitMode = mode
                    onApply(config.copy(zoom = zoom, rotationDegrees = rotation, fitMode = mode), false)
                }) {
                    Text(mode.name)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(
                checked = persist,
                onCheckedChange = { persist = it }
            )
            Text("保存为默认", color = textColor)
        }
        Button(
            onClick = {
                onApply(config.copy(zoom = zoom, rotationDegrees = rotation, fitMode = fitMode), persist)
            }
        ) {
            Text("应用")
        }
    }
}

@Composable
private fun SheetHeader(
    title: String,
    onBack: () -> Unit,
    actionText: String = "",
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(text = title, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        if (actionText.isBlank()) {
            Box(modifier = Modifier.width(42.dp))
        } else {
            TextButton(onClick = onActionClick ?: {}) {
                Text(actionText)
            }
        }
    }
}

@Composable
private fun SettingSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    textColor: Color,
    onChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = textColor)
            Text(valueLabel, color = textColor.copy(alpha = 0.7f))
        }
        Slider(value = value, valueRange = range, onValueChange = onChange)
    }
}

@Composable
private fun ChoiceButton(
    label: String,
    selected: Boolean,
    isNightMode: Boolean,
    onClick: () -> Unit
) {
    val selectedColors = ButtonDefaults.buttonColors(
        containerColor = ReaderTokens.Palette.PrototypeBlue,
        contentColor = Color.White
    )
    val unselectedText = if (isNightMode) Color(0xFFE9E5DE) else Color(0xFF24201C)

    if (selected) {
        Button(onClick = onClick, colors = selectedColors) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Text(label, color = unselectedText)
        }
    }
}
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\ui\components\TextPage.kt

```kotlin
package com.ireader.feature.reader.ui.components

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ireader.core.common.android.typography.prefersInterCharacterJustify
import com.ireader.core.common.android.typography.effectiveForInterCharacterScript
import com.ireader.core.common.android.typography.toAndroidBreakStrategy
import com.ireader.core.common.android.typography.toAndroidHyphenationFrequency
import com.ireader.core.common.android.typography.toAndroidJustificationMode
import com.ireader.reader.api.annotation.Decoration
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.toTypographySpec
import com.ireader.reader.model.DocumentLink
import kotlin.math.roundToInt

@Composable
fun TextPage(
    content: RenderContent.Text,
    links: List<DocumentLink>,
    decorations: List<Decoration>,
    reflowConfig: RenderConfig.ReflowText?,
    textColor: Color,
    backgroundColor: Color,
    onTextViewBound: (TextView) -> Unit,
    modifier: Modifier = Modifier
) {
    val config = reflowConfig ?: RenderConfig.ReflowText()
    val typography = config.toTypographySpec()
    val density = LocalDensity.current
    val horizontalPaddingPx = with(density) { typography.pagePaddingDp.dp.roundToPx() }
    val verticalPaddingPx = horizontalPaddingPx

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            TextView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                isFocusable = false
                isClickable = false
                overScrollMode = View.OVER_SCROLL_NEVER
                gravity = Gravity.TOP or Gravity.START
                onTextViewBound(this)
            }
        },
        update = { textView ->
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, typography.fontSizeSp)
            textView.setLineSpacing(0f, typography.lineHeightMult)
            textView.includeFontPadding = typography.includeFontPadding
            textView.setPadding(
                horizontalPaddingPx,
                verticalPaddingPx,
                horizontalPaddingPx,
                verticalPaddingPx
            )
            textView.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            textView.gravity = Gravity.TOP or Gravity.START
            textView.setTextColor(textColor.toArgb())
            textView.setBackgroundColor(backgroundColor.toArgb())
            val preferInterCharacterJustify = content.text.prefersInterCharacterJustify()
            val effectiveBreakStrategy = typography.breakStrategy
                .effectiveForInterCharacterScript(preferInterCharacterJustify)
            val familyName = typography.fontFamilyName
            textView.typeface = if (familyName.isNullOrBlank()) {
                Typeface.DEFAULT
            } else {
                Typeface.create(familyName, Typeface.NORMAL)
            }
            textView.text = buildDisplayText(
                content = content,
                links = links,
                decorations = decorations
            )
            textView.breakStrategy = effectiveBreakStrategy.toAndroidBreakStrategy()
            textView.hyphenationFrequency = typography.hyphenationMode.toAndroidHyphenationFrequency()
            runCatching {
                textView.justificationMode = typography.textAlign.toAndroidJustificationMode(
                    preferInterCharacter = preferInterCharacterJustify
                )
            }
            onTextViewBound(textView)
            textView.requestLayout()
        }
    )
}

private fun buildDisplayText(
    content: RenderContent.Text,
    links: List<DocumentLink>,
    decorations: List<Decoration>
): CharSequence {
    val source = content.text
    val mapping = content.mapping ?: return source
    val spannable = SpannableString(source)

    links.forEach { link ->
        val locatorRange = link.range ?: return@forEach
        val charRange = mapping.charRangeFor(locatorRange) ?: return@forEach
        applyRange(spannable, charRange) { start, end ->
            spannable.setSpan(
                ForegroundColorSpan(LINK_COLOR_ARGB),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                UnderlineSpan(),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    decorations.filterIsInstance<Decoration.Reflow>().forEach { decoration ->
        val charRange = mapping.charRangeFor(decoration.range) ?: return@forEach
        val highlightColor = resolveHighlightColor(decoration)
        applyRange(spannable, charRange) { start, end ->
            spannable.setSpan(
                BackgroundColorSpan(highlightColor),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    return spannable
}

private inline fun applyRange(
    spannable: SpannableString,
    range: IntRange,
    block: (start: Int, endExclusive: Int) -> Unit
) {
    if (range.isEmpty()) return
    val length = spannable.length
    if (length <= 0) return
    val start = range.first.coerceIn(0, length - 1)
    val endExclusive = (range.last + 1).coerceIn(0, length)
    if (start >= endExclusive) return
    block(start, endExclusive)
}

private fun resolveHighlightColor(decoration: Decoration.Reflow): Int {
    val base = decoration.style.colorArgb ?: DEFAULT_HIGHLIGHT_COLOR_ARGB
    val baseAlpha = (base ushr 24) and 0xFF
    val opacity = decoration.style.opacity
    val alpha = when {
        opacity != null -> {
            (opacity.coerceIn(0f, 1f) * 255f).roundToInt()
        }

        decoration.style.colorArgb != null -> baseAlpha
        else -> (DEFAULT_HIGHLIGHT_OPACITY * 255f).roundToInt()
    }.coerceIn(0, 255)
    return (base and 0x00FF_FFFF) or (alpha shl 24)
}

private const val LINK_COLOR_ARGB: Int = 0xFF2D6CDF.toInt()
private const val DEFAULT_HIGHLIGHT_COLOR_ARGB: Int = 0xFFFFD54F.toInt()
private const val DEFAULT_HIGHLIGHT_OPACITY: Float = 0.35f
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\ui\components\TilesPage.kt

```kotlin
package com.ireader.feature.reader.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.ireader.reader.api.annotation.Decoration
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.api.render.TileRequest
import com.ireader.reader.model.DocumentLink
import com.ireader.reader.model.Locator
import com.ireader.reader.model.NormalizedPoint
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TilesPage(
    pageId: String,
    content: RenderContent.Tiles,
    links: List<DocumentLink>,
    decorations: List<Decoration>,
    pageLocator: Locator,
    onBackgroundTap: (Offset, IntSize) -> Unit,
    onLinkActivated: (DocumentLink) -> Unit,
    onSelectionStart: (Locator) -> Unit,
    onSelectionFinish: () -> Unit,
    onSelectionClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val bitmaps = remember(pageId) { mutableStateMapOf<TileKey, Bitmap>() }
    val inflight = remember(pageId) { mutableStateMapOf<TileKey, Job>() }

    var zoom by remember(pageId) { mutableFloatStateOf(1f) }
    var offset by remember(pageId) { mutableStateOf(Offset.Zero) }
    var isGesturing by remember(pageId) { mutableStateOf(false) }
    var settleJob by remember(pageId) { mutableStateOf<Job?>(null) }

    DisposableEffect(pageId, content.tileProvider) {
        onDispose {
            settleJob?.cancel()
            inflight.values.forEach { it.cancel() }
            inflight.clear()
            bitmaps.values.forEach { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
            bitmaps.clear()
            runCatching { content.tileProvider.close() }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val viewportWidth = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val viewportHeight = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

        val pageWidth = content.pageWidthPx.toFloat().coerceAtLeast(1f)
        val pageHeight = content.pageHeightPx.toFloat().coerceAtLeast(1f)
        val drawScale = zoom
        val drawWidth = pageWidth * drawScale
        val drawHeight = pageHeight * drawScale
        val pageLeft = (viewportWidth - drawWidth) / 2f + offset.x
        val pageTop = (viewportHeight - drawHeight) / 2f + offset.y
        val normalizedScale = (drawScale * 100f).roundToInt() / 100f
        val quality = if (isGesturing) RenderPolicy.Quality.DRAFT else RenderPolicy.Quality.FINAL

        val tileBaseSize = content.baseTileSizePx.coerceAtLeast(128).toFloat()
        val tilePageSize = (tileBaseSize / drawScale).coerceIn(96f, 2048f)
        val leftPage = ((0f - pageLeft) / drawScale).coerceIn(0f, pageWidth)
        val topPage = ((0f - pageTop) / drawScale).coerceIn(0f, pageHeight)
        val rightPage = ((viewportWidth - pageLeft) / drawScale).coerceIn(0f, pageWidth)
        val bottomPage = ((viewportHeight - pageTop) / drawScale).coerceIn(0f, pageHeight)

        val startX = (leftPage / tilePageSize).toInt().coerceAtLeast(0)
        val endX = ceil(rightPage / tilePageSize).toInt().coerceAtLeast(startX)
        val startY = (topPage / tilePageSize).toInt().coerceAtLeast(0)
        val endY = ceil(bottomPage / tilePageSize).toInt().coerceAtLeast(startY)

        val needed = remember(
            pageId,
            startX,
            endX,
            startY,
            endY,
            tilePageSize,
            normalizedScale,
            quality
        ) {
            buildList {
                for (y in startY..endY) {
                    for (x in startX..endX) {
                        val left = (x * tilePageSize).toInt()
                        val top = (y * tilePageSize).toInt()
                        val width = min(tilePageSize.toInt(), content.pageWidthPx - left).coerceAtLeast(1)
                        val height = min(tilePageSize.toInt(), content.pageHeightPx - top).coerceAtLeast(1)
                        if (width <= 0 || height <= 0) continue
                        add(
                            TileKey(
                                leftPx = left,
                                topPx = top,
                                widthPx = width,
                                heightPx = height,
                                scale = normalizedScale,
                                quality = quality
                            )
                        )
                    }
                }
            }
        }

        LaunchedEffect(needed) {
            val neededSet = needed.toSet()
            inflight.entries
                .toList()
                .filter { (key, _) -> key !in neededSet }
                .forEach { (key, job) ->
                    job.cancel()
                    inflight.remove(key)
                }

            bitmaps.entries
                .toList()
                .filter { (key, _) -> key !in neededSet }
                .forEach { (key, bitmap) ->
                    bitmaps.remove(key)
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }

            needed.forEach { key ->
                if (bitmaps.containsKey(key) || inflight.containsKey(key)) return@forEach
                val requestJob = scope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        content.tileProvider.renderTile(
                            TileRequest(
                                leftPx = key.leftPx,
                                topPx = key.topPx,
                                widthPx = key.widthPx,
                                heightPx = key.heightPx,
                                scale = key.scale,
                                quality = key.quality
                            )
                        )
                    }

                    bitmaps.put(key, bitmap)?.let { previous ->
                        if (previous != bitmap && !previous.isRecycled) {
                            previous.recycle()
                        }
                    }
                }
                inflight[key] = requestJob
                requestJob.invokeOnCompletion { inflight.remove(key) }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pageId, links, pageWidth, pageHeight, drawScale, pageLeft, pageTop) {
                    detectTapGestures(
                        onTap = { tap ->
                            onSelectionClear()
                            val link = hitTestLink(
                                tap = tap,
                                links = links,
                                pageWidth = pageWidth,
                                pageHeight = pageHeight,
                                drawScale = drawScale,
                                pageLeft = pageLeft,
                                pageTop = pageTop
                            )
                            if (link != null) {
                                onLinkActivated(link)
                            } else {
                                onBackgroundTap(tap, size)
                            }
                        },
                        onLongPress = { tap ->
                            val normalized = normalizedPagePointOrNull(
                                tap = tap,
                                pageWidth = pageWidth,
                                pageHeight = pageHeight,
                                drawScale = drawScale,
                                pageLeft = pageLeft,
                                pageTop = pageTop
                            ) ?: return@detectTapGestures
                            onSelectionStart(
                                pageLocator.copy(
                                    extras = pageLocator.extras + mapOf(
                                        "hitX" to normalized.x.toString(),
                                        "hitY" to normalized.y.toString()
                                    )
                                )
                            )
                            onSelectionFinish()
                        }
                    )
                }
                .pointerInput(pageId) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        val oldZoom = zoom
                        val nextZoom = (zoom * gestureZoom).coerceIn(0.7f, 6.0f)
                        val oldCenteredX = (viewportWidth - pageWidth * oldZoom) / 2f
                        val oldCenteredY = (viewportHeight - pageHeight * oldZoom) / 2f
                        val before = Offset(
                            x = (centroid.x - (oldCenteredX + offset.x)) / oldZoom,
                            y = (centroid.y - (oldCenteredY + offset.y)) / oldZoom
                        )
                        val nextCenteredX = (viewportWidth - pageWidth * nextZoom) / 2f
                        val nextCenteredY = (viewportHeight - pageHeight * nextZoom) / 2f
                        zoom = nextZoom
                        offset = Offset(
                            x = centroid.x - nextCenteredX - before.x * nextZoom + pan.x,
                            y = centroid.y - nextCenteredY - before.y * nextZoom + pan.y
                        )

                        isGesturing = true
                        settleJob?.cancel()
                        settleJob = scope.launch {
                            delay(140L)
                            isGesturing = false
                        }
                    }
                }
        ) {
            needed.forEach { key ->
                val bitmap = bitmaps[key] ?: return@forEach
                if (bitmap.isRecycled) return@forEach

                val dstLeft = (pageLeft + key.leftPx * drawScale).roundToInt()
                val dstTop = (pageTop + key.topPx * drawScale).roundToInt()
                val dstWidth = (key.widthPx * drawScale).roundToInt().coerceAtLeast(1)
                val dstHeight = (key.heightPx * drawScale).roundToInt().coerceAtLeast(1)

                drawImage(
                    image = bitmap.asImageBitmap(),
                    srcOffset = IntOffset(0, 0),
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset(dstLeft, dstTop),
                    dstSize = IntSize(dstWidth, dstHeight)
                )
            }

            drawFixedDecorations(
                decorations = decorations,
                pageLeft = pageLeft,
                pageTop = pageTop,
                drawWidth = drawWidth,
                drawHeight = drawHeight
            )
        }
    }
}

private fun hitTestLink(
    tap: Offset,
    links: List<DocumentLink>,
    pageWidth: Float,
    pageHeight: Float,
    drawScale: Float,
    pageLeft: Float,
    pageTop: Float
): DocumentLink? {
    val point = normalizedPagePointOrNull(
        tap = tap,
        pageWidth = pageWidth,
        pageHeight = pageHeight,
        drawScale = drawScale,
        pageLeft = pageLeft,
        pageTop = pageTop
    ) ?: return null
    return links.firstOrNull { link ->
        link.bounds.orEmpty().any { rect -> rect.contains(point) }
    }
}

private fun normalizedPagePointOrNull(
    tap: Offset,
    pageWidth: Float,
    pageHeight: Float,
    drawScale: Float,
    pageLeft: Float,
    pageTop: Float
): NormalizedPoint? {
    val pageX = (tap.x - pageLeft) / drawScale
    val pageY = (tap.y - pageTop) / drawScale
    if (pageX !in 0f..pageWidth || pageY !in 0f..pageHeight) return null
    return NormalizedPoint(
        x = (pageX / pageWidth).coerceIn(0f, 1f),
        y = (pageY / pageHeight).coerceIn(0f, 1f)
    )
}

private data class TileKey(
    val leftPx: Int,
    val topPx: Int,
    val widthPx: Int,
    val heightPx: Int,
    val scale: Float,
    val quality: RenderPolicy.Quality
)

private fun DrawScope.drawFixedDecorations(
    decorations: List<Decoration>,
    pageLeft: Float,
    pageTop: Float,
    drawWidth: Float,
    drawHeight: Float
) {
    decorations.filterIsInstance<Decoration.Fixed>().forEach { fixed ->
        val fillColor = fixedOverlayColor(fixed)
        fixed.rects.forEach { rect ->
            val normalizedLeft = rect.left.coerceIn(0f, 1f)
            val normalizedTop = rect.top.coerceIn(0f, 1f)
            val normalizedRight = rect.right.coerceIn(0f, 1f)
            val normalizedBottom = rect.bottom.coerceIn(0f, 1f)
            if (normalizedRight <= normalizedLeft || normalizedBottom <= normalizedTop) {
                return@forEach
            }
            val left = pageLeft + normalizedLeft * drawWidth
            val top = pageTop + normalizedTop * drawHeight
            val width = (normalizedRight - normalizedLeft) * drawWidth
            val height = (normalizedBottom - normalizedTop) * drawHeight
            if (width <= 0f || height <= 0f) {
                return@forEach
            }
            drawRect(
                color = Color(fillColor),
                topLeft = Offset(left, top),
                size = Size(width, height)
            )
        }
    }
}

private fun fixedOverlayColor(decoration: Decoration.Fixed): Int {
    val base = decoration.style.colorArgb ?: DEFAULT_FIXED_OVERLAY_COLOR_ARGB
    val baseAlpha = (base ushr 24) and 0xFF
    val opacity = decoration.style.opacity
    val alpha = when {
        opacity != null -> {
            (opacity.coerceIn(0f, 1f) * 255f).roundToInt()
        }

        decoration.style.colorArgb != null -> baseAlpha
        else -> (DEFAULT_FIXED_OVERLAY_OPACITY * 255f).roundToInt()
    }.coerceIn(0, 255)
    return (base and 0x00FF_FFFF) or (alpha shl 24)
}

private const val DEFAULT_FIXED_OVERLAY_COLOR_ARGB: Int = 0xFFFFD54F.toInt()
private const val DEFAULT_FIXED_OVERLAY_OPACITY: Float = 0.35f
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\ui\ReaderChromeDefaults.kt

```kotlin
package com.ireader.feature.reader.ui

internal enum class ReaderTopActionIcon {
    Search,
    Note
}

internal object ReaderChromeDefaults {
    val topSearchIcon: ReaderTopActionIcon = ReaderTopActionIcon.Search
    val topNotesIcon: ReaderTopActionIcon = ReaderTopActionIcon.Note
}
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\ui\ReaderScaffold.kt

```kotlin
package com.ireader.feature.reader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import com.ireader.core.datastore.reader.ReaderBackgroundPreset
import com.ireader.core.datastore.reader.ReaderDisplayPrefs
import com.ireader.core.designsystem.PrototypeIcons
import com.ireader.core.designsystem.ReaderTokens
import com.ireader.feature.reader.presentation.ReaderErrorAction
import com.ireader.feature.reader.presentation.ReaderDockTab
import com.ireader.feature.reader.presentation.ReaderIntent
import com.ireader.feature.reader.presentation.ReaderMenuTab
import com.ireader.feature.reader.presentation.ReaderSheet
import com.ireader.feature.reader.presentation.ReaderUiState
import com.ireader.feature.reader.presentation.asString
import com.ireader.feature.reader.ui.components.ErrorPane
import com.ireader.feature.reader.ui.components.PageRenderer
import com.ireader.feature.reader.ui.components.PasswordDialog
import com.ireader.feature.reader.ui.components.ReaderSettingsPanel
import com.ireader.feature.reader.ui.components.SearchSheet
import com.ireader.feature.reader.ui.components.SettingsSheet
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ContextWrapper
import android.app.Activity
import android.os.BatteryManager
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import com.ireader.reader.api.render.LayoutConstraints

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScaffold(
    state: ReaderUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onIntent: (ReaderIntent) -> Unit,
    onOpenLocator: (String) -> Unit,
    onReadingViewportChanged: (LayoutConstraints) -> Unit
) {
    val prefs = state.displayPrefs
    val bgColor = resolveReaderBackgroundColor(
        nightMode = state.isNightMode,
        preset = prefs.backgroundPreset
    )
    val darkSurface = state.isNightMode || prefs.backgroundPreset in setOf(
        ReaderBackgroundPreset.DARK,
        ReaderBackgroundPreset.NAVY
    )
    val panelColor = if (darkSurface) {
        Color(0xFF1E1E1E)
    } else {
        ReaderTokens.Palette.PrototypeSurface
    }
    val panelBorder = if (darkSurface) Color(0xFF3A3A3A) else ReaderTokens.Palette.PrototypeBorder
    val panelTextColor = if (darkSurface) {
        Color(0xFFE0DDD8)
    } else {
        ReaderTokens.Palette.PrototypeTextPrimary
    }
    val readerTextColor = if (darkSurface) {
        ReaderTokens.Palette.ReaderTextNight
    } else {
        ReaderTokens.Palette.ReaderTextDay
    }
    val footerTextColor = if (darkSurface) Color(0xFF8E8B84) else Color(0xFF87817B)
    val clock = rememberClockText()
    val batteryPercent = rememberBatteryPercent()
    val density = LocalDensity.current
    val readingViewportSize = remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(readingViewportSize.value, density.density, density.fontScale) {
        val size = readingViewportSize.value
        if (size.width <= 0 || size.height <= 0) {
            return@LaunchedEffect
        }
        onReadingViewportChanged(
            LayoutConstraints(
                viewportWidthPx = size.width,
                viewportHeightPx = size.height,
                density = density.density,
                fontScale = density.fontScale
            )
        )
    }

    ApplyReaderBrightness(prefs = prefs)
    ApplyReaderSystemBars(
        prefs = prefs,
        chromeVisible = state.chromeVisible,
        isReadingLayer = state.layerState == com.ireader.feature.reader.presentation.ReaderLayerState.Reading
    )

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = bgColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
                    .padding(top = 8.dp, bottom = 32.dp)
                    .onSizeChanged { readingViewportSize.value = it }
            ) {
                PageRenderer(
                    state = state,
                    textColor = readerTextColor,
                    backgroundColor = bgColor,
                    onBackgroundTap = { tap, size ->
                        onIntent(
                            ReaderIntent.HandleTap(
                                xPx = tap.x,
                                yPx = tap.y,
                                viewportWidthPx = size.width,
                                viewportHeightPx = size.height
                            )
                        )
                    },
                    onDragEnd = { axis, deltaPx, viewportMainAxisPx ->
                        onIntent(
                            ReaderIntent.HandleDragEnd(
                                axis = axis,
                                deltaPx = deltaPx,
                                viewportMainAxisPx = viewportMainAxisPx
                            )
                        )
                    },
                    onLinkActivated = { link -> onIntent(ReaderIntent.ActivateLink(link)) },
                    onSelectionStart = { locator ->
                        onIntent(ReaderIntent.SelectionStart(locator))
                    },
                    onSelectionUpdate = { locator ->
                        onIntent(ReaderIntent.SelectionUpdate(locator))
                    },
                    onSelectionFinish = {
                        onIntent(ReaderIntent.SelectionFinish)
                    },
                    onSelectionClear = {
                        onIntent(ReaderIntent.ClearSelection)
                    },
                )
            }

            if (prefs.eyeProtection) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x22FFB74D))
                )
            }

            if (state.isOpening) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            if (state.isRenderingFinal) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                )
            }

            ReaderFooter(
                modifier = Modifier.align(Alignment.BottomCenter),
                progression = state.renderState?.progression?.percent?.toFloat()?.coerceIn(0f, 1f) ?: 0f,
                showProgress = prefs.showReadingProgress,
                currentTimeText = clock,
                batteryPercent = batteryPercent,
                textColor = footerTextColor
            )
            if (state.overlayState.showGestureHint) {
                GestureHintBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    isNightMode = state.isNightMode
                )
            }

            AnimatedVisibility(
                visible = state.overlayState.showTopBar && state.sheet != ReaderSheet.FullSettings,
                enter = fadeIn(animationSpec = tween(ReaderTokens.Motion.ChromeIn)) +
                    slideInVertically(initialOffsetY = { -it / 2 }, animationSpec = tween(ReaderTokens.Motion.ChromeIn)),
                exit = fadeOut(animationSpec = tween(ReaderTokens.Motion.ChromeOut)) +
                    slideOutVertically(targetOffsetY = { -it / 2 }, animationSpec = tween(ReaderTokens.Motion.ChromeOut)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                ReaderTopBar(
                    title = state.title ?: "阅读中",
                    panelColor = panelColor.copy(alpha = 0.98f),
                    panelBorderColor = panelBorder,
                    contentColor = panelTextColor,
                    searchIcon = ReaderChromeDefaults.topSearchIcon,
                    notesIcon = ReaderChromeDefaults.topNotesIcon,
                    onBack = onBack,
                    onOpenSearch = { onIntent(ReaderIntent.OpenSearch) },
                    onOpenAnnotations = { onIntent(ReaderIntent.OpenAnnotations) },
                    onMore = { onIntent(ReaderIntent.OpenReaderMore) }
                )
            }

            AnimatedVisibility(
                visible = state.overlayState.showBottomBar && state.sheet != ReaderSheet.FullSettings,
                enter = fadeIn(animationSpec = tween(ReaderTokens.Motion.ChromeIn)) +
                    slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(ReaderTokens.Motion.ChromeIn)),
                exit = fadeOut(animationSpec = tween(ReaderTokens.Motion.ChromeOut)) +
                    slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = tween(ReaderTokens.Motion.ChromeOut)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AnimatedVisibility(
                        visible = state.activeDockTab != null,
                        enter = fadeIn(animationSpec = tween(ReaderTokens.Motion.SheetEnter)) +
                            slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(ReaderTokens.Motion.SheetEnter)),
                        exit = fadeOut(animationSpec = tween(ReaderTokens.Motion.SheetExit)) +
                            slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = tween(ReaderTokens.Motion.SheetExit))
                    ) {
                        val dockTab = state.activeDockTab
                        if (dockTab != null) {
                            ReaderDockPanel(
                                tab = dockTab,
                                state = state,
                                panelColor = panelColor.copy(alpha = 0.995f),
                                panelBorderColor = panelBorder,
                                contentColor = panelTextColor,
                                onClose = { onIntent(ReaderIntent.CloseDockPanel) },
                                onOpenLocator = { encoded ->
                                    onOpenLocator(encoded)
                                    onIntent(ReaderIntent.CloseDockPanel)
                                },
                                onSetMenuTab = { tab -> onIntent(ReaderIntent.SetMenuTab(tab)) },
                                onOpenAnnotations = { onIntent(ReaderIntent.OpenAnnotations) },
                                onBrightnessChange = { onIntent(ReaderIntent.UpdateBrightness(it)) },
                                onUseSystemBrightnessChange = { onIntent(ReaderIntent.SetUseSystemBrightness(it)) },
                                onEyeProtectionChange = { onIntent(ReaderIntent.SetEyeProtection(it)) },
                                onOpenSubPanel = { panel ->
                                    onIntent(ReaderIntent.OpenSettingsSub(panel.toSheet()))
                                },
                                onOpenFullSettings = { onIntent(ReaderIntent.OpenFullSettings) },
                                onApplyConfig = { cfg, persist ->
                                    onIntent(ReaderIntent.UpdateConfig(cfg, persist))
                                }
                            )
                        }
                    }
                    ReaderBottomBar(
                        activeDockTab = state.activeDockTab,
                        isNightMode = state.isNightMode,
                        panelColor = panelColor.copy(alpha = 0.98f),
                        panelBorderColor = panelBorder,
                        onToggleDockTab = { tab -> onIntent(ReaderIntent.ToggleDockTab(tab)) },
                        onToggleNight = { onIntent(ReaderIntent.ToggleNightMode) }
                    )
                }
            }

            val error = state.error
            if (error != null) {
                ErrorPane(
                    error = error,
                    onAction = { action ->
                        when (action) {
                            ReaderErrorAction.Retry -> onIntent(ReaderIntent.RetryOpen)
                            ReaderErrorAction.Back -> onBack()
                        }
                    }
                )
            }
        }

        val passwordPrompt = state.passwordPrompt
        if (passwordPrompt != null) {
            PasswordDialog(
                prompt = passwordPrompt,
                onSubmit = { pwd -> onIntent(ReaderIntent.SubmitPassword(pwd)) },
                onCancel = { onIntent(ReaderIntent.CancelPassword) }
            )
        }

        when (state.sheet) {
            ReaderSheet.None,
            ReaderSheet.Toc,
            ReaderSheet.Brightness,
            ReaderSheet.Settings -> Unit

            ReaderSheet.Search -> SearchSheet(
                state = state.search,
                isNightMode = state.isNightMode,
                onClose = { onIntent(ReaderIntent.CloseSheet) },
                onQueryChange = { q -> onIntent(ReaderIntent.SearchQueryChanged(q)) },
                onSearch = { onIntent(ReaderIntent.ExecuteSearch) },
                onClickResult = { encoded ->
                    onOpenLocator(encoded)
                    onIntent(ReaderIntent.CloseSheet)
                }
            )

            ReaderSheet.SettingsFont,
            ReaderSheet.SettingsSpacing,
            ReaderSheet.SettingsPageTurn,
            ReaderSheet.SettingsMoreBackground -> {
                SettingsSheet(
                    panel = state.sheet.toSettingsPanel(),
                    capabilities = state.capabilities,
                    config = state.currentConfig,
                    isNightMode = state.isNightMode,
                    onClose = { onIntent(ReaderIntent.CloseSheet) },
                    onBackToMain = { onIntent(ReaderIntent.OpenSettings) },
                    backgroundPreset = prefs.backgroundPreset,
                    onSelectBackground = { preset -> onIntent(ReaderIntent.SelectBackground(preset)) },
                    onApply = { cfg, persist -> onIntent(ReaderIntent.UpdateConfig(cfg, persist)) }
                )
            }

            ReaderSheet.ReaderMore -> ReaderMoreSheet(
                panelColor = panelColor,
                textColor = panelTextColor,
                darkSurface = darkSurface,
                onClose = { onIntent(ReaderIntent.CloseSheet) },
                onOpenSearch = { onIntent(ReaderIntent.OpenSearch) },
                onCreateAnnotation = { onIntent(ReaderIntent.CreateAnnotation) },
                onShare = { onIntent(ReaderIntent.ShareBook) }
            )

            ReaderSheet.FullSettings -> FullSettingsScreen(
                prefs = prefs,
                isNightMode = state.isNightMode,
                onBack = { onIntent(ReaderIntent.BackInSheetHierarchy) },
                onShowReadingProgressChanged = { onIntent(ReaderIntent.SetReadingProgressVisible(it)) },
                onFullScreenModeChanged = { onIntent(ReaderIntent.SetFullScreenMode(it)) },
                onVolumeKeyPagingChanged = { onIntent(ReaderIntent.SetVolumeKeyPaging(it)) }
            )
        }
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    panelColor: Color,
    panelBorderColor: Color,
    contentColor: Color,
    searchIcon: ReaderTopActionIcon,
    notesIcon: ReaderTopActionIcon,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAnnotations: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .background(panelColor)
            .border(width = ReaderTokens.Border.Hairline, color = panelBorderColor)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = contentColor
            )
        ) {
            Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = contentColor)
        }
        Text(
            text = title,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(
                onClick = onOpenSearch,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = contentColor
                )
            ) {
                when (searchIcon) {
                    ReaderTopActionIcon.Search -> PrototypeIcons.Search(tint = contentColor)
                    ReaderTopActionIcon.Note -> PrototypeIcons.Note(tint = contentColor)
                }
            }
            IconButton(
                onClick = onOpenAnnotations,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = contentColor
                )
            ) {
                when (notesIcon) {
                    ReaderTopActionIcon.Search -> PrototypeIcons.Search(tint = contentColor)
                    ReaderTopActionIcon.Note -> PrototypeIcons.Note(tint = contentColor)
                }
            }
            IconButton(
                onClick = onMore,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = contentColor
                )
            ) {
                PrototypeIcons.MoreVertical(tint = contentColor)
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    activeDockTab: ReaderDockTab?,
    isNightMode: Boolean,
    panelColor: Color,
    panelBorderColor: Color,
    onToggleDockTab: (ReaderDockTab) -> Unit,
    onToggleNight: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(panelColor)
            .border(width = ReaderTokens.Border.Hairline, color = panelBorderColor)
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        BottomItem(
            label = "目录",
            selected = activeDockTab == ReaderDockTab.Menu,
            isNightMode = isNightMode,
            icon = {
                PrototypeIcons.MenuList()
            },
            onClick = { onToggleDockTab(ReaderDockTab.Menu) }
        )
        BottomItem(
            label = "亮度",
            selected = activeDockTab == ReaderDockTab.Brightness,
            isNightMode = isNightMode,
            icon = {
                PrototypeIcons.LightbulbBolt()
            },
            onClick = { onToggleDockTab(ReaderDockTab.Brightness) }
        )
        BottomItem(
            label = if (isNightMode) "日间" else "夜间",
            selected = false,
            isNightMode = isNightMode,
            icon = {
                PrototypeIcons.Moon()
            },
            onClick = onToggleNight
        )
        BottomItem(
            label = "设置",
            selected = activeDockTab == ReaderDockTab.Settings,
            isNightMode = isNightMode,
            icon = {
                PrototypeIcons.SettingsHex()
            },
            onClick = { onToggleDockTab(ReaderDockTab.Settings) }
        )
    }
}

@Composable
private fun BottomItem(
    label: String,
    selected: Boolean,
    isNightMode: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val selectedTint = ReaderTokens.Palette.PrototypeBlue
    val normalTint = if (isNightMode) ReaderTokens.Palette.PrototypeTextTertiary else ReaderTokens.Palette.PrototypeTextSecondary

    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val tint = if (selected) selectedTint else normalTint
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides tint) { icon() }
            }
            Text(
                text = label,
                color = tint,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun ReaderDockPanel(
    tab: ReaderDockTab,
    state: ReaderUiState,
    panelColor: Color,
    panelBorderColor: Color,
    contentColor: Color,
    onClose: () -> Unit,
    onOpenLocator: (String) -> Unit,
    onSetMenuTab: (ReaderMenuTab) -> Unit,
    onOpenAnnotations: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onUseSystemBrightnessChange: (Boolean) -> Unit,
    onEyeProtectionChange: (Boolean) -> Unit,
    onOpenSubPanel: (ReaderSettingsPanel) -> Unit,
    onOpenFullSettings: () -> Unit,
    onApplyConfig: (com.ireader.reader.api.render.RenderConfig, Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = ReaderTokens.Shape.PrototypeSheetTop,
            topEnd = ReaderTokens.Shape.PrototypeSheetTop
        ),
        color = panelColor,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = ReaderTokens.Border.Hairline, color = panelBorderColor)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val title = when (tab) {
                    ReaderDockTab.Menu -> "目录"
                    ReaderDockTab.Brightness -> "亮度"
                    ReaderDockTab.Settings -> "阅读设置"
                }
                Text(
                    text = title,
                    color = contentColor,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = onClose) {
                    Text("收起", color = contentColor.copy(alpha = 0.76f))
                }
            }
            when (tab) {
                ReaderDockTab.Menu -> ReaderMenuDockPanel(
                    state = state,
                    contentColor = contentColor,
                    onSetMenuTab = onSetMenuTab,
                    onOpenLocator = onOpenLocator,
                    onOpenAnnotations = onOpenAnnotations
                )
                ReaderDockTab.Brightness -> ReaderBrightnessDockPanel(
                    prefs = state.displayPrefs,
                    isNightMode = state.isNightMode,
                    contentColor = contentColor,
                    onBrightnessChange = onBrightnessChange,
                    onUseSystemBrightnessChange = onUseSystemBrightnessChange,
                    onEyeProtectionChange = onEyeProtectionChange
                )
                ReaderDockTab.Settings -> ReaderSettingsDockPanel(
                    state = state,
                    contentColor = contentColor,
                    onOpenSubPanel = onOpenSubPanel,
                    onOpenFullSettings = onOpenFullSettings,
                    onApplyConfig = onApplyConfig
                )
            }
        }
    }
}

@Composable
private fun ReaderMenuDockPanel(
    state: ReaderUiState,
    contentColor: Color,
    onSetMenuTab: (ReaderMenuTab) -> Unit,
    onOpenLocator: (String) -> Unit,
    onOpenAnnotations: () -> Unit
) {
    val tabBg = if (state.isNightMode) Color(0xFF2E2E2E) else ReaderTokens.Palette.PrototypeSurfaceMuted
    val dividerColor = if (state.isNightMode) Color(0xFF3A3A3A) else ReaderTokens.Palette.PrototypeBorder
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tabBg, RoundedCornerShape(999.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MenuTabButton(
            label = "目录",
            selected = state.activeMenuTab == ReaderMenuTab.Toc,
            contentColor = contentColor,
            onClick = { onSetMenuTab(ReaderMenuTab.Toc) },
            modifier = Modifier.weight(1f)
        )
        MenuTabButton(
            label = "笔记",
            selected = state.activeMenuTab == ReaderMenuTab.Notes,
            contentColor = contentColor,
            onClick = { onSetMenuTab(ReaderMenuTab.Notes) },
            modifier = Modifier.weight(1f)
        )
        MenuTabButton(
            label = "书签",
            selected = state.activeMenuTab == ReaderMenuTab.Bookmarks,
            contentColor = contentColor,
            onClick = { onSetMenuTab(ReaderMenuTab.Bookmarks) },
            modifier = Modifier.weight(1f)
        )
    }

    when (state.activeMenuTab) {
        ReaderMenuTab.Toc -> {
            when {
                state.toc.isLoading -> {
                    Text("目录加载中...", color = contentColor.copy(alpha = 0.72f))
                }
                state.toc.error != null -> {
                    Text(state.toc.error.asString(), color = Color(0xFFE0614F))
                }
                state.toc.items.isEmpty() -> {
                    Text("暂无目录数据", color = contentColor.copy(alpha = 0.72f))
                }
                else -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "已完结 共${state.toc.items.size}章",
                            color = contentColor.copy(alpha = 0.62f),
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = "下载",
                                color = contentColor.copy(alpha = 0.62f),
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = "正序",
                                color = contentColor.copy(alpha = 0.62f),
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        itemsIndexed(state.toc.items) { index, item ->
                            TextButton(
                                onClick = { onOpenLocator(item.locatorEncoded) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = item.title,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = (item.depth * 10).dp),
                                    color = if (index == 0) ReaderTokens.Palette.PrototypeBlue else contentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (index < state.toc.items.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(dividerColor)
                                )
                            }
                        }
                    }
                }
            }
        }

        ReaderMenuTab.Notes -> {
            ReaderDockPlaceholder(
                title = "暂无笔记",
                desc = "可在阅读时选中文本后添加笔记",
                action = "打开笔记页",
                contentColor = contentColor,
                onAction = onOpenAnnotations
            )
        }

        ReaderMenuTab.Bookmarks -> {
            ReaderDockPlaceholder(
                title = "暂无书签",
                desc = "在更多功能中添加后会显示在这里",
                action = "继续阅读",
                contentColor = contentColor,
                onAction = {}
            )
        }
    }
}

@Composable
private fun ReaderBrightnessDockPanel(
    prefs: ReaderDisplayPrefs,
    isNightMode: Boolean,
    contentColor: Color,
    onBrightnessChange: (Float) -> Unit,
    onUseSystemBrightnessChange: (Boolean) -> Unit,
    onEyeProtectionChange: (Boolean) -> Unit
) {
    val rowBg = if (isNightMode) Color(0xFF2C2C2C) else ReaderTokens.Palette.PrototypeSurfaceMuted
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PrototypeIcons.LightbulbBolt(
            modifier = Modifier.size(22.dp),
            tint = contentColor.copy(alpha = 0.64f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Slider(
            value = prefs.brightness,
            onValueChange = onBrightnessChange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = ReaderTokens.Palette.PrototypeBlue,
                inactiveTrackColor = ReaderTokens.Palette.PrototypeBorder
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        PrototypeIcons.LightbulbBolt(
            modifier = Modifier.size(24.dp),
            tint = contentColor
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = rowBg
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("系统亮度", color = contentColor)
                Switch(checked = prefs.useSystemBrightness, onCheckedChange = onUseSystemBrightnessChange)
            }
        }
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = rowBg
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("护眼模式", color = contentColor)
                Switch(checked = prefs.eyeProtection, onCheckedChange = onEyeProtectionChange)
            }
        }
    }
}

@Composable
private fun ReaderSettingsDockPanel(
    state: ReaderUiState,
    contentColor: Color,
    onOpenSubPanel: (ReaderSettingsPanel) -> Unit,
    onOpenFullSettings: () -> Unit,
    onApplyConfig: (com.ireader.reader.api.render.RenderConfig, Boolean) -> Unit
) {
    val current = state.currentConfig as? com.ireader.reader.api.render.RenderConfig.ReflowText
    if (current == null) {
        ReaderDockPlaceholder(
            title = "当前文档为固定排版",
            desc = "可在完整设置页中调整阅读偏好",
            action = "打开完整设置",
            contentColor = contentColor,
            onAction = onOpenFullSettings
        )
        return
    }
    val actionBg = if (state.isNightMode) Color(0xFF2C2C2C) else ReaderTokens.Palette.PrototypeSurfaceMuted
    val actionBorder = if (state.isNightMode) Color(0xFF3A3A3A) else ReaderTokens.Palette.PrototypeBorder

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            color = actionBg
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        onApplyConfig(current.copy(fontSizeSp = (current.fontSizeSp - 1f).coerceAtLeast(12f)), false)
                    }
                ) {
                    Text("A-", color = contentColor)
                }
                Text(text = current.fontSizeSp.roundToInt().toString(), color = contentColor.copy(alpha = 0.75f))
                TextButton(
                    onClick = {
                        onApplyConfig(current.copy(fontSizeSp = (current.fontSizeSp + 1f).coerceAtMost(30f)), false)
                    }
                ) {
                    Text("A+", color = contentColor)
                }
            }
        }
        TextButton(
            onClick = { onOpenSubPanel(ReaderSettingsPanel.Font) },
            modifier = Modifier
                .background(actionBg, RoundedCornerShape(10.dp))
                .border(ReaderTokens.Border.Hairline, actionBorder, RoundedCornerShape(10.dp))
        ) {
            Text("字体", color = contentColor)
        }
        TextButton(
            onClick = { onOpenSubPanel(ReaderSettingsPanel.Spacing) },
            modifier = Modifier
                .background(actionBg, RoundedCornerShape(10.dp))
                .border(ReaderTokens.Border.Hairline, actionBorder, RoundedCornerShape(10.dp))
        ) {
            Text("间距", color = contentColor)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(
            onClick = { onOpenSubPanel(ReaderSettingsPanel.PageTurn) },
            modifier = Modifier
                .weight(1f)
                .background(actionBg, RoundedCornerShape(10.dp))
                .border(ReaderTokens.Border.Hairline, actionBorder, RoundedCornerShape(10.dp))
        ) {
            Text("翻页方式", color = contentColor)
        }
        TextButton(
            onClick = { onOpenSubPanel(ReaderSettingsPanel.MoreBackground) },
            modifier = Modifier
                .weight(1f)
                .background(actionBg, RoundedCornerShape(10.dp))
                .border(ReaderTokens.Border.Hairline, actionBorder, RoundedCornerShape(10.dp))
        ) {
            Text("更多背景", color = contentColor)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onOpenFullSettings) {
            Text("更多设置 >", color = contentColor.copy(alpha = 0.72f))
        }
    }
}

@Composable
private fun ReaderDockPlaceholder(
    title: String,
    desc: String,
    action: String,
    contentColor: Color,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        color = contentColor.copy(alpha = 0.06f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = contentColor, style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(6.dp))
            Text(desc, color = contentColor.copy(alpha = 0.72f), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(onClick = onAction) {
                Text(action, color = ReaderTokens.Palette.PrototypeBlue)
            }
        }
    }
}

@Composable
private fun MenuTabButton(
    label: String,
    selected: Boolean,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedBg = if (selected) Color.White else Color.Transparent
    TextButton(
        onClick = onClick,
        modifier = modifier
            .background(
                color = selectedBg,
                shape = RoundedCornerShape(999.dp)
            )
    ) {
        Text(
            text = label,
            color = if (selected) ReaderTokens.Palette.PrototypeTextPrimary else contentColor.copy(alpha = 0.64f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrightnessSheet(
    brightness: Float,
    useSystemBrightness: Boolean,
    eyeProtection: Boolean,
    isNightMode: Boolean,
    onClose: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onUseSystemBrightnessChange: (Boolean) -> Unit,
    onEyeProtectionChange: (Boolean) -> Unit
) {
    val textColor = if (isNightMode) Color(0xFFE9E7E2) else Color(0xFF221F1B)
    val container = if (isNightMode) ReaderTokens.Palette.ReaderPanelElevatedNight else ReaderTokens.Palette.ReaderPanelElevatedDay
    val rowBg = if (isNightMode) Color(0xFF2C2C2C) else Color(0xFFF0ECE4)
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = container
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("亮度", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = Color.Gray)
                Spacer(modifier = Modifier.width(10.dp))
                Slider(
                    value = brightness,
                    onValueChange = onBrightnessChange,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = textColor, modifier = Modifier.size(24.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = rowBg
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("系统亮度", color = textColor)
                        Switch(checked = useSystemBrightness, onCheckedChange = onUseSystemBrightnessChange)
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = rowBg
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("护眼模式", color = textColor)
                        Switch(checked = eyeProtection, onCheckedChange = onEyeProtectionChange)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderMoreSheet(
    panelColor: Color,
    textColor: Color,
    darkSurface: Boolean,
    onClose: () -> Unit,
    onOpenSearch: () -> Unit,
    onCreateAnnotation: () -> Unit,
    onShare: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = panelColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("更多功能", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = textColor)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MoreActionItem(label = "搜索", icon = { tint -> PrototypeIcons.Search(tint = tint) }, darkSurface = darkSurface, onClick = {
                    onClose()
                    onOpenSearch()
                })
                MoreActionItem(label = "笔记", icon = { tint -> PrototypeIcons.Note(tint = tint) }, darkSurface = darkSurface, onClick = {
                    onClose()
                    onCreateAnnotation()
                })
                MoreActionItem(label = "分享", icon = { tint -> PrototypeIcons.Share(tint = tint) }, darkSurface = darkSurface, onClick = {
                    onClose()
                    onShare()
                })
            }
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onClose
            ) {
                Text("取消", color = Color.Gray)
            }
        }
    }
}

@Composable
private fun MoreActionItem(
    label: String,
    icon: @Composable (Color) -> Unit,
    darkSurface: Boolean,
    onClick: () -> Unit
) {
    val iconBg = if (darkSurface) Color(0xFF2F2F2F) else Color.White
    val iconBorder = if (darkSurface) Color(0x1FFFFFFF) else ReaderTokens.Palette.PrototypeBorder
    val tint = if (darkSurface) Color(0xFFE5E5E5) else ReaderTokens.Palette.PrototypeTextSecondary
    val textColor = if (darkSurface) Color(0xFFE2DED7) else ReaderTokens.Palette.PrototypeTextSecondary
    TextButton(onClick = onClick) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.defaultMinSize(minWidth = 72.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(iconBg, RoundedCornerShape(ReaderTokens.Shape.PrototypeActionTile))
                    .border(
                        width = ReaderTokens.Border.Hairline,
                        color = iconBorder,
                        shape = RoundedCornerShape(ReaderTokens.Shape.PrototypeActionTile)
                    ),
                contentAlignment = Alignment.Center
            ) {
                icon(tint)
            }
            Text(
                text = label,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = textColor
            )
        }
    }
}

@Composable
private fun ReaderFooter(
    modifier: Modifier = Modifier,
    progression: Float,
    showProgress: Boolean,
    currentTimeText: String,
    batteryPercent: Int?,
    textColor: Color
) {
    val progressText = "${(progression * 100f).coerceIn(0f, 100f).toInt()}%"
    val batteryFill = (batteryPercent ?: 0).coerceIn(0, 100) / 100f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 22.dp, end = 22.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showProgress) {
            Text(
                progressText,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = textColor
            )
        } else {
            Spacer(modifier = Modifier.width(1.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                currentTimeText,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = textColor
            )
            Spacer(modifier = Modifier.width(5.dp))
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(10.dp)
                    .border(1.dp, textColor, RoundedCornerShape(2.dp))
                    .padding(1.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(batteryFill)
                        .background(textColor)
                )
            }
        }
    }
}

@Composable
private fun GestureHintBar(
    modifier: Modifier = Modifier,
    isNightMode: Boolean
) {
    val color = if (isNightMode) Color(0xFF555555) else Color(0xFFD4D0C8)
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 3.dp)
            .width(128.dp)
            .height(4.dp)
            .background(color = color, shape = RoundedCornerShape(999.dp))
    )
}

@Composable
private fun FullSettingsScreen(
    prefs: ReaderDisplayPrefs,
    isNightMode: Boolean,
    onBack: () -> Unit,
    onShowReadingProgressChanged: (Boolean) -> Unit,
    onFullScreenModeChanged: (Boolean) -> Unit,
    onVolumeKeyPagingChanged: (Boolean) -> Unit
) {
    val bg = if (isNightMode) Color(0xFF111111) else Color(0xFFF4F5F7)
    val card = if (isNightMode) Color(0xFF1C1C1C) else Color.White
    val textColor = if (isNightMode) Color(0xFFE4E4E4) else Color(0xFF1C1C1C)
    val sub = if (isNightMode) Color(0xFF9E9E9E) else Color(0xFF7A7A7A)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = textColor)
                }
                Text("阅读设置", color = textColor, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(shape = RoundedCornerShape(14.dp), color = card) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("屏幕显示", color = ReaderTokens.Palette.PrototypeBlue)
                        SwitchRow(
                            "阅读进度显示",
                            "页脚显示本章百分比",
                            prefs.showReadingProgress,
                            onChange = onShowReadingProgressChanged,
                            textColor = textColor,
                            subColor = sub
                        )
                        SwitchRow(
                            "全屏模式",
                            "仅隐藏状态栏，保留系统导航栏",
                            prefs.fullScreenMode,
                            onChange = onFullScreenModeChanged,
                            textColor = textColor,
                            subColor = sub
                        )
                        SwitchRow(
                            "音量键翻页",
                            "音量上键上一页，音量下键下一页",
                            prefs.volumeKeyPagingEnabled,
                            onChange = onVolumeKeyPagingChanged,
                            textColor = textColor,
                            subColor = sub
                        )
                    }
                }

            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    textColor: Color,
    subColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = textColor)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = subColor, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ApplyReaderBrightness(prefs: ReaderDisplayPrefs) {
    val context = LocalContext.current
    DisposableEffect(prefs.useSystemBrightness, prefs.brightness, context) {
        val activity = context.findActivity()
        val window = activity?.window
        val attrs = window?.attributes
        if (window != null && attrs != null) {
            attrs.screenBrightness = if (prefs.useSystemBrightness) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            } else {
                prefs.brightness.coerceIn(0.05f, 1f)
            }
            window.attributes = attrs
        }

        onDispose {
            val disposeWindow = context.findActivity()?.window ?: return@onDispose
            val disposeAttrs = disposeWindow.attributes
            disposeAttrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            disposeWindow.attributes = disposeAttrs
        }
    }
}

@Composable
private fun ApplyReaderSystemBars(
    prefs: ReaderDisplayPrefs,
    chromeVisible: Boolean,
    isReadingLayer: Boolean
) {
    val context = LocalContext.current
    DisposableEffect(context, prefs.fullScreenMode, chromeVisible, isReadingLayer) {
        val activity = context.findActivity()
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            val immersive = prefs.fullScreenMode && isReadingLayer && !chromeVisible
            if (immersive) {
                controller.hide(WindowInsetsCompat.Type.statusBars())
                controller.show(WindowInsetsCompat.Type.navigationBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        onDispose {
            val disposeWindow = context.findActivity()?.window ?: return@onDispose
            WindowCompat.setDecorFitsSystemWindows(disposeWindow, true)
            WindowInsetsControllerCompat(disposeWindow, disposeWindow.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun rememberClockText(): String {
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val value by produceState(
        initialValue = LocalDateTime.now().format(formatter),
        key1 = formatter
    ) {
        while (true) {
            value = LocalDateTime.now().format(formatter)
            val millis = System.currentTimeMillis()
            val delayMs = 60_000L - (millis % 60_000L)
            delay(delayMs)
        }
    }
    return value
}

@Composable
private fun rememberBatteryPercent(): Int? {
    val context = LocalContext.current
    val battery = remember(context) { mutableStateOf<Int?>(null) }
    DisposableEffect(context) {
        val appContext = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                battery.value = parseBatteryPercent(intent)
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = appContext.registerReceiver(receiver, filter)
        battery.value = parseBatteryPercent(sticky)

        onDispose {
            runCatching { appContext.unregisterReceiver(receiver) }
        }
    }
    return battery.value
}

private fun parseBatteryPercent(intent: Intent?): Int? {
    val source = intent ?: return null
    val level = source.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = source.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    return ((level * 100f) / scale.toFloat()).roundToInt().coerceIn(0, 100)
}

private fun resolveReaderBackgroundColor(
    nightMode: Boolean,
    preset: ReaderBackgroundPreset
): Color {
    return when (preset) {
        ReaderBackgroundPreset.SYSTEM -> if (nightMode) {
            ReaderTokens.Palette.ReaderBackgroundNight
        } else {
            ReaderTokens.Palette.ReaderBackgroundDay
        }

        ReaderBackgroundPreset.PAPER -> Color(0xFFFDF9F3)
        ReaderBackgroundPreset.WARM -> Color(0xFFF3E7CA)
        ReaderBackgroundPreset.GREEN -> Color(0xFFCCE0D1)
        ReaderBackgroundPreset.DARK -> Color(0xFF2B2B2B)
        ReaderBackgroundPreset.NAVY -> Color(0xFF1A1F2B)
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun ReaderSheet.toSettingsPanel(): ReaderSettingsPanel {
    return when (this) {
        ReaderSheet.SettingsFont -> ReaderSettingsPanel.Font
        ReaderSheet.SettingsSpacing -> ReaderSettingsPanel.Spacing
        ReaderSheet.SettingsPageTurn -> ReaderSettingsPanel.PageTurn
        ReaderSheet.SettingsMoreBackground -> ReaderSettingsPanel.MoreBackground
        else -> ReaderSettingsPanel.Font
    }
}

private fun ReaderSettingsPanel.toSheet(): ReaderSheet {
    return when (this) {
        ReaderSettingsPanel.Font -> ReaderSheet.SettingsFont
        ReaderSettingsPanel.Spacing -> ReaderSheet.SettingsSpacing
        ReaderSettingsPanel.PageTurn -> ReaderSheet.SettingsPageTurn
        ReaderSettingsPanel.MoreBackground -> ReaderSheet.SettingsMoreBackground
    }
}
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\ui\ReaderScreen.kt

```kotlin
package com.ireader.feature.reader.ui

import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ireader.feature.reader.presentation.ReaderEffect
import com.ireader.feature.reader.presentation.ReaderHardwareKeyBridge
import com.ireader.feature.reader.presentation.ReaderIntent
import com.ireader.feature.reader.presentation.UiText
import com.ireader.feature.reader.presentation.ReaderViewModel
import com.ireader.feature.reader.web.ExternalLinkPolicy

@Composable
@Suppress("FunctionNaming")
fun ReaderScreen(
    bookId: Long,
    locatorArg: String?,
    onBack: () -> Unit,
    onOpenAnnotations: (Long) -> Unit,
    vm: ReaderViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    val volumeKeyHandler = rememberUpdatedState(newValue = { keyCode: Int, action: Int ->
        if (!state.displayPrefs.volumeKeyPagingEnabled) {
            false
        } else {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (action == KeyEvent.ACTION_DOWN) {
                        vm.dispatch(ReaderIntent.Prev)
                    }
                    true
                }

                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (action == KeyEvent.ACTION_DOWN) {
                        vm.dispatch(ReaderIntent.Next)
                    }
                    true
                }

                else -> false
            }
        }
    })

    LaunchedEffect(bookId, locatorArg) {
        vm.dispatch(ReaderIntent.Start(bookId = bookId, locatorArg = locatorArg))
    }

    DisposableEffect(Unit) {
        ReaderHardwareKeyBridge.setVolumeKeyListener { keyCode, action ->
            volumeKeyHandler.value(keyCode, action)
        }
        onDispose {
            ReaderHardwareKeyBridge.setVolumeKeyListener(null)
        }
    }

    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                ReaderEffect.Back -> onBack()
                is ReaderEffect.OpenAnnotations -> onOpenAnnotations(effect.bookId)
                is ReaderEffect.OpenExternalUrl -> {
                    when (ExternalLinkPolicy.evaluate(effect.url)) {
                        is ExternalLinkPolicy.Decision.Allow -> runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(effect.url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }.onFailure {
                            snackbarHost.showSnackbar("无法打开外部链接")
                        }

                        is ExternalLinkPolicy.Decision.Block -> {
                            snackbarHost.showSnackbar("已拦截不安全外部链接")
                        }
                    }
                }
                is ReaderEffect.ShareText -> {
                    runCatching {
                        val chooser = Intent.createChooser(
                            Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, effect.text),
                            null
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooser)
                    }.onFailure {
                        snackbarHost.showSnackbar("分享失败")
                    }
                }
                is ReaderEffect.Snackbar -> {
                    val message = when (val text = effect.message) {
                        is UiText.Dynamic -> text.value
                        is UiText.Res -> context.getString(text.id, *text.args.toTypedArray())
                    }
                    snackbarHost.showSnackbar(message)
                }
            }
        }
    }

    BackHandler {
        vm.dispatch(ReaderIntent.BackPressed)
    }

    ReaderScaffold(
        state = state,
        snackbarHostState = snackbarHost,
        onBack = onBack,
        onIntent = vm::dispatch,
        onOpenLocator = vm::openLocator,
        onReadingViewportChanged = { constraints ->
            vm.dispatch(ReaderIntent.LayoutChanged(constraints))
        }
    )
}
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\ui\ReaderSurface.kt

```kotlin
package com.ireader.feature.reader.ui

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.ireader.core.common.android.surface.DefaultFragmentRenderSurface
import com.ireader.reader.api.render.ReaderController
import kotlinx.coroutines.launch

@Composable
internal fun ReaderSurface(
    controller: ReaderController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    if (activity == null) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(text = "当前 Activity 不支持 EPUB 导航器渲染")
        }
        return
    }

    val fragmentManager = activity.supportFragmentManager
    val containerId = remember(controller) { View.generateViewId() }
    val surface = remember(fragmentManager, containerId) {
        DefaultFragmentRenderSurface(
            fragmentManager = fragmentManager,
            containerViewId = containerId
        )
    }
    val scope = rememberCoroutineScope()

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            FragmentContainerView(viewContext).apply {
                id = containerId
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        }
    )

    LaunchedEffect(controller, surface) {
        controller.bindSurface(surface)
    }

    DisposableEffect(controller, surface) {
        onDispose {
            scope.launch {
                controller.unbindSurface()
            }
        }
    }
}
```

## D:\file\android\ireader\feature\reader\src\main\kotlin\com\ireader\feature\reader\web\ExternalLinkPolicy.kt

```kotlin
package com.ireader.feature.reader.web

import java.net.URI
import java.util.Locale

object ExternalLinkPolicy {
    sealed interface Decision {
        data class Allow(val url: String) : Decision
        data class Block(val reason: String) : Decision
    }

    private val allowedSchemes: Set<String> = setOf(
        "https",
        "http",
        "mailto",
        "tel"
    )

    fun evaluate(rawUrl: String): Decision {
        val candidate = rawUrl.trim()
        if (candidate.isBlank()) return Decision.Block("empty")

        val uri = runCatching { URI(candidate) }.getOrNull()
            ?: return Decision.Block("invalid_uri")
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
            ?: return Decision.Block("missing_scheme")
        if (scheme !in allowedSchemes) return Decision.Block("unsupported_scheme")

        if ((scheme == "http" || scheme == "https") && uri.host.isNullOrBlank()) {
            return Decision.Block("missing_host")
        }
        return Decision.Allow(uri.toString())
    }
}

```


