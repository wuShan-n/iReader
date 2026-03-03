package com.ireader.feature.reader.ui.components

import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ireader.reader.api.render.RenderContent

@Composable
fun HtmlPage(
    pageId: String,
    content: RenderContent.Html,
    onWebSchemeUrl: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
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
            if (webView.tag == pageId) return@AndroidView
            if (content.contentUri != null) {
                webView.loadUrl(content.contentUri.toString())
            } else {
                webView.loadDataWithBaseURL(
                    content.baseUri?.toString() ?: "about:blank",
                    content.inlineHtml.orEmpty(),
                    "text/html",
                    "utf-8",
                    null
                )
            }
            webView.tag = pageId
        }
    )
}

