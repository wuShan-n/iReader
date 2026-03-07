package com.ireader.feature.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ireader.core.data.reader.ReaderBackgroundPreset
import com.ireader.core.designsystem.PrototypeIcons
import com.ireader.core.designsystem.ReaderTokens
import com.ireader.reader.api.engine.DocumentCapabilities
import com.ireader.reader.api.render.PAGE_PADDING_BOTTOM_DP_EXTRA_KEY
import com.ireader.reader.api.render.PAGE_PADDING_TOP_DP_EXTRA_KEY
import com.ireader.reader.api.render.REFLOW_LINE_HEIGHT_MAX
import com.ireader.reader.api.render.REFLOW_LINE_HEIGHT_MIN
import com.ireader.reader.api.render.REFLOW_PAGE_PADDING_HORIZONTAL_MAX_DP
import com.ireader.reader.api.render.REFLOW_PAGE_PADDING_HORIZONTAL_MIN_DP
import com.ireader.reader.api.render.REFLOW_PAGE_PADDING_VERTICAL_MAX_DP
import com.ireader.reader.api.render.REFLOW_PAGE_PADDING_VERTICAL_MIN_DP
import com.ireader.reader.api.render.REFLOW_PARAGRAPH_SPACING_MAX_DP
import com.ireader.reader.api.render.REFLOW_PARAGRAPH_SPACING_MIN_DP
import com.ireader.reader.api.render.RenderConfig
import com.ireader.feature.reader.presentation.PageTurnStyle
import com.ireader.feature.reader.presentation.pageTurnStyle
import com.ireader.feature.reader.presentation.withPageTurnStyle
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
    isEmbeddedEpub: Boolean,
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
                isEmbeddedEpub = isEmbeddedEpub,
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
            pagePaddingDp = 20f,
            respectPublisherStyles = false,
            extra = mapOf(
                PAGE_PADDING_TOP_DP_EXTRA_KEY to "20.0",
                PAGE_PADDING_BOTTOM_DP_EXTRA_KEY to "20.0"
            )
        )
    }
    data class SpacingPreset(
        val label: String,
        val lineHeight: Float,
        val paragraph: Float,
        val horizontalPadding: Float,
        val topPadding: Float,
        val bottomPadding: Float
    )
    val spacingPresets = remember {
        listOf(
            SpacingPreset(
                label = "紧凑",
                lineHeight = 1.45f,
                paragraph = 4f,
                horizontalPadding = 14f,
                topPadding = 10f,
                bottomPadding = 10f
            ),
            SpacingPreset(
                label = "默认",
                lineHeight = 1.85f,
                paragraph = 10f,
                horizontalPadding = 20f,
                topPadding = 20f,
                bottomPadding = 20f
            ),
            SpacingPreset(
                label = "宽松",
                lineHeight = 2.0f,
                paragraph = 14f,
                horizontalPadding = 24f,
                topPadding = 26f,
                bottomPadding = 26f
            )
        )
    }
    var lineHeight by remember(current.lineHeightMult) {
        mutableFloatStateOf(current.lineHeightMult.coerceIn(REFLOW_LINE_HEIGHT_MIN, REFLOW_LINE_HEIGHT_MAX))
    }
    var paragraph by remember(current.paragraphSpacingDp) {
        mutableFloatStateOf(
            current.paragraphSpacingDp.coerceIn(
                REFLOW_PARAGRAPH_SPACING_MIN_DP,
                REFLOW_PARAGRAPH_SPACING_MAX_DP
            )
        )
    }
    var horizontalPadding by remember(current.pagePaddingDp) {
        mutableFloatStateOf(
            current.pagePaddingDp.coerceIn(
                REFLOW_PAGE_PADDING_HORIZONTAL_MIN_DP,
                REFLOW_PAGE_PADDING_HORIZONTAL_MAX_DP
            )
        )
    }
    var topPadding by remember(current.extra, current.pagePaddingDp) {
        mutableFloatStateOf(current.resolveTopPaddingDp())
    }
    var bottomPadding by remember(current.extra, current.pagePaddingDp) {
        mutableFloatStateOf(current.resolveBottomPaddingDp())
    }
    var respectPublisherStyles by remember(current.respectPublisherStyles) { mutableStateOf(current.respectPublisherStyles) }
    val textColor = if (isNightMode) Color(0xFFE9E5DE) else Color(0xFF1E1C18)
    val subColor = if (isNightMode) Color(0xFF9C978E) else Color(0xFF6F6A62)
    val allowParagraphSpacingAdjustments = !respectPublisherStyles

    fun draftConfig(): RenderConfig.ReflowText {
        return current.copy(
            lineHeightMult = lineHeight,
            paragraphSpacingDp = paragraph,
            pagePaddingDp = horizontalPadding,
            respectPublisherStyles = respectPublisherStyles,
            extra = current.extra +
                (PAGE_PADDING_TOP_DP_EXTRA_KEY to topPadding.toString()) +
                (PAGE_PADDING_BOTTOM_DP_EXTRA_KEY to bottomPadding.toString())
        )
    }

    fun applyDraft() {
        onApply(draftConfig(), false)
    }

    fun resetToDefaults() {
        lineHeight = defaults.lineHeightMult
        paragraph = defaults.paragraphSpacingDp
        horizontalPadding = defaults.pagePaddingDp
        topPadding = defaults.resolveTopPaddingDp()
        bottomPadding = defaults.resolveBottomPaddingDp()
        respectPublisherStyles = defaults.respectPublisherStyles
        applyDraft()
    }

    fun applyPreset(preset: SpacingPreset) {
        lineHeight = preset.lineHeight
        paragraph = preset.paragraph
        horizontalPadding = preset.horizontalPadding
        topPadding = preset.topPadding
        bottomPadding = preset.bottomPadding
        applyDraft()
    }

    fun isPresetSelected(preset: SpacingPreset): Boolean {
        return abs(lineHeight - preset.lineHeight) < 0.02f &&
            abs(paragraph - preset.paragraph) < 0.5f &&
            abs(horizontalPadding - preset.horizontalPadding) < 0.5f &&
            abs(topPadding - preset.topPadding) < 0.5f &&
            abs(bottomPadding - preset.bottomPadding) < 0.5f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.78f)
            .verticalScroll(rememberScrollState())
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
                    "开启后，行距/段距等可能不生效",
                    color = subColor,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = respectPublisherStyles,
                onCheckedChange = {
                    respectPublisherStyles = it
                    applyDraft()
                }
            )
        }
        if (!allowParagraphSpacingAdjustments) {
            Text(
                "当前已启用出版方样式，行距/段距/边距将由书籍样式控制",
                color = subColor,
                style = MaterialTheme.typography.bodySmall
            )
        }
        SettingSliderRow(
            label = "行间距",
            valueLabel = "%.2f".format(lineHeight),
            value = lineHeight,
            range = REFLOW_LINE_HEIGHT_MIN..REFLOW_LINE_HEIGHT_MAX,
            step = 0.05f,
            textColor = textColor,
            enabled = allowParagraphSpacingAdjustments,
            onChange = {
                lineHeight = it
                applyDraft()
            }
        )
        SettingSliderRow(
            label = "段间距",
            valueLabel = "${paragraph.roundToInt()}dp",
            value = paragraph,
            range = REFLOW_PARAGRAPH_SPACING_MIN_DP..REFLOW_PARAGRAPH_SPACING_MAX_DP,
            step = 1f,
            textColor = textColor,
            enabled = allowParagraphSpacingAdjustments,
            onChange = {
                paragraph = it
                applyDraft()
            }
        )
        SettingSliderRow(
            label = "左右间距",
            valueLabel = "${horizontalPadding.roundToInt()}dp",
            value = horizontalPadding,
            range = REFLOW_PAGE_PADDING_HORIZONTAL_MIN_DP..REFLOW_PAGE_PADDING_HORIZONTAL_MAX_DP,
            step = 1f,
            textColor = textColor,
            enabled = allowParagraphSpacingAdjustments,
            onChange = {
                horizontalPadding = it
                applyDraft()
            }
        )
        SettingSliderRow(
            label = "上间距",
            valueLabel = "${topPadding.roundToInt()}dp",
            value = topPadding,
            range = REFLOW_PAGE_PADDING_VERTICAL_MIN_DP..REFLOW_PAGE_PADDING_VERTICAL_MAX_DP,
            step = 1f,
            textColor = textColor,
            enabled = allowParagraphSpacingAdjustments,
            onChange = {
                topPadding = it
                applyDraft()
            }
        )
        SettingSliderRow(
            label = "下间距",
            valueLabel = "${bottomPadding.roundToInt()}dp",
            value = bottomPadding,
            range = REFLOW_PAGE_PADDING_VERTICAL_MIN_DP..REFLOW_PAGE_PADDING_VERTICAL_MAX_DP,
            step = 1f,
            textColor = textColor,
            enabled = allowParagraphSpacingAdjustments,
            onChange = {
                bottomPadding = it
                applyDraft()
            }
        )
    }
}

