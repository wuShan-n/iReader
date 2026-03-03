package com.ireader.feature.reader.ui.components

import android.graphics.Color
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderContent
import kotlinx.coroutines.runBlocking

@Composable
fun HtmlPage(
    pageId: String,
    content: RenderContent.Html,
    resourceProvider: ResourceProvider?,
    reflowConfig: RenderConfig.ReflowText?,
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
                settings.allowFileAccess = false
            }
        },
        update = { webView ->
            webView.webViewClient = ReaderHtmlWebViewClient(
                resourceProvider = resourceProvider,
                content = content,
                reflowConfig = reflowConfig,
                onWebSchemeUrl = onWebSchemeUrl
            )

            if (webView.tag == pageId) return@AndroidView
            if (content.contentUri != null) {
                val url = buildInitialPageUrl(content, resourceProvider)
                webView.loadUrl(url)
            } else {
                webView.loadDataWithBaseURL(
                    buildBaseUrl(content, resourceProvider),
                    content.inlineHtml.orEmpty(),
                    "text/html",
                    "utf-8",
                    null
                )
            }
            webView.tag = pageId
            injectTheme(webView, content, reflowConfig)
        }
    )
}

private class ReaderHtmlWebViewClient(
    private val resourceProvider: ResourceProvider?,
    private val content: RenderContent.Html,
    private val reflowConfig: RenderConfig.ReflowText?,
    private val onWebSchemeUrl: (String) -> Boolean
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        val url = request?.url?.toString().orEmpty()
        if (onWebSchemeUrl(url)) return true
        return request?.url?.scheme.equals("javascript", ignoreCase = true)
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val uri = request?.url ?: return null
        if (!uri.scheme.equals(READER_SCHEME, ignoreCase = true)) return null

        val provider = resourceProvider
            ?: return errorResponse(statusCode = 404, reason = "Resource provider missing")
        val path = uri.readerResourcePath()
            ?: return errorResponse(statusCode = 400, reason = "Missing resource path")

        val opened = runBlocking { provider.openResource(path) }
        val stream = (opened as? ReaderResult.Ok)?.value
            ?: return errorResponse(statusCode = 404, reason = "Resource not found")

        val mime = runBlocking { provider.getMimeType(path) }
            .let { it as? ReaderResult.Ok }
            ?.value
            ?: guessMimeType(path)

        return WebResourceResponse(
            mime,
            defaultCharsetForMime(mime),
            stream
        ).apply {
            setStatusCodeAndReasonPhrase(200, "OK")
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view ?: return
        injectTheme(view, content, reflowConfig)
    }
}

private fun buildInitialPageUrl(
    content: RenderContent.Html,
    resourceProvider: ResourceProvider?
): String {
    val uri = content.contentUri ?: return "about:blank"
    if (uri.scheme.equals(READER_SCHEME, ignoreCase = true) || resourceProvider == null) {
        return uri.toString()
    }
    val rawPath = content.resourceBasePath
        ?: uri.encodedPath?.removePrefix("/")
        ?: uri.toString()
    return toReaderUrl(rawPath)
}

private fun buildBaseUrl(
    content: RenderContent.Html,
    resourceProvider: ResourceProvider?
): String {
    if (resourceProvider == null) {
        return content.baseUri?.toString() ?: "about:blank"
    }
    val rawPath = content.resourceBasePath
        ?: content.baseUri?.encodedPath?.removePrefix("/")
        ?: ""
    return toReaderBaseUrl(rawPath)
}

private fun injectTheme(
    webView: WebView,
    content: RenderContent.Html,
    reflowConfig: RenderConfig.ReflowText?
) {
    val css = buildString {
        append(buildReflowCss(reflowConfig))
        content.themeInjection?.css?.takeIf { it.isNotBlank() }?.let {
            append('\n')
            append(it)
        }
    }.trim()

    if (css.isNotEmpty()) {
        val js = """
            (function() {
              var head = document.head || document.documentElement;
              if (!head) return;
              var style = document.getElementById('ireader-theme-style');
              if (!style) {
                style = document.createElement('style');
                style.id = 'ireader-theme-style';
                head.appendChild(style);
              }
              style.textContent = ${css.toJsStringLiteral()};
            })();
        """.trimIndent()
        runCatching { webView.evaluateJavascript(js, null) }
    }

    content.themeInjection?.javascript?.takeIf { it.isNotBlank() }?.let { script ->
        runCatching { webView.evaluateJavascript(script, null) }
    }
}

private fun buildReflowCss(config: RenderConfig.ReflowText?): String {
    if (config == null) return ""
    val family = config.fontFamilyName?.takeIf { it.isNotBlank() }
        ?.let { "font-family: '${it.replace("'", "\\'")}';" }
        .orEmpty()
    return """
        html, body {
            font-size: ${config.fontSizeSp}px !important;
            line-height: ${config.lineHeightMult} !important;
            padding: ${config.pagePaddingDp}px !important;
            margin: 0 !important;
            $family
        }
        p {
            margin-top: 0 !important;
            margin-bottom: ${config.paragraphSpacingDp}px !important;
        }
    """.trimIndent()
}

private fun toReaderBaseUrl(path: String): String {
    val normalized = normalizeBasePath(path)
    return if (normalized == null) {
        "$READER_SCHEME://$READER_HOST/"
    } else {
        "$READER_SCHEME://$READER_HOST/$normalized/"
    }
}

private fun toReaderUrl(path: String): String {
    val trimmed = path.trimStart('/')
    if (trimmed.isBlank()) {
        return "$READER_SCHEME://$READER_HOST/"
    }
    return "$READER_SCHEME://$READER_HOST/$trimmed"
}

private fun Uri.readerResourcePath(): String? {
    val queryPath = getQueryParameter("path")
    if (!queryPath.isNullOrBlank()) return queryPath
    val encoded = encodedPath?.removePrefix("/") ?: return null
    return Uri.decode(encoded).takeIf { it.isNotBlank() }
}

private fun guessMimeType(path: String): String {
    return when (path.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
        "html", "htm", "xhtml" -> "text/html"
        "css" -> "text/css"
        "js" -> "application/javascript"
        "json" -> "application/json"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        "otf" -> "font/otf"
        "xml" -> "application/xml"
        "ncx" -> "application/x-dtbncx+xml"
        else -> "application/octet-stream"
    }
}

private fun defaultCharsetForMime(mime: String): String? {
    val normalized = mime.lowercase()
    return if (
        normalized.startsWith("text/") ||
        normalized.contains("json") ||
        normalized.contains("xml") ||
        normalized.contains("javascript")
    ) {
        "utf-8"
    } else {
        null
    }
}

private fun errorResponse(statusCode: Int, reason: String): WebResourceResponse {
    return WebResourceResponse("text/plain", "utf-8", reason.byteInputStream()).apply {
        setStatusCodeAndReasonPhrase(statusCode, reason)
    }
}

private fun String.toJsStringLiteral(): String {
    val escaped = this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "")
    return "\"$escaped\""
}

private fun normalizeBasePath(path: String): String? {
    val trimmed = path.trim('/')
    if (trimmed.isBlank()) return null
    if (trimmed.endsWith('/')) return trimmed.trimEnd('/').takeIf { it.isNotBlank() }
    val parent = trimmed.substringBeforeLast('/', missingDelimiterValue = "")
    return parent.takeIf { it.isNotBlank() }
}

private const val READER_SCHEME = "reader"
private const val READER_HOST = "book"
