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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
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
import com.ireader.feature.reader.presentation.resolveActiveTocIndex
import com.ireader.feature.reader.ui.components.ErrorPane
import com.ireader.feature.reader.ui.components.PageRenderer
import com.ireader.feature.reader.ui.components.PasswordDialog
import com.ireader.feature.reader.ui.components.ReaderSettingsPanel
import com.ireader.feature.reader.ui.components.SearchSheet
import com.ireader.feature.reader.ui.components.SettingsSheet
import com.ireader.reader.api.render.RenderContent
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
    val footerHeightPx = remember { mutableIntStateOf(0) }
    val readingBottomInsetDp = with(density) { footerHeightPx.intValue.toDp() }

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
        isReadingLayer = state.layerState == com.ireader.feature.reader.presentation.ReaderLayerState.Reading,
        readerBackgroundColor = bgColor
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
                    .padding(top = 8.dp, bottom = readingBottomInsetDp)
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
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { size ->
                        if (footerHeightPx.intValue != size.height) {
                            footerHeightPx.intValue = size.height
                        }
                    },
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
                    isEmbeddedEpub = state.capabilities?.reflowable == true &&
                        state.page?.content is RenderContent.Embedded,
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
    val activeTocIndex = resolveActiveTocIndex(
        items = state.toc.items,
        currentLocator = state.renderState?.locator
    )
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
                            text = "共${state.toc.items.size}章",
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
                                    color = if (index == activeTocIndex) ReaderTokens.Palette.PrototypeBlue else contentColor,
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
    isReadingLayer: Boolean,
    readerBackgroundColor: Color
) {
    val context = LocalContext.current
    val readerBackgroundArgb = readerBackgroundColor.toArgb()
    val useLightSystemBarIcons = readerBackgroundColor.luminance() > 0.5f
    DisposableEffect(
        context,
        prefs.fullScreenMode,
        isReadingLayer,
        readerBackgroundArgb,
        useLightSystemBarIcons
    ) {
        val activity = context.findActivity()
        val window = activity?.window
        val originalStatusBarColor = window?.statusBarColor
        val originalNavigationBarColor = window?.navigationBarColor
        var originalLightStatusBars = false
        var originalLightNavigationBars = false
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            originalLightStatusBars = controller.isAppearanceLightStatusBars
            originalLightNavigationBars = controller.isAppearanceLightNavigationBars

            window.statusBarColor = readerBackgroundArgb
            window.navigationBarColor = readerBackgroundArgb
            controller.isAppearanceLightStatusBars = useLightSystemBarIcons
            controller.isAppearanceLightNavigationBars = useLightSystemBarIcons

            val immersive = prefs.fullScreenMode && isReadingLayer
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
            val disposeController = WindowInsetsControllerCompat(disposeWindow, disposeWindow.decorView)
            disposeController.isAppearanceLightStatusBars = originalLightStatusBars
            disposeController.isAppearanceLightNavigationBars = originalLightNavigationBars
            originalStatusBarColor?.let { disposeWindow.statusBarColor = it }
            originalNavigationBarColor?.let { disposeWindow.navigationBarColor = it }
            disposeController.show(WindowInsetsCompat.Type.systemBars())
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