private fun RenderConfig.ReflowText.resolveTopPaddingDp(): Float {
    val raw = extra[PAGE_PADDING_TOP_DP_EXTRA_KEY]
    return (raw?.toFloatOrNull() ?: pagePaddingDp)
        .takeIf(Float::isFinite)
        ?.coerceIn(REFLOW_PAGE_PADDING_VERTICAL_MIN_DP, REFLOW_PAGE_PADDING_VERTICAL_MAX_DP)
        ?: pagePaddingDp.coerceIn(REFLOW_PAGE_PADDING_VERTICAL_MIN_DP, REFLOW_PAGE_PADDING_VERTICAL_MAX_DP)
}

private fun RenderConfig.ReflowText.resolveBottomPaddingDp(): Float {
    val raw = extra[PAGE_PADDING_BOTTOM_DP_EXTRA_KEY]
    return (raw?.toFloatOrNull() ?: pagePaddingDp)
        .takeIf(Float::isFinite)
        ?.coerceIn(REFLOW_PAGE_PADDING_VERTICAL_MIN_DP, REFLOW_PAGE_PADDING_VERTICAL_MAX_DP)
        ?: pagePaddingDp.coerceIn(REFLOW_PAGE_PADDING_VERTICAL_MIN_DP, REFLOW_PAGE_PADDING_VERTICAL_MAX_DP)
}

