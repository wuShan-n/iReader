package com.ireader.feature.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ireader.core.designsystem.PrototypeIcons
import com.ireader.core.designsystem.ReaderTokens
import com.ireader.feature.reader.presentation.ReaderMenuTab
import com.ireader.feature.reader.presentation.TocState
import com.ireader.feature.reader.presentation.asString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TocSheet(
    state: TocState,
    activeTab: ReaderMenuTab,
    isNightMode: Boolean,
    onClose: () -> Unit,
    onTabChange: (ReaderMenuTab) -> Unit,
    onOpenAnnotations: () -> Unit,
    onClick: (locatorEncoded: String) -> Unit
) {
    val container = if (isNightMode) {
        ReaderTokens.Palette.ReaderPanelElevatedNight
    } else {
        ReaderTokens.Palette.PrototypeSurface
    }
    val tabsBg = if (isNightMode) Color(0xFF343434) else ReaderTokens.Palette.PrototypeSurfaceMuted
    val selectedBg = if (isNightMode) Color(0xFF252525) else Color.White
    val textColor = if (isNightMode) Color(0xFFE8E3DA) else ReaderTokens.Palette.PrototypeTextPrimary
    val secondary = if (isNightMode) ReaderTokens.Palette.SecondaryTextNight else ReaderTokens.Palette.PrototypeTextSecondary
    val divider = if (isNightMode) Color(0xFF3A3A3A) else ReaderTokens.Palette.PrototypeBorder

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("剑来 (1-54册) 完结精校版", style = MaterialTheme.typography.titleMedium, color = textColor)
                Text(
                    text = "${state.items.size} 章",
                    style = MaterialTheme.typography.labelMedium,
                    color = secondary
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tabsBg, RoundedCornerShape(999.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SheetMenuTab(
                    modifier = Modifier.weight(1f),
                    label = "目录",
                    selected = activeTab == ReaderMenuTab.Toc,
                    selectedColor = selectedBg,
                    textColor = textColor,
                    onClick = { onTabChange(ReaderMenuTab.Toc) }
                )
                SheetMenuTab(
                    modifier = Modifier.weight(1f),
                    label = "笔记",
                    selected = activeTab == ReaderMenuTab.Notes,
                    selectedColor = selectedBg,
                    textColor = textColor,
                    onClick = { onTabChange(ReaderMenuTab.Notes) }
                )
                SheetMenuTab(
                    modifier = Modifier.weight(1f),
                    label = "书签",
                    selected = activeTab == ReaderMenuTab.Bookmarks,
                    selectedColor = selectedBg,
                    textColor = textColor,
                    onClick = { onTabChange(ReaderMenuTab.Bookmarks) }
                )
            }

            HorizontalDivider(color = divider)

            when (activeTab) {
                ReaderMenuTab.Toc -> TocList(
                    state = state,
                    isNightMode = isNightMode,
                    onClick = onClick
                )

                ReaderMenuTab.Notes -> PlaceholderPanel(
                    icon = { tint -> PrototypeIcons.Note(tint = tint) },
                    title = "暂无笔记",
                    desc = "可在阅读页选中文本后添加笔记",
                    cta = "打开笔记页",
                    isNightMode = isNightMode,
                    onCta = onOpenAnnotations
                )

                ReaderMenuTab.Bookmarks -> PlaceholderPanel(
                    icon = { tint -> PrototypeIcons.Bookmark(tint = tint) },
                    title = "暂无书签",
                    desc = "在更多功能里添加书签后会显示在这里",
                    cta = "继续阅读",
                    isNightMode = isNightMode,
                    onCta = onClose
                )
            }
        }
    }
}

@Composable
private fun TocList(
    state: TocState,
    isNightMode: Boolean,
    onClick: (locatorEncoded: String) -> Unit
) {
    val itemText = if (isNightMode) Color(0xFFE8E3DA) else ReaderTokens.Palette.PrototypeTextPrimary
    val divider = if (isNightMode) Color(0xFF3A3A3A) else ReaderTokens.Palette.PrototypeBorder
    val active = ReaderTokens.Palette.PrototypeBlue
    if (state.isLoading) {
        Text("加载中...", style = MaterialTheme.typography.bodySmall, color = itemText.copy(alpha = 0.8f))
    }
    if (state.error != null) {
        Text(
            state.error.asString(),
            style = MaterialTheme.typography.bodySmall,
            color = ReaderTokens.Palette.PrototypeDanger
        )
    }

    LazyColumn(
        modifier = Modifier.height(420.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        itemsIndexed(state.items) { index, item ->
            TextButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                onClick = { onClick(item.locatorEncoded) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (item.depth * 10).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (index == 0) active else itemText
                    )
                }
            }
            if (index < state.items.lastIndex) {
                HorizontalDivider(color = divider)
            }
        }
    }
}

@Composable
private fun SheetMenuTab(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    selectedColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                color = if (selected) selectedColor else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(999.dp)
            )
    ) {
        TextButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick
        ) {
            Text(
                text = label,
                color = if (selected) textColor else textColor.copy(alpha = 0.62f)
            )
        }
    }
}

@Composable
private fun PlaceholderPanel(
    icon: @Composable (Color) -> Unit,
    title: String,
    desc: String,
    cta: String,
    isNightMode: Boolean,
    onCta: () -> Unit
) {
    val textColor = if (isNightMode) Color(0xFFE8E3DA) else ReaderTokens.Palette.PrototypeTextPrimary
    val subColor = if (isNightMode) ReaderTokens.Palette.SecondaryTextNight else ReaderTokens.Palette.PrototypeTextSecondary
    val card = if (isNightMode) ReaderTokens.Palette.ReaderPanelNight else ReaderTokens.Palette.LibraryCardDay
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(card, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon(subColor)
            Text(title, color = textColor, style = MaterialTheme.typography.titleMedium)
            Text(desc, color = subColor, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onCta) { Text(cta) }
        }
    }
}
