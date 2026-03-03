package com.ireader.feature.reader.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ireader.feature.reader.ui.ReaderScreen

fun NavGraphBuilder.readerNavGraph(
    onBack: () -> Unit,
    onOpenAnnotations: (Long) -> Unit
) {
    composable(
        route = ReaderRoute.pattern,
        arguments = ReaderRoute.arguments
    ) { backStackEntry ->
        ReaderScreen(
            bookId = ReaderRoute.bookId(backStackEntry),
            locatorArg = ReaderRoute.locatorArg(backStackEntry),
            onBack = onBack,
            onOpenAnnotations = onOpenAnnotations
        )
    }
}
