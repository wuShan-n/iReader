package com.ireader.feature.reader.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ireader.core.navigation.AppRoutes
import com.ireader.feature.reader.ui.ReaderScreen

fun NavGraphBuilder.readerNavGraph(
    onOpenAnnotations: () -> Unit,
    onOpenSearch: () -> Unit
) {
    composable(
        route = AppRoutes.READER,
        arguments = listOf(
            navArgument(AppRoutes.ARG_BOOK_ID) {
                type = NavType.StringType
            }
        )
    ) { backStackEntry ->
        val bookId = backStackEntry.arguments?.getString(AppRoutes.ARG_BOOK_ID).orEmpty()
        ReaderScreen(
            bookId = bookId,
            onOpenAnnotations = onOpenAnnotations,
            onOpenSearch = onOpenSearch
        )
    }
}
