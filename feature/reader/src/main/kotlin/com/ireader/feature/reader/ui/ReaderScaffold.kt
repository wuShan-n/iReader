package com.ireader.feature.reader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ireader.core.designsystem.ReaderTokens
import com.ireader.feature.reader.presentation.ReaderErrorAction
import com.ireader.feature.reader.presentation.ReaderIntent
import com.ireader.feature.reader.presentation.ReaderSheet
import com.ireader.feature.reader.presentation.ReaderUiState
import com.ireader.feature.reader.ui.components.ErrorPane
import com.ireader.feature.reader.ui.components.PageRenderer
import com.ireader.feature.reader.ui.components.PasswordDialog
import com.ireader.feature.reader.ui.components.ReaderSettingsPanel
import com.ireader.feature.reader.ui.components.SearchSheet
import com.ireader.feature.reader.ui.components.SettingsSheet
import com.ireader.feature.reader.ui.components.TocSheet
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
    val bgColor = if (state.isNightMode) {
        ReaderTokens.Palette.ReaderBackgroundNight
    } else {
        ReaderTokens.Palette.ReaderBackgroundDay
    }
    val panelColor = if (state.isNightMode) {
        ReaderTokens.Palette.ReaderPanelNight
    } else {
        ReaderTokens.Palette.ReaderPanelDay
    }
    val panelTextColor = if (state.isNightMode) {
        Color(0xFFD8D8D8)
    } else {
        Color(0xFF1E1E1E)
    }
    val footerTextColor = if (state.isNightMode) Color(0xFF8E8B84) else Color(0xFF87817B)

    var brightness by rememberSaveable { mutableFloatStateOf(0.35f) }
    var useSystemBrightness by rememberSaveable { mutableStateOf(true) }
    var eyeProtection by rememberSaveable { mutableStateOf(false) }

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
                onBackgroundTap = { tap, size ->
                    handleReaderTap(
                        tap = tap,
                        size = size,
                        currentSheet = state.sheet,
                        chromeVisible = state.chromeVisible,
                        onIntent = onIntent
                    )
                },
                onLinkActivated = { link -> onIntent(ReaderIntent.ActivateLink(link)) },
                onWebSchemeUrl = onWebSchemeUrl
            )

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
                textColor = footerTextColor
            )

            AnimatedVisibility(
                visible = state.chromeVisible && state.sheet != ReaderSheet.FullSettings,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                ReaderTopBar(
                    title = state.title ?: "阅读中",
                    panelColor = panelColor.copy(alpha = 0.98f),
                    contentColor = panelTextColor,
                    onBack = onBack,
                    onMore = { onIntent(ReaderIntent.OpenReaderMore) }
                )
            }

            AnimatedVisibility(
                visible = state.chromeVisible && state.sheet != ReaderSheet.FullSettings,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ReaderBottomBar(
                    sheet = state.sheet,
                    isNightMode = state.isNightMode,
                    panelColor = panelColor.copy(alpha = 0.98f),
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
                brightness = brightness,
                useSystemBrightness = useSystemBrightness,
                eyeProtection = eyeProtection,
                isNightMode = state.isNightMode,
                onClose = { onIntent(ReaderIntent.CloseSheet) },
                onBrightnessChange = { brightness = it },
                onUseSystemBrightnessChange = { useSystemBrightness = it },
                onEyeProtectionChange = { eyeProtection = it }
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
                    onApply = { cfg, persist -> onIntent(ReaderIntent.UpdateConfig(cfg, persist)) }
                )
            }

            ReaderSheet.ReaderMore -> ReaderMoreSheet(
                panelColor = panelColor,
                textColor = panelTextColor,
                onClose = { onIntent(ReaderIntent.CloseSheet) },
                onOpenSearch = { onIntent(ReaderIntent.OpenSearch) },
                onOpenAnnotations = { onIntent(ReaderIntent.OpenAnnotations) }
            )

            ReaderSheet.FullSettings -> FullSettingsScreen(
                isNightMode = state.isNightMode,
                onBack = { onIntent(ReaderIntent.BackInSheetHierarchy) },
                onToggleNightMode = { onIntent(ReaderIntent.ToggleNightMode) }
            )
        }
    }
}

