package com.ireader.feature.reader.ui

import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ireader.feature.reader.presentation.ReaderEffect
import com.ireader.feature.reader.presentation.ReaderHardwareKeyBridge
import com.ireader.feature.reader.presentation.ReaderIntent
import com.ireader.feature.reader.presentation.UiText
import com.ireader.feature.reader.presentation.ReaderViewModel
import com.ireader.feature.reader.web.ExternalLinkPolicy

@Composable
@Suppress("FunctionNaming")
fun ReaderScreen(
    bookId: Long,
    locatorArg: String?,
    onBack: () -> Unit,
    onOpenAnnotations: (Long) -> Unit,
    vm: ReaderViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    val volumeKeyHandler = rememberUpdatedState(newValue = { keyCode: Int, action: Int ->
        if (!state.displayPrefs.volumeKeyPagingEnabled) {
            false
        } else {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (action == KeyEvent.ACTION_DOWN) {
                        vm.dispatch(ReaderIntent.Prev)
                    }
                    true
                }

                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (action == KeyEvent.ACTION_DOWN) {
                        vm.dispatch(ReaderIntent.Next)
                    }
                    true
                }

                else -> false
            }
        }
    })

    LaunchedEffect(bookId, locatorArg) {
        vm.dispatch(ReaderIntent.Start(bookId = bookId, locatorArg = locatorArg))
    }

    DisposableEffect(Unit) {
        ReaderHardwareKeyBridge.setVolumeKeyListener { keyCode, action ->
            volumeKeyHandler.value(keyCode, action)
        }
        onDispose {
            ReaderHardwareKeyBridge.setVolumeKeyListener(null)
        }
    }

    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                ReaderEffect.Back -> onBack()
                is ReaderEffect.OpenAnnotations -> onOpenAnnotations(effect.bookId)
                is ReaderEffect.OpenExternalUrl -> {
                    when (ExternalLinkPolicy.evaluate(effect.url)) {
                        is ExternalLinkPolicy.Decision.Allow -> runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(effect.url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }.onFailure {
                            snackbarHost.showSnackbar("无法打开外部链接")
                        }

                        is ExternalLinkPolicy.Decision.Block -> {
                            snackbarHost.showSnackbar("已拦截不安全外部链接")
                        }
                    }
                }
                is ReaderEffect.ShareText -> {
                    runCatching {
                        val chooser = Intent.createChooser(
                            Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, effect.text),
                            null
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooser)
                    }.onFailure {
                        snackbarHost.showSnackbar("分享失败")
                    }
                }
                is ReaderEffect.Snackbar -> {
                    val message = when (val text = effect.message) {
                        is UiText.Dynamic -> text.value
                        is UiText.Res -> context.getString(text.id, *text.args.toTypedArray())
                    }
                    snackbarHost.showSnackbar(message)
                }
            }
        }
    }

    BackHandler {
        vm.dispatch(ReaderIntent.BackPressed)
    }

    ReaderScaffold(
        state = state,
        snackbarHostState = snackbarHost,
        onBack = onBack,
        onIntent = vm::dispatch,
        onOpenLocator = vm::openLocator,
        onReadingViewportChanged = { constraints ->
            vm.dispatch(ReaderIntent.LayoutChanged(constraints))
        }
    )
}
