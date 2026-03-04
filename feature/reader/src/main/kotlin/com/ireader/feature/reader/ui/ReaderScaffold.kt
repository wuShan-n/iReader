package com.ireader.feature.reader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NightlightRound
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.ireader.core.datastore.reader.ReaderBackgroundPreset
import com.ireader.core.datastore.reader.ReaderDisplayPrefs
import com.ireader.core.designsystem.ReaderTokens
import com.ireader.feature.reader.presentation.ReaderErrorAction
import com.ireader.feature.reader.presentation.ReaderIntent
import com.ireader.feature.reader.presentation.ReaderSheet
import com.ireader.feature.reader.presentation.ReaderUiState
import com.ireader.feature.reader.presentation.PageTurnDirection
import com.ireader.reader.api.render.PageTurnMode
import com.ireader.feature.reader.ui.components.ErrorPane
import com.ireader.feature.reader.ui.components.PageRenderer
import com.ireader.feature.reader.ui.components.PasswordDialog
import com.ireader.feature.reader.ui.components.ReaderSettingsPanel
import com.ireader.feature.reader.ui.components.SearchSheet
import com.ireader.feature.reader.ui.components.SettingsSheet
import com.ireader.feature.reader.ui.components.TocSheet
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
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScaffold(
    state: ReaderUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onIntent: (ReaderIntent) -> Unit,
    onOpenLocator: (String) -> Unit,
    onWebSchemeUrl: (String) -> Boolean
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
        ReaderTokens.Palette.ReaderPanelElevatedNight
    } else {
        ReaderTokens.Palette.ReaderPanelElevatedDay
    }
    val panelBorder = if (darkSurface) ReaderTokens.Palette.ReaderDividerNight else ReaderTokens.Palette.ReaderDividerDay
    val panelTextColor = if (darkSurface) {
        Color(0xFFE8E3DA)
    } else {
        Color(0xFF24201C)
    }
    val readerTextColor = if (darkSurface) {
        ReaderTokens.Palette.ReaderTextNight
    } else {
        ReaderTokens.Palette.ReaderTextDay
    }
    val footerTextColor = if (darkSurface) Color(0xFF8E8B84) else Color(0xFF87817B)
    val clock = rememberClockText()
    val batteryPercent = rememberBatteryPercent()

    LaunchedEffect(prefs.fullScreenMode) {
        if (!prefs.fullScreenMode && !state.chromeVisible) {
            onIntent(ReaderIntent.ToggleChrome)
        }
    }
    ApplyReaderBrightness(prefs = prefs)

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = bgColor
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(bgColor)
        ) {
            PageRenderer(
                state = state,
                textColor = readerTextColor,
                backgroundColor = bgColor,
                onBackgroundTap = { tap, size ->
                    handleReaderTap(
                        tap = tap,
                        size = size,
                        currentSheet = state.sheet,
                        allowChromeToggle = prefs.fullScreenMode,
                        onIntent = onIntent
                    )
                },
                onPageTurn = { direction ->
                    when (direction) {
                        PageTurnDirection.NEXT -> onIntent(ReaderIntent.Next)
                        PageTurnDirection.PREV -> onIntent(ReaderIntent.Prev)
                    }
                },
                onLinkActivated = { link -> onIntent(ReaderIntent.ActivateLink(link)) },
                onWebSchemeUrl = onWebSchemeUrl
            )

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

            AnimatedVisibility(
                visible = state.chromeVisible && state.sheet != ReaderSheet.FullSettings,
                enter = fadeIn(animationSpec = tween(ReaderTokens.Motion.Medium)) +
                    slideInVertically(initialOffsetY = { -it / 2 }, animationSpec = tween(ReaderTokens.Motion.Medium)),
                exit = fadeOut(animationSpec = tween(ReaderTokens.Motion.Fast)) +
                    slideOutVertically(targetOffsetY = { -it / 2 }, animationSpec = tween(ReaderTokens.Motion.Fast)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                ReaderTopBar(
                    title = state.title ?: "阅读中",
                    panelColor = panelColor.copy(alpha = 0.98f),
                    panelBorderColor = panelBorder,
                    contentColor = panelTextColor,
                    onBack = onBack,
                    onMore = { onIntent(ReaderIntent.OpenReaderMore) }
                )
            }

            AnimatedVisibility(
                visible = state.chromeVisible && state.sheet != ReaderSheet.FullSettings,
                enter = fadeIn(animationSpec = tween(ReaderTokens.Motion.Medium)) +
                    slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(ReaderTokens.Motion.Medium)),
                exit = fadeOut(animationSpec = tween(ReaderTokens.Motion.Fast)) +
                    slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = tween(ReaderTokens.Motion.Fast)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ReaderBottomBar(
                    sheet = state.sheet,
                    isNightMode = state.isNightMode,
                    panelColor = panelColor.copy(alpha = 0.98f),
                    panelBorderColor = panelBorder,
                    contentColor = panelTextColor,
                    onOpenToc = { onIntent(ReaderIntent.OpenToc) },
                    onOpenBrightness = { onIntent(ReaderIntent.OpenBrightness) },
                    onToggleNight = { onIntent(ReaderIntent.ToggleNightMode) },
                    onOpenSettings = { onIntent(ReaderIntent.OpenSettings) }
                )
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
            ReaderSheet.None -> Unit
            ReaderSheet.Toc -> TocSheet(
                state = state.toc,
                onClose = { onIntent(ReaderIntent.CloseSheet) },
                onClick = { encoded ->
                    onOpenLocator(encoded)
                    onIntent(ReaderIntent.CloseSheet)
                }
            )

            ReaderSheet.Search -> SearchSheet(
                state = state.search,
                onClose = { onIntent(ReaderIntent.CloseSheet) },
                onQueryChange = { q -> onIntent(ReaderIntent.SearchQueryChanged(q)) },
                onSearch = { onIntent(ReaderIntent.ExecuteSearch) },
                onClickResult = { encoded ->
                    onOpenLocator(encoded)
                    onIntent(ReaderIntent.CloseSheet)
                }
            )

            ReaderSheet.Brightness -> BrightnessSheet(
                brightness = prefs.brightness,
                useSystemBrightness = prefs.useSystemBrightness,
                eyeProtection = prefs.eyeProtection,
                isNightMode = state.isNightMode,
                onClose = { onIntent(ReaderIntent.CloseSheet) },
                onBrightnessChange = { onIntent(ReaderIntent.UpdateBrightness(it)) },
                onUseSystemBrightnessChange = { onIntent(ReaderIntent.SetUseSystemBrightness(it)) },
                onEyeProtectionChange = { onIntent(ReaderIntent.SetEyeProtection(it)) }
            )

            ReaderSheet.Settings,
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
                    onOpenSubPanel = { panel ->
                        onIntent(ReaderIntent.OpenSettingsSub(panel.toSheet()))
                    },
                    onOpenFullSettings = { onIntent(ReaderIntent.OpenFullSettings) },
                    onToggleNightMode = { onIntent(ReaderIntent.ToggleNightMode) },
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
                onOpenAnnotations = { onIntent(ReaderIntent.OpenAnnotations) },
                onShare = { onIntent(ReaderIntent.ShareBook) }
            )

            ReaderSheet.FullSettings -> FullSettingsScreen(
                prefs = prefs,
                isVerticalPaging = state.pageTurnMode == PageTurnMode.SCROLL_VERTICAL,
                isNightMode = state.isNightMode,
                onBack = { onIntent(ReaderIntent.BackInSheetHierarchy) },
                onToggleNightMode = { onIntent(ReaderIntent.ToggleNightMode) },
                onShowReadingProgressChanged = { onIntent(ReaderIntent.SetReadingProgressVisible(it)) },
                onFullScreenModeChanged = { onIntent(ReaderIntent.SetFullScreenMode(it)) },
                onVerticalPagingChanged = { onIntent(ReaderIntent.SetVerticalPaging(it)) }
            )
        }
    }
}