@Composable
private fun PageTurnPanel(
    current: RenderConfig.ReflowText,
    isEmbeddedEpub: Boolean,
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
    val subColor = if (isNightMode) Color(0xFF9C978E) else Color(0xFF6F6A62)
    val controlsEnabled = !isEmbeddedEpub
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SheetHeader(title = "翻页方式", onBack = onBack)
        Text(
            text = "可重排文档统一使用点击区翻页；TXT 额外支持滑动翻页。",
            color = subColor,
            style = MaterialTheme.typography.bodySmall
        )
        if (isEmbeddedEpub) {
            Text(
                text = "当前 EPUB 的连续翻页动画由阅读内核控制，这里主要调整文本页动效预览。",
                color = subColor,
                style = MaterialTheme.typography.bodySmall
            )
        }
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
                        enabled = controlsEnabled,
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
                enabled = controlsEnabled,
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
                    enabled = controlsEnabled,
                    onCheckedChange = { persist = it }
                )
                Text("保存为默认", color = textColor)
            }
            Button(
                enabled = controlsEnabled,
                onClick = { onApply(draftConfig(), persist) }
            ) {
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
        Text(
            "固定版式优先缩放与拖拽；仅在原始视图下响应边缘点按翻页。",
            color = textColor.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodySmall
        )
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
    step: Float = 0f,
    textColor: Color,
    enabled: Boolean = true,
    onChange: (Float) -> Unit
) {
    val effectiveTextColor = if (enabled) textColor else textColor.copy(alpha = 0.52f)
    val snappedValue = remember(value, range, step) { snapSliderValue(value, range, step) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = effectiveTextColor,
            modifier = Modifier.width(64.dp)
        )
        Slider(
            value = snappedValue,
            valueRange = range,
            steps = sliderDiscreteSteps(range, step),
            enabled = enabled,
            onValueChange = { raw ->
                onChange(snapSliderValue(raw, range, step))
            },
            modifier = Modifier.weight(1f)
        )
        Text(
            text = valueLabel,
            color = effectiveTextColor.copy(alpha = 0.7f),
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp)
        )
    }
}

@Composable
private fun ChoiceButton(
    label: String,
    selected: Boolean,
    isNightMode: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val selectedColors = ButtonDefaults.buttonColors(
        containerColor = ReaderTokens.Palette.PrototypeBlue,
        contentColor = Color.White
    )
    val unselectedText = if (isNightMode) Color(0xFFE9E5DE) else Color(0xFF24201C)

    if (selected) {
        Button(
            enabled = enabled,
            onClick = onClick,
            colors = selectedColors
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            enabled = enabled,
            onClick = onClick
        ) {
            Text(label, color = unselectedText)
        }
    }
}
