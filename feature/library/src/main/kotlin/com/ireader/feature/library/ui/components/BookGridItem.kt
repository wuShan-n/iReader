package com.ireader.feature.library.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ireader.core.designsystem.ReaderTokens
import com.ireader.core.data.book.IndexState
import com.ireader.core.data.book.LibraryBookItem
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookGridItem(
    book: LibraryBookItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false,
    isSelected: Boolean = false
) {
    val entity = book.book
    val title = bookTitle(entity.title, entity.fileName)
    val progressText = progressionText(book.progression)
    val selectionProgress = animateFloatAsState(
        targetValue = if (isEditMode && isSelected) 1f else 0f,
        animationSpec = tween(durationMillis = ReaderTokens.Motion.Medium),
        label = "book_item_selection"
    )
    val coverElevation = animateDpAsState(
        targetValue = if (isEditMode && isSelected) 10.dp else 5.dp,
        animationSpec = tween(durationMillis = ReaderTokens.Motion.Fast),
        label = "book_item_cover_elevation"
    )
    val cardStroke = animateColorAsState(
        targetValue = if (isEditMode && isSelected) {
            ReaderTokens.Palette.AccentBlue
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
        },
        animationSpec = tween(durationMillis = ReaderTokens.Motion.Fast),
        label = "book_item_stroke"
    )
    val metaColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface

    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(surfaceColor, RoundedCornerShape(14.dp))
                .border(width = 1.dp, color = cardStroke.value, shape = RoundedCornerShape(14.dp))
                .padding(6.dp)
        ) {
            BookCover(
                coverPath = entity.coverPath,
                titleFallback = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = coverElevation.value,
                        shape = MaterialTheme.shapes.medium,
                        clip = false
                    )
            )

            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(22.dp)
                        .background(
                            color = if (isSelected) ReaderTokens.Palette.AccentBlue else Color(0x78000000),
                            shape = CircleShape
                        )
                        .border(width = 1.dp, color = Color.White, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = progressText,
            style = MaterialTheme.typography.labelMedium,
            color = metaColor
        )

        val status = statusLabel(entity.indexState)
        if (status != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.defaultMinSize(minHeight = 18.dp)
            ) {
                Text(
                    text = status,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }

        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { book.progression.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )

        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entity.format.name,
                style = MaterialTheme.typography.bodySmall,
                color = metaColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (entity.favorite) {
                Text(
                    text = "收藏",
                    style = MaterialTheme.typography.bodySmall,
                    color = ReaderTokens.Palette.Warning
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = sizeText(entity.fileSizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = metaColor.copy(alpha = 0.9f * (1f - 0.15f * selectionProgress.value))
            )
        }
    }
}

private fun progressionText(progression: Double): String {
    if (progression <= 0.0) return "未读"
    val percent = (progression.coerceIn(0.0, 1.0) * 100.0).roundToInt()
    return "已读$percent%"
}

private fun statusLabel(state: IndexState): String? {
    return when (state) {
        IndexState.PENDING -> "待解析"
        IndexState.ERROR -> "解析失败"
        IndexState.MISSING -> "文件缺失"
        IndexState.INDEXED -> null
    }
}

private fun bookTitle(title: String?, fileName: String): String {
    val trimmed = title?.trim().orEmpty()
    if (trimmed.isNotEmpty()) return trimmed
    return fileName.substringBeforeLast('.', fileName).ifBlank { "未命名" }
}

private fun sizeText(bytes: Long): String {
    if (bytes <= 0L) return "0B"

    val kb = bytes / 1024.0
    if (kb < 1024.0) {
        return String.format(Locale.US, "%.0fKB", kb)
    }

    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1fMB", mb)
}
