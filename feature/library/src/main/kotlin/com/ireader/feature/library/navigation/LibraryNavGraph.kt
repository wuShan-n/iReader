package com.ireader.feature.library.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ireader.core.navigation.AppRoutes
import com.ireader.feature.library.ui.LibraryScreen

fun NavGraphBuilder.libraryNavGraph(
    onOpenBook: (Long) -> Unit,
    onOpenSettings: () -> Unit
) {
    composable(AppRoutes.LIBRARY) {
        LibraryScreen(
            onOpenBook = onOpenBook,
            onOpenSettings = onOpenSettings
        )
    }
}
