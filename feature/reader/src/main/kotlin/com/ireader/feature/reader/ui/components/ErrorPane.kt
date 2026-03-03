package com.ireader.feature.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ireader.feature.reader.presentation.ReaderErrorAction
import com.ireader.feature.reader.presentation.ReaderUiError
import com.ireader.feature.reader.presentation.asString

@Composable
fun ErrorPane(
    error: ReaderUiError,
    onAction: (ReaderErrorAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = error.message.asString(), color = Color.White)
        val action = error.action
        if (action != null) {
            TextButton(onClick = { onAction(action) }) {
                Text(text = error.actionLabel?.asString() ?: action.name)
            }
        }
    }
}

