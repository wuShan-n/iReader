package com.ireader.feature.library.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ireader.core.database.book.BookEntity
import java.io.File
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookGridItem(
    book: BookEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val title = bookTitle(book)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { confirmDelete = true }
            )
    ) {
        BookCover(
            coverPath = book.coverPath,
            titleFallback = title
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium
        )

        val author = book.author.orEmpty().trim()
        if (author.isNotBlank()) {
            Text(
                text = author,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Spacer(modifier = Modifier.height(2.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = book.format.name,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = sizeText(book.sizeBytes),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除书籍？") },
            text = { Text("将从书架移除，并删除本地已导入文件。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }
}

private fun bookTitle(book: BookEntity): String {
    val title = book.title?.trim().orEmpty()
    if (title.isNotBlank()) return title

    val displayName = book.displayName?.trim().orEmpty()
    if (displayName.isNotBlank()) return displayName.substringBeforeLast('.', displayName)

    return File(book.canonicalPath).nameWithoutExtension.ifBlank { "Untitled" }
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
