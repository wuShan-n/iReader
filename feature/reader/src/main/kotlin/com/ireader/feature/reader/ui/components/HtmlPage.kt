package com.ireader.feature.reader.ui.components

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.BlockingResourceProvider
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.api.render.HyphenationMode
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.TextAlignMode
import com.ireader.reader.api.render.toTypographySpec
import java.util.Locale
import kotlinx.coroutines.runBlocking

@Composable
fun HtmlPage(
    pageId: String,
    content: RenderContent.Html,
    resourceProvider: ResourceProvider?,
    reflowConfig: RenderConfig.ReflowText?,
    textColor: Color,
    backgroundColor: Color,
    onWebSchemeUrl: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(backgroundColor.toArgb())
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
                textColor = textColor,
                backgroundColor = backgroundColor,
                onWebSchemeUrl = onWebSchemeUrl
            )

            webView.setBackgroundColor(backgroundColor.toArgb())
            val themeKey = buildThemeKey(content, reflowConfig, textColor, backgroundColor)
            val previous = webView.tag as? HtmlViewState
            val samePage = previous?.pageId == pageId

            if (!samePage) {
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
            }

            if (!samePage || previous.themeKey != themeKey) {
                injectTheme(
                    webView = webView,
                    content = content,
                    reflowConfig = reflowConfig,
                    textColor = textColor,
                    backgroundColor = backgroundColor
                )
            }
            webView.tag = HtmlViewState(pageId = pageId, themeKey = themeKey)
        }
    )
}

private class ReaderHtmlWebViewClient(
    private val resourceProvider: ResourceProvider?,
    private val content: RenderContent.Html,
    private val reflowConfig: RenderConfig.ReflowText?,
    private val textColor: Color,
    private val backgroundColor: Color,
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

        val opened = if (provider is BlockingResourceProvider) {
            provider.openResourceBlocking(path)
        } else {
            runBlocking { provider.openResource(path) }
        }
        val stream = (opened as? ReaderResult.Ok)?.value
            ?: return errorResponse(statusCode = 404, reason = "Resource not found")

        val mimeResult = if (provider is BlockingResourceProvider) {
            provider.getMimeTypeBlocking(path)
        } else {
            runBlocking { provider.getMimeType(path) }
        }
        val mime = mimeResult
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
        injectTheme(
            webView = view,
            content = content,
            reflowConfig = reflowConfig,
            textColor = textColor,
            backgroundColor = backgroundColor
        )
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
    reflowConfig: RenderConfig.ReflowText?,
    textColor: Color,
    backgroundColor: Color
) {
    val css = buildString {
        append(
            buildReflowCss(
                config = reflowConfig,
                textColorHex = textColor.toCssHex(),
                backgroundColorHex = backgroundColor.toCssHex()
            )
        )
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

internal fun buildReflowCss(
    config: RenderConfig.ReflowText?,
    textColorHex: String? = null,
    backgroundColorHex: String? = null
): String {
    if (config == null) return ""
    val typography = config.toTypographySpec()
    val textAlign = when (typography.textAlign) {
        TextAlignMode.START -> "start"
        TextAlignMode.JUSTIFY -> "justify"
    }
    val hyphens = if (typography.hyphenationMode == HyphenationMode.NONE) {
        "manual"
    } else {
        "auto"
    }
    val lineBreak = if (typography.cjkLineBreakStrict) "strict" else "auto"
    val hangingPunctuation = if (typography.hangingPunctuation) "first allow-end last" else "none"
    val family = typography.fontFamilyName?.takeIf { it.isNotBlank() }
        ?.let { "font-family: '${it.replace("'", "\\'")}';" }
        .orEmpty()
    val fontSizePx = formatCssNumber(typography.fontSizeSp)
    val lineHeight = formatCssNumber(typography.lineHeightMult)
    val pagePaddingPx = formatCssNumber(typography.pagePaddingDp)
    val paragraphSpacingPx = formatCssNumber(typography.paragraphSpacingDp)
    val paragraphIndentEm = formatCssNumber(typography.paragraphIndentEm)
    val colorRule = textColorHex?.let { "color: $it !important;" }.orEmpty()
    val bgRule = backgroundColorHex?.let { "background-color: $it !important;" }.orEmpty()
    return """
        :root {
            --ireader-font-size: ${fontSizePx}px;
            --ireader-line-height: $lineHeight;
            --ireader-page-padding: ${pagePaddingPx}px;
            --ireader-paragraph-spacing: ${paragraphSpacingPx}px;
            --ireader-paragraph-indent: ${paragraphIndentEm}em;
        }
        html, body {
            font-size: var(--ireader-font-size) !important;
            line-height: var(--ireader-line-height) !important;
            padding: var(--ireader-page-padding) !important;
            margin: 0 !important;
            text-align: ${textAlign} !important;
            line-break: $lineBreak !important;
            word-break: normal !important;
            overflow-wrap: break-word !important;
            hyphens: $hyphens !important;
            -webkit-hyphens: $hyphens !important;
            hanging-punctuation: $hangingPunctuation !important;
            $colorRule
            $bgRule
            $family
        }
        p {
            margin-top: 0 !important;
            margin-bottom: var(--ireader-paragraph-spacing) !important;
            text-indent: var(--ireader-paragraph-indent) !important;
        }
        h1, h2, h3, h4, h5, h6, blockquote, pre, li, figcaption, td, th {
            text-indent: 0 !important;
        }
    """.trimIndent()
}

private fun buildThemeKey(
    content: RenderContent.Html,
    reflowConfig: RenderConfig.ReflowText?,
    textColor: Color,
    backgroundColor: Color
): String {
    return buildString {
        append(
            buildReflowCss(
                config = reflowConfig,
                textColorHex = textColor.toCssHex(),
                backgroundColorHex = backgroundColor.toCssHex()
            ).hashCode()
        )
        append(':')
        append(content.themeInjection?.css.orEmpty().hashCode())
        append(':')
        append(content.themeInjection?.javascript.orEmpty().hashCode())
    }
}

private fun formatCssNumber(value: Float): String {
    return String.format(Locale.US, "%.3f", value)
        .trimEnd('0')
        .trimEnd('.')
}

private fun Color.toCssHex(): String {
    val r = (red * 255f).toInt().coerceIn(0, 255)
    val g = (green * 255f).toInt().coerceIn(0, 255)
    val b = (blue * 255f).toInt().coerceIn(0, 255)
    return String.format(Locale.US, "#%02X%02X%02X", r, g, b)
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

private data class HtmlViewState(
    val pageId: String,
    val themeKey: String
)
