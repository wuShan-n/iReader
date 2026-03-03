package com.ireader.feature.reader.presentation

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

sealed interface UiText {
    data class Dynamic(val value: String) : UiText
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
}

@Composable
fun UiText.asString(): String =
    when (this) {
        is UiText.Dynamic -> value
        is UiText.Res -> stringResource(id, *args.toTypedArray())
    }