private fun handleReaderTap(
    tap: Offset,
    size: IntSize,
    currentSheet: ReaderSheet,
    allowChromeToggle: Boolean,
    onIntent: (ReaderIntent) -> Unit
) {
    if (currentSheet != ReaderSheet.None) {
        onIntent(ReaderIntent.CloseSheet)
        return
    }

    val width = size.width.toFloat().coerceAtLeast(1f)
    when {
        tap.x < width * 0.3f -> onIntent(ReaderIntent.Prev)
        tap.x > width * 0.7f -> onIntent(ReaderIntent.Next)
        allowChromeToggle -> onIntent(ReaderIntent.ToggleChrome)
        else -> Unit
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    panelColor: Color,
    panelBorderColor: Color,
    contentColor: Color,
    onBack: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(panelColor)
            .border(width = 1.dp, color = panelBorderColor)
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = contentColor.copy(alpha = 0.12f),
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
        IconButton(
            onClick = onMore,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = contentColor.copy(alpha = 0.12f),
                contentColor = contentColor
            )
        ) {
            Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = "更多", tint = contentColor)
        }
    }
}

@Composable
private fun ReaderBottomBar(
    sheet: ReaderSheet,
    isNightMode: Boolean,
    panelColor: Color,
    panelBorderColor: Color,
    contentColor: Color,
    onOpenToc: () -> Unit,
    onOpenBrightness: () -> Unit,
    onToggleNight: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(panelColor)
            .border(width = 1.dp, color = panelBorderColor)
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        BottomItem(
            label = "目录",
            selected = sheet == ReaderSheet.Toc,
            isNightMode = isNightMode,
            contentColor = contentColor,
            icon = {
                Icon(imageVector = Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
            },
            onClick = onOpenToc
        )
        BottomItem(
            label = "亮度",
            selected = sheet == ReaderSheet.Brightness,
            isNightMode = isNightMode,
            contentColor = contentColor,
            icon = {
                Icon(imageVector = Icons.Outlined.Brightness6, contentDescription = null)
            },
            onClick = onOpenBrightness
        )
        BottomItem(
            label = if (isNightMode) "日间" else "夜间",
            selected = false,
            isNightMode = isNightMode,
            contentColor = contentColor,
            icon = {
                Icon(
                    imageVector = if (isNightMode) Icons.Outlined.WbSunny else Icons.Outlined.NightlightRound,
                    contentDescription = null
                )
            },
            onClick = onToggleNight
        )
        BottomItem(
            label = "设置",
            selected = sheet == ReaderSheet.Settings ||
                sheet == ReaderSheet.SettingsFont ||
                sheet == ReaderSheet.SettingsSpacing ||
                sheet == ReaderSheet.SettingsPageTurn ||
                sheet == ReaderSheet.SettingsMoreBackground,
            isNightMode = isNightMode,
            contentColor = contentColor,
            icon = {
                Icon(imageVector = Icons.Outlined.Settings, contentDescription = null)
            },
            onClick = onOpenSettings
        )
    }
}

