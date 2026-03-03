package com.ireader.feature.reader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ireader.feature.reader.presentation.SearchState
import com.ireader.feature.reader.presentation.asString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(
    state: SearchState,
    onClose: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClickResult: (locatorEncoded: String) -> Unit
) {
    var query by remember(state.query) { mutableStateOf(state.query) }

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Search in book")
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onQueryChange(it)
                },
                label = { Text("Keyword") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                onClick = { onSearch(query) },
                enabled = query.isNotBlank() && !state.isSearching
            ) {
                Text(if (state.isSearching) "Searching..." else "Search")
            }

            if (state.error != null) {
                Text(state.error.asString())
            }

            Divider()
            LazyColumn {
                items(state.results) { item ->
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onClickResult(item.locatorEncoded) }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!item.title.isNullOrBlank()) {
                                Text(item.title)
                            }
                            Text(item.excerpt)
                        }
                    }
                }
            }
        }
    }
}

