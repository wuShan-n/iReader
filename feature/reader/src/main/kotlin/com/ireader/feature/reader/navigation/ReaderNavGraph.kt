package com.ireader.feature.reader.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ireader.core.navigation.AppRoutes
import com.ireader.feature.reader.ui.ReaderScreen

fun NavGraphBuilder.readerNavGraph(
    onBack: () -> Unit,
    onOpenAnnotations: (String) -> Unit
) {
    composable(
        route = AppRoutes.READER,
        arguments = listOf(
            navArgument(AppRoutes.ARG_BOOK_ID) {
                type = NavType.StringType
            },
            navArgument(AppRoutes.ARG_LOCATOR) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) { backStackEntry ->
        ReaderScreen(
            bookId = ReaderRoute.bookId(backStackEntry),
            locatorArg = ReaderRoute.locatorArg(backStackEntry),
            onBack = onBack,
            onOpenAnnotations = onOpenAnnotations
        )
    }
}
