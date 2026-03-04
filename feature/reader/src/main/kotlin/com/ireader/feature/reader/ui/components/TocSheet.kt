package com.ireader.feature.reader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("目录")
            if (state.isLoading) {
                Text("加载中...")
            }
            if (state.error != null) {
                Text(state.error.asString())
            }

            HorizontalDivider()
            LazyColumn {
                items(state.items) { item ->
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onClick(item.locatorEncoded) }
                    ) {
                        Text(
                            text = item.title,
                            modifier = Modifier.padding(start = (item.depth * 12).dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
