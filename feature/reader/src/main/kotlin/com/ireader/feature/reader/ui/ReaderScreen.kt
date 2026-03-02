package com.ireader.feature.reader.ui

import android.graphics.Color
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ireader.feature.reader.presentation.ReaderUiState
import com.ireader.feature.reader.presentation.ReaderViewModel
import com.ireader.reader.api.render.TileRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionNaming")
fun ReaderScreen(
    bookId: Long,
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

                is ReaderUiState.Embedded -> {
                    ReaderSurface(
                        controller = state.controller,
                        modifier = Modifier.fillMaxSize()
                    )
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

                is ReaderUiState.TilesPage -> {
                    TilesReaderContent(
                        state = state,
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
private fun TilesReaderContent(
    state: ReaderUiState.TilesPage,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val tiles = remember(state.pageId) { mutableStateMapOf<TileSlot, Bitmap>() }

    DisposableEffect(state.pageId, state.tileProvider) {
        onDispose {
            tiles.values.forEach { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            tiles.clear()
            runCatching { state.tileProvider.close() }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val viewportWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val viewportHeightPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        val fitScale = min(
            viewportWidthPx.toFloat() / state.pageWidthPx.toFloat().coerceAtLeast(1f),
            viewportHeightPx.toFloat() / state.pageHeightPx.toFloat().coerceAtLeast(1f)
        ).coerceAtLeast(0.01f)

        val tileSizePx = 512
        val cols = ceil(state.pageWidthPx / tileSizePx.toFloat()).toInt().coerceAtLeast(1)
        val rows = ceil(state.pageHeightPx / tileSizePx.toFloat()).toInt().coerceAtLeast(1)

        LaunchedEffect(state.pageId, cols, rows, state.pageWidthPx, state.pageHeightPx, state.tileProvider) {
            tiles.values.forEach { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            tiles.clear()

            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    if (!isActive) return@LaunchedEffect
                    val left = col * tileSizePx
                    val top = row * tileSizePx
                    val width = min(tileSizePx, state.pageWidthPx - left).coerceAtLeast(1)
                    val height = min(tileSizePx, state.pageHeightPx - top).coerceAtLeast(1)
                    val key = TileSlot(left, top, width, height)
                    val bitmap = withContext(Dispatchers.IO) {
                        state.tileProvider.renderTile(
                            TileRequest(
                                leftPx = left,
                                topPx = top,
                                widthPx = width,
                                heightPx = height,
                                scale = 1f
                            )
                        )
                    }
                    tiles.put(key, bitmap)?.let { previous ->
                        if (previous != bitmap && !previous.isRecycled) {
                            previous.recycle()
                        }
                    }
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val drawWidth = state.pageWidthPx * fitScale
            val drawHeight = state.pageHeightPx * fitScale
            val leftOffset = (size.width - drawWidth) / 2f
            val topOffset = (size.height - drawHeight) / 2f

            tiles.forEach { (slot, bitmap) ->
                if (bitmap.isRecycled) return@forEach
                drawImage(
                    image = bitmap.asImageBitmap(),
                    srcOffset = IntOffset(0, 0),
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset(
                        x = (leftOffset + slot.leftPx * fitScale).roundToInt(),
                        y = (topOffset + slot.topPx * fitScale).roundToInt()
                    ),
                    dstSize = IntSize(
                        width = (slot.widthPx * fitScale).roundToInt().coerceAtLeast(1),
                        height = (slot.heightPx * fitScale).roundToInt().coerceAtLeast(1)
                    )
                )
            }
        }
    }
}

private data class TileSlot(
    val leftPx: Int,
    val topPx: Int,
    val widthPx: Int,
    val heightPx: Int
)

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