private fun handleReaderTap(
    tap: Offset,
    size: IntSize,
    currentSheet: ReaderSheet,
    chromeVisible: Boolean,
    onIntent: (ReaderIntent) -> Unit
) {
    if (currentSheet != ReaderSheet.None) {
        onIntent(ReaderIntent.CloseSheet)
        return
    }

    if (chromeVisible) {
        onIntent(ReaderIntent.ToggleChrome)
        return
    }

    val width = size.width.toFloat().coerceAtLeast(1f)
    when {
        tap.x < width * 0.3f -> onIntent(ReaderIntent.Prev)
        tap.x > width * 0.7f -> onIntent(ReaderIntent.Next)
        else -> onIntent(ReaderIntent.ToggleChrome)
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    panelColor: Color,
    contentColor: Color,
    onBack: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(panelColor)
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = contentColor)
        }
        Text(
            text = title,
            color = contentColor,
            maxLines = 1,
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall
        )
        TextButton(onClick = onMore) {
            Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = "更多", tint = contentColor)
        }
    }
}

@Composable
private fun ReaderBottomBar(
    sheet: ReaderSheet,
    isNightMode: Boolean,
    panelColor: Color,
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
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        BottomItem(
            label = "目录",
            selected = sheet == ReaderSheet.Toc,
            contentColor = contentColor,
            icon = {
                Icon(imageVector = Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
            },
            onClick = onOpenToc
        )
        BottomItem(
            label = "亮度",
            selected = sheet == ReaderSheet.Brightness,
            contentColor = contentColor,
            icon = {
                Icon(imageVector = Icons.Outlined.Brightness6, contentDescription = null)
            },
            onClick = onOpenBrightness
        )
        BottomItem(
            label = if (isNightMode) "日间" else "夜间",
            selected = false,
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
    contentColor: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        if (selected) ReaderTokens.Palette.AccentBlueSoft else Color.Transparent,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                val tint = if (selected) ReaderTokens.Palette.AccentBlue else contentColor
                CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides tint) {
                    icon()
                }
            }
            Text(
                text = label,
                color = if (selected) ReaderTokens.Palette.AccentBlue else contentColor,
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
    val textColor = if (isNightMode) Color(0xFFE5E5E5) else Color(0xFF1B1B1B)
    ModalBottomSheet(onDismissRequest = onClose) {
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
                Text("系统亮度", color = textColor)
                Switch(checked = useSystemBrightness, onCheckedChange = onUseSystemBrightnessChange)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("护眼模式", color = textColor)
                Switch(checked = eyeProtection, onCheckedChange = onEyeProtectionChange)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderMoreSheet(
    panelColor: Color,
    textColor: Color,
    onClose: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAnnotations: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = panelColor) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("更多功能", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = textColor)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoreActionItem(label = "搜索", icon = Icons.Outlined.Search, onClick = {
                    onClose()
                    onOpenSearch()
                })
                MoreActionItem(label = "笔记", icon = Icons.Outlined.Bookmarks, onClick = {
                    onClose()
                    onOpenAnnotations()
                })
                MoreActionItem(label = "分享", icon = Icons.Outlined.Share, onClick = { })
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
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFF5F5F5), CircleShape)
                    .border(1.dp, Color(0x22000000), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = label, tint = Color.Gray)
            }
            Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ReaderFooter(
    modifier: Modifier = Modifier,
    progression: Float,
    textColor: Color
) {
    val progressText = "${(progression * 100f).coerceIn(0f, 100f).toInt()}%"
    val time = remember { LocalTime.now() }.format(DateTimeFormatter.ofPattern("HH:mm"))

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 22.dp, end = 22.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(progressText, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = textColor)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(time, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = textColor)
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
                        .fillMaxWidth(0.6f)
                        .background(textColor)
                )
            }
        }
    }
}

@Composable
private fun FullSettingsScreen(
    isNightMode: Boolean,
    onBack: () -> Unit,
    onToggleNightMode: () -> Unit
) {
    var showProgress by rememberSaveable { mutableStateOf(true) }
    var fullScreenMode by rememberSaveable { mutableStateOf(true) }
    var verticalLayout by rememberSaveable { mutableStateOf(false) }

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
                        SwitchRow("阅读进度显示", "本章百分比", showProgress, onChange = { showProgress = it }, textColor = textColor, subColor = sub)
                        SwitchRow("全屏模式", "", fullScreenMode, onChange = { fullScreenMode = it }, textColor = textColor, subColor = sub)
                        SwitchRow("竖排版", "部分书籍排版可能不佳", verticalLayout, onChange = { verticalLayout = it }, textColor = textColor, subColor = sub)
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
