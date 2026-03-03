package com.ireader.feature.reader.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ireader.feature.reader.presentation.PasswordPrompt
import com.ireader.feature.reader.presentation.asString

@Composable
fun PasswordDialog(
    prompt: PasswordPrompt,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var password by remember { mutableStateOf(prompt.lastTried.orEmpty()) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Password required") },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(prompt.reason.asString()) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(password) },
                enabled = password.isNotBlank()
            ) {
                Text("Open")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

