package com.ireader.feature.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ireader.core.datastore.reader.ReaderBackgroundPreset
import com.ireader.reader.api.render.BreakStrategyMode
import com.ireader.reader.api.render.HyphenationMode
import com.ireader.core.designsystem.ReaderTokens
import com.ireader.reader.api.render.PageInsetMode
import com.ireader.reader.api.render.PageTurnMode
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.TextAlignMode
import com.ireader.feature.reader.presentation.displayLabel
import com.ireader.feature.reader.presentation.pageTurnMode
import com.ireader.feature.reader.presentation.withPageTurnMode
import com.ireader.reader.model.DocumentCapabilities
import kotlin.math.roundToInt

enum class ReaderSettingsPanel {
    Main,
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
    onOpenSubPanel: (ReaderSettingsPanel) -> Unit,
    onOpenFullSettings: () -> Unit,
    onToggleNightMode: () -> Unit,
    onSelectBackground: (ReaderBackgroundPreset) -> Unit,
    onApply: (RenderConfig, persist: Boolean) -> Unit
) {
    val container = if (isNightMode) ReaderTokens.Palette.ReaderPanelElevatedNight else ReaderTokens.Palette.ReaderPanelElevatedDay

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
            ReaderSettingsPanel.Main -> ReflowMainPanel(
                config = current,
                isNightMode = isNightMode,
                onOpenSubPanel = onOpenSubPanel,
                onOpenFullSettings = onOpenFullSettings,
                onToggleNightMode = onToggleNightMode,
                onApply = onApply
            )

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
                onToggleNightMode = onToggleNightMode,
                onSelectBackground = onSelectBackground
            )
        }
    }
}

