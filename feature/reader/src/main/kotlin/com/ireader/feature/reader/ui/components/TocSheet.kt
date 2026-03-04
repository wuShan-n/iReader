package com.ireader.feature.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ireader.feature.reader.presentation.TocState
import com.ireader.feature.reader.presentation.asString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TocSheet(
    state: TocState,
    onClose: () -> Unit,
    onClick: (locatorEncoded: String) -> Unit
) {
    val container = MaterialTheme.colorScheme.surface
    val itemColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)

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
                Text("目录", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${state.items.size} 章",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.isLoading) {
                Text("加载中...", style = MaterialTheme.typography.bodySmall)
            }
            if (state.error != null) {
                Text(
                    state.error.asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            HorizontalDivider()
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.items) { item ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = itemColor
                    ) {
                        TextButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            onClick = { onClick(item.locatorEncoded) }
                        ) {
                            Text(
                                text = item.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = (item.depth * 10).dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
