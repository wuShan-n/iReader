package com.ireader.feature.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
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
import com.ireader.reader.api.render.BreakStrategyMode
import com.ireader.reader.api.render.HyphenationMode
import com.ireader.core.designsystem.ReaderTokens
import com.ireader.reader.api.render.PageInsetMode
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.TextAlignMode
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
    onClose: () -> Unit,
    onBackToMain: () -> Unit,
    onOpenSubPanel: (ReaderSettingsPanel) -> Unit,
    onOpenFullSettings: () -> Unit,
    onToggleNightMode: () -> Unit,
    onApply: (RenderConfig, persist: Boolean) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onClose) {
        if (capabilities?.fixedLayout == true) {
            FixedLayoutSettings(
                config = config as? RenderConfig.FixedPage ?: RenderConfig.FixedPage(),
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
                onBack = onBackToMain,
                onApply = onApply
            )

            ReaderSettingsPanel.Spacing -> SpacingPanel(
                current = current,
                onBack = onBackToMain,
                onApply = onApply
            )

            ReaderSettingsPanel.PageTurn -> PageTurnPanel(
                current = current,
                onBack = onBackToMain,
                onApply = onApply
            )

            ReaderSettingsPanel.MoreBackground -> MoreBackgroundPanel(
                isNightMode = isNightMode,
                onBack = onBackToMain,
                onToggleNightMode = onToggleNightMode
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
    var fontSize by remember(config.fontSizeSp) { mutableFloatStateOf(config.fontSizeSp) }
    var lineHeight by remember(config.lineHeightMult) { mutableFloatStateOf(config.lineHeightMult) }
    var pagePadding by remember(config.pagePaddingDp) { mutableFloatStateOf(config.pagePaddingDp) }

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
                        onApply(config.copy(fontSizeSp = fontSize, lineHeightMult = lineHeight, pagePaddingDp = pagePadding), false)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("A-", color = textColor)
                }
                Text(text = fontSize.roundToInt().toString(), color = Color.Gray)
                TextButton(
                    onClick = {
                        fontSize = (fontSize + 1f).coerceAtMost(30f)
                        onApply(config.copy(fontSizeSp = fontSize, lineHeightMult = lineHeight, pagePaddingDp = pagePadding), false)
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
                    onApply(config.copy(fontSizeSp = fontSize, lineHeightMult = it, pagePaddingDp = pagePadding), false)
                }
            )
            Text("边距 ${pagePadding.roundToInt()}dp", color = textColor)
            Slider(
                value = pagePadding,
                valueRange = 8f..36f,
                onValueChange = {
                    pagePadding = it
                    onApply(config.copy(fontSizeSp = fontSize, lineHeightMult = lineHeight, pagePaddingDp = it), false)
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
                    onApply(
                        config.copy(
                            fontSizeSp = fontSize,
                            lineHeightMult = lineHeight,
                            pagePaddingDp = pagePadding
                        ),
                        persist
                    )
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
    onBack: () -> Unit,
    onApply: (RenderConfig, persist: Boolean) -> Unit
) {
    val fonts = listOf("系统字体", "思源宋体", "霞鹜文楷", "方正新楷体")
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
                val selected = current.fontFamilyName == name || (index == 0 && current.fontFamilyName == null)
                Box(
                    modifier = Modifier
                        .height(70.dp)
                        .background(
                            if (selected) ReaderTokens.Palette.AccentBlueSoft else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) ReaderTokens.Palette.AccentBlue else Color.LightGray,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    TextButton(
                        onClick = {
                            val family = if (index == 0) null else name
                            onApply(current.copy(fontFamilyName = family), false)
                        }
                    ) {
                        Text(name)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpacingPanel(
    current: RenderConfig.ReflowText,
    onBack: () -> Unit,
    onApply: (RenderConfig, persist: Boolean) -> Unit
) {
    val defaults = remember { RenderConfig.ReflowText() }
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
        onApply(draftConfig(), false)
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
            onChange = {
                lineHeight = it
                onApply(draftConfig(), false)
            }
        )
        SettingSliderRow(
            label = "首行缩进",
            valueLabel = "${"%.1f".format(paragraphIndent)}em",
            value = paragraphIndent,
            range = 0f..3.2f,
            onChange = {
                paragraphIndent = it
                onApply(draftConfig(), false)
            }
        )
        SettingSliderRow(
            label = "段间距",
            valueLabel = "${paragraph.roundToInt()}dp",
            value = paragraph,
            range = 0f..24f,
            onChange = {
                paragraph = it
                onApply(draftConfig(), false)
            }
        )
        SettingSliderRow(
            label = "左右边距",
            valueLabel = "${padding.roundToInt()}dp",
            value = padding,
            range = 8f..42f,
            onChange = {
                padding = it
                onApply(draftConfig(), false)
            }
        )

        Text("对齐方式")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceButton(
                label = "左对齐",
                selected = textAlign == TextAlignMode.START,
                onClick = {
                    textAlign = TextAlignMode.START
                    onApply(draftConfig(), false)
                }
            )
            ChoiceButton(
                label = "两端对齐",
                selected = textAlign == TextAlignMode.JUSTIFY,
                onClick = {
                    textAlign = TextAlignMode.JUSTIFY
                    onApply(draftConfig(), false)
                }
            )
        }

        Text("断行策略")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceButton(
                label = "快速",
                selected = breakStrategy == BreakStrategyMode.SIMPLE,
                onClick = {
                    breakStrategy = BreakStrategyMode.SIMPLE
                    onApply(draftConfig(), false)
                }
            )
            ChoiceButton(
                label = "平衡",
                selected = breakStrategy == BreakStrategyMode.BALANCED,
                onClick = {
                    breakStrategy = BreakStrategyMode.BALANCED
                    onApply(draftConfig(), false)
                }
            )
            ChoiceButton(
                label = "高质量",
                selected = breakStrategy == BreakStrategyMode.HIGH_QUALITY,
                onClick = {
                    breakStrategy = BreakStrategyMode.HIGH_QUALITY
                    onApply(draftConfig(), false)
                }
            )
        }

        Text("断词")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceButton(
                label = "关闭",
                selected = hyphenationMode == HyphenationMode.NONE,
                onClick = {
                    hyphenationMode = HyphenationMode.NONE
                    onApply(draftConfig(), false)
                }
            )
            ChoiceButton(
                label = "普通",
                selected = hyphenationMode == HyphenationMode.NORMAL,
                onClick = {
                    hyphenationMode = HyphenationMode.NORMAL
                    onApply(draftConfig(), false)
                }
            )
            ChoiceButton(
                label = "增强",
                selected = hyphenationMode == HyphenationMode.FULL,
                onClick = {
                    hyphenationMode = HyphenationMode.FULL
                    onApply(draftConfig(), false)
                }
            )
        }

        Text("页面留白")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceButton(
                label = "舒适",
                selected = pageInsetMode == PageInsetMode.RELAXED,
                onClick = {
                    pageInsetMode = PageInsetMode.RELAXED
                    onApply(draftConfig(), false)
                }
            )
            ChoiceButton(
                label = "紧凑",
                selected = pageInsetMode == PageInsetMode.COMPACT,
                onClick = {
                    pageInsetMode = PageInsetMode.COMPACT
                    onApply(draftConfig(), false)
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("字体安全留白")
            Switch(
                checked = includeFontPadding,
                onCheckedChange = {
                    includeFontPadding = it
                    onApply(draftConfig(), false)
                }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("CJK 严格换行")
            Switch(
                checked = cjkLineBreakStrict,
                onCheckedChange = {
                    cjkLineBreakStrict = it
                    onApply(draftConfig(), false)
                }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("标点悬挂")
            Switch(
                checked = hangingPunctuation,
                onCheckedChange = {
                    hangingPunctuation = it
                    onApply(draftConfig(), false)
                }
            )
        }
    }
}

@Composable
private fun PageTurnPanel(
    current: RenderConfig.ReflowText,
    onBack: () -> Unit,
    onApply: (RenderConfig, persist: Boolean) -> Unit
) {
    var selected by remember(current.extra) {
        mutableStateOf(current.extra["page_turn"] ?: "左右覆盖")
    }
    val options = listOf("仿真翻页", "左右覆盖", "上下滑动", "无动效")
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
                            .background(Color(0xFFF4F5F6), RoundedCornerShape(8.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) ReaderTokens.Palette.AccentBlue else Color.LightGray,
                                shape = RoundedCornerShape(8.dp)
                            )
                    )
                    TextButton(
                        onClick = {
                            selected = option
                            onApply(
                                current.copy(extra = current.extra + ("page_turn" to option)),
                                false
                            )
                        }
                    ) {
                        Text(option, color = if (isSelected) ReaderTokens.Palette.AccentBlue else Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreBackgroundPanel(
    isNightMode: Boolean,
    onBack: () -> Unit,
    onToggleNightMode: () -> Unit
) {
    val backgrounds = listOf(
        Color(0xFFFDF9F3),
        Color(0xFFF3E7CA),
        Color(0xFFDFD4C5),
        Color(0xFFCCE0D1),
        Color(0xFF2B2B2B),
        Color(0xFF1A1F2B)
    )
    Column(
        modifier = Modifier
            .fillMaxHeight(0.65f)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SheetHeader(title = "更多背景", onBack = onBack)
        Text("背景色", fontWeight = FontWeight.Medium)
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height(120.dp)
        ) {
            items(backgrounds.size) { index ->
                val color = backgrounds[index]
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(color, CircleShape)
                        .border(
                            width = if ((isNightMode && index >= 4) || (!isNightMode && index == 0)) 2.dp else 1.dp,
                            color = if ((isNightMode && index >= 4) || (!isNightMode && index == 0)) {
                                ReaderTokens.Palette.AccentBlue
                            } else {
                                Color.LightGray
                            },
                            shape = CircleShape
                        )
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("跟随夜间模式")
            Switch(checked = isNightMode, onCheckedChange = { onToggleNightMode() })
        }
    }
}

@Composable
private fun FixedLayoutSettings(
    config: RenderConfig.FixedPage,
    onApply: (RenderConfig, persist: Boolean) -> Unit
) {
    var persist by remember { mutableStateOf(true) }
    var zoom by remember(config.zoom) { mutableFloatStateOf(config.zoom) }
    var rotation by remember(config.rotationDegrees) { mutableIntStateOf(config.rotationDegrees) }
    var fitMode by remember(config.fitMode) { mutableStateOf(config.fitMode) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("版式设置")
        Text("缩放 ${"%.2f".format(zoom)}")
        Slider(
            value = zoom,
            valueRange = 0.6f..4f,
            onValueChange = {
                zoom = it
                onApply(config.copy(zoom = it, rotationDegrees = rotation, fitMode = fitMode), false)
            }
        )

        Text("旋转")
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

        Text("适配")
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
            Text("保存为默认")
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
            Icon(imageVector = Icons.Outlined.KeyboardArrowDown, contentDescription = "返回")
        }
        Text(text = title, fontWeight = FontWeight.Medium)
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
    onChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label)
            Text(valueLabel, color = Color.Gray)
        }
        Slider(value = value, valueRange = range, onValueChange = onChange)
    }
}

@Composable
private fun ChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        Button(onClick = onClick) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Text(label)
        }
    }
}
