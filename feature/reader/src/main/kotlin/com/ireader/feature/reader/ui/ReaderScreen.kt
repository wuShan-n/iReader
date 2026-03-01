package com.ireader.feature.reader.ui

import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
@Suppress("FunctionNaming")
fun ReaderScreen(
    onOpenAnnotations: () -> Unit,
    onOpenSearch: () -> Unit,
    webUrl: String? = null,
    onWebSchemeUrl: ((String) -> Boolean)? = null
) {
    if (!webUrl.isNullOrBlank()) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                                return onWebSchemeUrl?.invoke(url) == true
                            }
                        }
                        loadUrl(webUrl)
                    }
                },
                update = { view ->
                    if (view.url != webUrl) {
                        view.loadUrl(webUrl)
                    }
                }
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Reader")
        Button(onClick = onOpenAnnotations) { Text("Annotations") }
        Button(onClick = onOpenSearch) { Text("Search") }
    }
}
