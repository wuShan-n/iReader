package com.ireader.feature.reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ireader.feature.reader.presentation.ReaderEffect
import com.ireader.feature.reader.presentation.ReaderIntent
import com.ireader.feature.reader.presentation.ReaderSheet
import com.ireader.feature.reader.presentation.UiText
import com.ireader.feature.reader.presentation.ReaderViewModel
import com.ireader.reader.api.render.LayoutConstraints

@Composable
@Suppress("FunctionNaming")
fun ReaderScreen(
    bookId: String,
    locatorArg: String?,
    onBack: () -> Unit,
    onOpenAnnotations: (String) -> Unit,
    vm: ReaderViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val density = LocalDensity.current
    val context = LocalContext.current

    LaunchedEffect(bookId, locatorArg) {
        vm.dispatch(ReaderIntent.Start(bookId = bookId, locatorArg = locatorArg))
    }

    LaunchedEffect(Unit) {
        vm.effects.collect { effect ->
            when (effect) {
                ReaderEffect.Back -> onBack()
                is ReaderEffect.OpenAnnotations -> onOpenAnnotations(effect.bookId)
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

    BackHandler(enabled = state.sheet != ReaderSheet.None) {
        vm.dispatch(ReaderIntent.CloseSheet)
    }
    BackHandler(enabled = state.sheet == ReaderSheet.None && !state.chromeVisible) {
        vm.dispatch(ReaderIntent.ToggleChrome)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wPx = with(density) { maxWidth.roundToPx() }
        val hPx = with(density) { maxHeight.roundToPx() }

        LaunchedEffect(wPx, hPx, density.density, density.fontScale) {
            vm.dispatch(
                ReaderIntent.LayoutChanged(
                    LayoutConstraints(
                        viewportWidthPx = wPx,
                        viewportHeightPx = hPx,
                        density = density.density,
                        fontScale = density.fontScale
                    )
                )
            )
        }

        ReaderScaffold(
            state = state,
            snackbarHostState = snackbarHost,
            onBack = onBack,
            onIntent = vm::dispatch,
            onOpenLocator = vm::openLocator,
            onWebSchemeUrl = vm::onWebSchemeUrl
        )
    }
}