@Composable
private fun BottomItem(
    label: String,
    selected: Boolean,
    isNightMode: Boolean,
    contentColor: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val selectedTint = if (isNightMode) ReaderTokens.Palette.AccentBlueNight else ReaderTokens.Palette.AccentBlue
    val selectedBg = if (isNightMode) selectedTint.copy(alpha = 0.22f) else ReaderTokens.Palette.AccentBlueSoft

    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        if (selected) selectedBg else Color.Transparent,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                val tint = if (selected) selectedTint else contentColor
                CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides tint) {
                    icon()
                }
            }
            Text(
                text = label,
                color = if (selected) selectedTint else contentColor,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
            )
        }
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
                .padding(24.dp),
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(12.dp), color = rowBg) {
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
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(12.dp), color = rowBg) {
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
    onOpenAnnotations: () -> Unit,
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
                MoreActionItem(label = "搜索", icon = Icons.Outlined.Search, darkSurface = darkSurface, onClick = {
                    onClose()
                    onOpenSearch()
                })
                MoreActionItem(label = "笔记", icon = Icons.Outlined.Bookmarks, darkSurface = darkSurface, onClick = {
                    onClose()
                    onOpenAnnotations()
                })
                MoreActionItem(label = "分享", icon = Icons.Outlined.Share, darkSurface = darkSurface, onClick = {
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    darkSurface: Boolean,
    onClick: () -> Unit
) {
    val iconBg = if (darkSurface) Color(0xFF2F2F2F) else Color(0xFFF5F2EC)
    val iconBorder = if (darkSurface) Color(0x1FFFFFFF) else Color(0x22000000)
    val tint = if (darkSurface) Color(0xFFE5E5E5) else Color(0xFF585858)
    val textColor = if (darkSurface) Color(0xFFE2DED7) else Color(0xFF6A6865)
    TextButton(onClick = onClick) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.defaultMinSize(minWidth = 72.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(iconBg, CircleShape)
                    .border(1.dp, iconBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = label, tint = tint)
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
private fun FullSettingsScreen(
    prefs: ReaderDisplayPrefs,
    isVerticalPaging: Boolean,
    isNightMode: Boolean,
    onBack: () -> Unit,
    onToggleNightMode: () -> Unit,
    onShowReadingProgressChanged: (Boolean) -> Unit,
    onFullScreenModeChanged: (Boolean) -> Unit,
    onVerticalPagingChanged: (Boolean) -> Unit
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
                    .statusBarsPadding()
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
                        Text("屏幕显示", color = ReaderTokens.Palette.AccentBlue)
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
                            "关闭后常驻顶部和底部工具栏",
                            prefs.fullScreenMode,
                            onChange = onFullScreenModeChanged,
                            textColor = textColor,
                            subColor = sub
                        )
                        SwitchRow(
                            "竖向翻页",
                            "启用上下滚动翻页",
                            isVerticalPaging,
                            onChange = onVerticalPagingChanged,
                            textColor = textColor,
                            subColor = sub
                        )
                    }
                }

                Surface(shape = RoundedCornerShape(14.dp), color = card) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("主题", color = ReaderTokens.Palette.AccentBlue)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("夜间模式", color = textColor)
                            Switch(checked = isNightMode, onCheckedChange = { onToggleNightMode() })
                        }
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
        ReaderSheet.Settings -> ReaderSettingsPanel.Main
        ReaderSheet.SettingsFont -> ReaderSettingsPanel.Font
        ReaderSheet.SettingsSpacing -> ReaderSettingsPanel.Spacing
        ReaderSheet.SettingsPageTurn -> ReaderSettingsPanel.PageTurn
        ReaderSheet.SettingsMoreBackground -> ReaderSettingsPanel.MoreBackground
        else -> ReaderSettingsPanel.Main
    }
}

private fun ReaderSettingsPanel.toSheet(): ReaderSheet {
    return when (this) {
        ReaderSettingsPanel.Main -> ReaderSheet.Settings
        ReaderSettingsPanel.Font -> ReaderSheet.SettingsFont
        ReaderSettingsPanel.Spacing -> ReaderSheet.SettingsSpacing
        ReaderSettingsPanel.PageTurn -> ReaderSheet.SettingsPageTurn
        ReaderSettingsPanel.MoreBackground -> ReaderSheet.SettingsMoreBackground
    }
}
