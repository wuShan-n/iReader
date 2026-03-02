package com.ireader.feature.reader.ui

import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ireader.feature.reader.presentation.ReaderUiState
import com.ireader.feature.reader.presentation.ReaderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionNaming")
fun ReaderScreen(
    bookId: String,
    onOpenAnnotations: () -> Unit,
    onOpenSearch: () -> Unit,
    vm: ReaderViewModel = hiltViewModel()
) {
    val uiState by vm.uiState.collectAsState()
    val density = LocalDensity.current

    LaunchedEffect(bookId) {
        vm.loadBook(bookId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("阅读") },
                actions = {
                    TextButton(onClick = onOpenAnnotations) { Text("标注") }
                    TextButton(onClick = onOpenSearch) { Text("搜索") }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val widthPx = with(density) { maxWidth.roundToPx() }
            val heightPx = with(density) { maxHeight.roundToPx() }

            LaunchedEffect(widthPx, heightPx, density.density, density.fontScale) {
                vm.onViewportChanged(
                    widthPx = widthPx,
                    heightPx = heightPx,
                    density = density.density,
                    fontScale = density.fontScale
                )
            }

            when (val state = uiState) {
                is ReaderUiState.Loading -> {
                    CenteredMessage(message = "正在打开书籍…")
                }

                is ReaderUiState.Html -> {
                    HtmlReaderContent(
                        state = state,
                        onWebSchemeUrl = vm::onWebSchemeUrl
                    )
                }

                is ReaderUiState.Text -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(
                            text = state.text,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                is ReaderUiState.BitmapPage -> {
                    Image(
                        bitmap = state.bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is ReaderUiState.Unsupported -> {
                    CenteredMessage(message = state.message)
                }

                is ReaderUiState.Error -> {
                    CenteredMessage(message = state.message)
                }
            }
        }
    }
}

@Composable
private fun HtmlReaderContent(
    state: ReaderUiState.Html,
    onWebSchemeUrl: (String) -> Boolean
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.WHITE)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString().orEmpty()
                        return onWebSchemeUrl(url)
                    }
                }
            }
        },
        update = { webView ->
            if (webView.tag != state.pageId) {
                if (!state.contentUrl.isNullOrBlank()) {
                    webView.loadUrl(state.contentUrl)
                } else {
                    webView.loadDataWithBaseURL(
                        state.baseUrl ?: "about:blank",
                        state.inlineHtml.orEmpty(),
                        "text/html",
                        "utf-8",
                        null
                    )
                }
                webView.tag = state.pageId
            }
        }
    )
}

@Composable
private fun CenteredMessage(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = message)
    }
}
