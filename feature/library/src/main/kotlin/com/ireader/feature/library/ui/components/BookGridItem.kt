package com.ireader.feature.library.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ireader.core.designsystem.ReaderTokens
import com.ireader.core.data.book.LibraryBookItem
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
    val showSelectionBorder = isEditMode && isSelected
    val coverShape = RoundedCornerShape(9.dp)
    val cardStroke = animateColorAsState(
        targetValue = if (showSelectionBorder) {
            ReaderTokens.Palette.PrototypeBlue
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = ReaderTokens.Motion.Fast),
        label = "book_item_stroke"
    )
    val metaColor = if (showSelectionBorder) {
        ReaderTokens.Palette.PrototypeBlue
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val titleColor = MaterialTheme.colorScheme.onSurface

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
                .border(
                    width = if (showSelectionBorder) 1.dp else 0.dp,
                    color = cardStroke.value,
                    shape = coverShape
                )
        ) {
            BookCover(
                coverPath = entity.coverPath,
                titleFallback = title,
                shape = coverShape,
                modifier = Modifier
                    .fillMaxWidth()
            )

            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(22.dp)
                        .background(
                            color = if (isSelected) ReaderTokens.Palette.PrototypeBlue else Color(0x78000000),
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = titleColor
        )

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = progressText,
            style = MaterialTheme.typography.labelMedium,
            color = metaColor
        )
    }
}

private fun progressionText(progression: Double): String {
    if (progression <= 0.0) return "未读"
    val percent = (progression.coerceIn(0.0, 1.0) * 100.0).roundToInt()
    return "已读$percent%"
}

private fun bookTitle(title: String?, fileName: String): String {
    val trimmed = title?.trim().orEmpty()
    if (trimmed.isNotEmpty()) return trimmed
    return fileName.substringBeforeLast('.', fileName).ifBlank { "未命名" }
}
