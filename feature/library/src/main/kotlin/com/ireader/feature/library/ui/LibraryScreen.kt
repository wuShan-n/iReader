package com.ireader.feature.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
@Suppress("FunctionNaming")
fun LibraryScreen(
    onImportBooks: () -> Unit,
    importStatusText: String?,
    onOpenReader: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Library")
        Button(onClick = onImportBooks) { Text("Import Books") }
        if (!importStatusText.isNullOrBlank()) {
            Text(text = importStatusText)
        }
        Button(onClick = onOpenReader) { Text("Open Reader") }
        Button(onClick = onOpenSettings) { Text("Open Settings") }
    }
}
