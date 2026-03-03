package com.ireader.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ireader.feature.reader.presentation.ReaderErrorAction
import com.ireader.feature.reader.presentation.ReaderIntent
import com.ireader.feature.reader.presentation.ReaderSheet
import com.ireader.feature.reader.presentation.ReaderUiState
import com.ireader.feature.reader.presentation.asString
import com.ireader.feature.reader.ui.components.ErrorPane
import com.ireader.feature.reader.ui.components.PageRenderer
import com.ireader.feature.reader.ui.components.PasswordDialog
import com.ireader.feature.reader.ui.components.SearchSheet
import com.ireader.feature.reader.ui.components.SettingsSheet
import com.ireader.feature.reader.ui.components.TocSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScaffold(
    state: ReaderUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onIntent: (ReaderIntent) -> Unit,
    onOpenLocator: (String) -> Unit,
    onWebSchemeUrl: (String) -> Boolean
) {
    val title = state.title ?: "Reader"

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (state.chromeVisible) {
                TopAppBar(
                    title = { Text(text = title) },
                    navigationIcon = {
                        TextButton(onClick = onBack) {
                            Text("Back")
                        }
                    },
                    actions = {
                        TextButton(onClick = { onIntent(ReaderIntent.OpenAnnotations) }) { Text("Mark") }
                        TextButton(onClick = { onIntent(ReaderIntent.OpenToc) }) { Text("Toc") }
                        TextButton(onClick = { onIntent(ReaderIntent.OpenSearch) }) { Text("Search") }
                        TextButton(onClick = { onIntent(ReaderIntent.OpenSettings) }) { Text("Style") }
                    }
                )
            }
        },
        bottomBar = {
            val renderState = state.renderState
            if (state.chromeVisible && renderState != null) {
                val liveProgress = renderState.progression.percent.toFloat().coerceIn(0f, 1f)
                var sliderProgress by remember(liveProgress) { mutableFloatStateOf(liveProgress) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.04f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            enabled = renderState.nav.canGoPrev,
                            onClick = { onIntent(ReaderIntent.Prev) }
                        ) { Text("Prev") }
                        TextButton(
                            enabled = renderState.nav.canGoNext,
                            onClick = { onIntent(ReaderIntent.Next) }
                        ) { Text("Next") }
                    }
                    Slider(
                        value = sliderProgress,
                        onValueChange = { sliderProgress = it.coerceIn(0f, 1f) },
                        onValueChangeFinished = {
                            onIntent(ReaderIntent.GoToProgress(sliderProgress.toDouble()))
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PageRenderer(
                state = state,
                onToggleChrome = { onIntent(ReaderIntent.ToggleChrome) },
                onWebSchemeUrl = onWebSchemeUrl
            )

            if (state.isOpening) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            if (state.isRenderingFinal) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                )
            }

            val error = state.error
            if (error != null) {
                ErrorPane(
                    error = error,
                    onAction = { action ->
                        when (action) {
                            ReaderErrorAction.Retry -> onIntent(ReaderIntent.RetryOpen)
                            ReaderErrorAction.Back -> onBack()
                        }
                    }
                )
            }
        }

        val passwordPrompt = state.passwordPrompt
        if (passwordPrompt != null) {
            PasswordDialog(
                prompt = passwordPrompt,
                onSubmit = { pwd -> onIntent(ReaderIntent.SubmitPassword(pwd)) },
                onCancel = { onIntent(ReaderIntent.CancelPassword) }
            )
        }

        when (state.sheet) {
            ReaderSheet.None -> Unit
            ReaderSheet.Toc -> TocSheet(
                state = state.toc,
                onClose = { onIntent(ReaderIntent.CloseSheet) },
                onClick = { encoded ->
                    onOpenLocator(encoded)
                    onIntent(ReaderIntent.CloseSheet)
                }
            )

            ReaderSheet.Search -> SearchSheet(
                state = state.search,
                onClose = { onIntent(ReaderIntent.CloseSheet) },
                onQueryChange = { q -> onIntent(ReaderIntent.SearchQueryChanged(q)) },
                onSearch = { onIntent(ReaderIntent.ExecuteSearch) },
                onClickResult = { encoded ->
                    onOpenLocator(encoded)
                    onIntent(ReaderIntent.CloseSheet)
                }
            )

            ReaderSheet.Settings -> SettingsSheet(
                capabilities = state.capabilities,
                config = state.currentConfig,
                onClose = { onIntent(ReaderIntent.CloseSheet) },
                onApply = { cfg, persist -> onIntent(ReaderIntent.UpdateConfig(cfg, persist)) }
            )
        }
    }
}