@Composable
private fun ReflowMainPanel(
    config: RenderConfig.ReflowText,
    isNightMode: Boolean,
    onOpenSubPanel: (ReaderSettingsPanel) -> Unit,
    onOpenFullSettings: () -> Unit,
    onToggleNightMode: () -> Unit,
    onApply: (RenderConfig, persist: Boolean) -> Unit
) {
    var persist by remember { mutableStateOf(true) }
    var livePreview by remember { mutableStateOf(true) }
    var fontSize by remember(config.fontSizeSp) { mutableFloatStateOf(config.fontSizeSp) }
    var lineHeight by remember(config.lineHeightMult) { mutableFloatStateOf(config.lineHeightMult) }
    var pagePadding by remember(config.pagePaddingDp) { mutableFloatStateOf(config.pagePaddingDp) }
    fun draftConfig(): RenderConfig.ReflowText {
        return config.copy(
            fontSizeSp = fontSize,
            lineHeightMult = lineHeight,
            pagePaddingDp = pagePadding
        )
    }
    fun previewIfEnabled() {
        if (livePreview) {
            onApply(draftConfig(), false)
        }
    }

    val actionBg = if (isNightMode) Color(0xFF2C2C2C) else Color(0xFFE9ECEF)
    val textColor = if (isNightMode) Color(0xFFE5E5E5) else Color(0xFF1B1B1B)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("阅读设置", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1.2f)
                    .height(40.dp)
                    .background(actionBg, RoundedCornerShape(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        fontSize = (fontSize - 1f).coerceAtLeast(12f)
                        previewIfEnabled()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("A-", color = textColor)
                }
                Text(text = fontSize.roundToInt().toString(), color = Color.Gray)
                TextButton(
                    onClick = {
                        fontSize = (fontSize + 1f).coerceAtMost(30f)
                        previewIfEnabled()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("A+", color = textColor)
                }
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = { onOpenSubPanel(ReaderSettingsPanel.Font) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = actionBg, contentColor = textColor),
                modifier = Modifier
                    .weight(0.8f)
                    .height(40.dp)
            ) {
                Text("字体")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("行高 ${"%.2f".format(lineHeight)}", color = textColor)
            Slider(
                value = lineHeight,
                valueRange = 1.1f..2.2f,
                onValueChange = {
                    lineHeight = it
                    previewIfEnabled()
                }
            )
            Text("边距 ${pagePadding.roundToInt()}dp", color = textColor)
            Slider(
                value = pagePadding,
                valueRange = 8f..36f,
                onValueChange = {
                    pagePadding = it
                    previewIfEnabled()
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = { onOpenSubPanel(ReaderSettingsPanel.Spacing) }) {
                Text("更多间距")
            }
            OutlinedButton(onClick = { onOpenSubPanel(ReaderSettingsPanel.PageTurn) }) {
                Text("翻页方式")
            }
            OutlinedButton(onClick = { onOpenSubPanel(ReaderSettingsPanel.MoreBackground) }) {
                Text("更多背景")
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
                Text("夜间模式", color = textColor)
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isNightMode,
                    onCheckedChange = { onToggleNightMode() }
                )
            }
            TextButton(onClick = onOpenFullSettings) {
                Text("更多设置 >", color = Color.Gray)
            }
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
            Button(
                onClick = {
                    onApply(draftConfig(), persist)
                }
            ) {
                Text("应用")
            }
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
    val unselectedBorder = if (isNightMode) Color(0x2EFFFFFF) else Color.LightGray

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
                                if (isNightMode) ReaderTokens.Palette.AccentBlue.copy(alpha = 0.2f) else ReaderTokens.Palette.AccentBlueSoft
                            } else {
                                cardBg
                            },
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) ReaderTokens.Palette.AccentBlue else unselectedBorder,
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
    val defaults = remember { RenderConfig.ReflowText() }
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
    var cjkLineBreakStrict by remember(current.cjkLineBreakStrict) { mutableStateOf(current.cjkLineBreakStrict) }
    var hangingPunctuation by remember(current.hangingPunctuation) { mutableStateOf(current.hangingPunctuation) }
    var pageInsetMode by remember(current.pageInsetMode) { mutableStateOf(current.pageInsetMode) }
    val textColor = if (isNightMode) Color(0xFFE9E5DE) else Color(0xFF1E1C18)

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
            cjkLineBreakStrict = cjkLineBreakStrict,
            hangingPunctuation = hangingPunctuation,
            pageInsetMode = pageInsetMode
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
        cjkLineBreakStrict = defaults.cjkLineBreakStrict
        hangingPunctuation = defaults.hangingPunctuation
        pageInsetMode = defaults.pageInsetMode
        previewIfEnabled()
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
            Text("CJK 严格换行", color = textColor)
            Switch(
                checked = cjkLineBreakStrict,
                onCheckedChange = {
                    cjkLineBreakStrict = it
                    previewIfEnabled()
                }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("标点悬挂", color = textColor)
            Switch(
                checked = hangingPunctuation,
                onCheckedChange = {
                    hangingPunctuation = it
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
    var persist by remember { mutableStateOf(true) }
    var livePreview by remember { mutableStateOf(true) }
    var selected by remember(current.extra) {
        mutableStateOf(current.pageTurnMode())
    }
    fun draftConfig(): RenderConfig.ReflowText {
        return current.withPageTurnMode(selected)
    }
    fun previewIfEnabled() {
        if (livePreview) {
            onApply(draftConfig(), false)
        }
    }

    val options = PageTurnMode.entries
    val optionBg = if (isNightMode) Color(0xFF2B2B2B) else Color(0xFFF4F5F6)
    val optionBorder = if (isNightMode) Color(0x2EFFFFFF) else Color.LightGray
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
                val isSelected = selected == option
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(width = 66.dp, height = 86.dp)
                            .background(optionBg, RoundedCornerShape(8.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) ReaderTokens.Palette.AccentBlue else optionBorder,
                                shape = RoundedCornerShape(8.dp)
                            )
                    )
                    TextButton(
                        onClick = {
                            selected = option
                            previewIfEnabled()
                        }
                    ) {
                        Text(
                            option.displayLabel(),
                            color = if (isSelected) ReaderTokens.Palette.AccentBlue else textColor.copy(alpha = 0.7f)
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
private fun MoreBackgroundPanel(
    isNightMode: Boolean,
    selectedPreset: ReaderBackgroundPreset,
    onBack: () -> Unit,
    onToggleNightMode: () -> Unit,
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
    val unselectedBorder = if (isNightMode) Color(0x2EFFFFFF) else Color.LightGray
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
                                ReaderTokens.Palette.AccentBlue
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("跟随夜间模式", color = textColor)
            Switch(checked = isNightMode, onCheckedChange = { onToggleNightMode() })
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
        containerColor = ReaderTokens.Palette.AccentBlue,
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
