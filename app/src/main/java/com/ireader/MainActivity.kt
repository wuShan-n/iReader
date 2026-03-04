package com.ireader

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.fragment.app.FragmentActivity
import com.ireader.core.designsystem.IReaderTheme
import com.ireader.core.navigation.AppRoutes
import com.ireader.feature.annotations.navigation.AnnotationsRoute
import com.ireader.feature.annotations.ui.AnnotationsScreen
import com.ireader.feature.library.navigation.libraryNavGraph
import com.ireader.feature.reader.navigation.ReaderRoute
import com.ireader.feature.reader.navigation.readerNavGraph
import com.ireader.feature.search.ui.SearchScreen
import com.ireader.feature.settings.ui.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IReaderTheme {
                iReaderApp()
            }
        }
    }
}

@Composable
private fun iReaderApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = AppRoutes.LIBRARY) {
        libraryNavGraph(
            onOpenBook = { bookId ->
                navController.navigate(AppRoutes.reader(bookId))
            },
            onOpenSettings = { navController.navigate(AppRoutes.SETTINGS) }
        )
        readerNavGraph(
            onBack = { navController.popBackStack() },
            onOpenAnnotations = { bookId ->
                navController.navigate(AnnotationsRoute.create(bookId))
            }
        )
        composable(
            route = AppRoutes.ANNOTATIONS,
            arguments = listOf(
                navArgument(AppRoutes.ARG_BOOK_ID) {
                    type = NavType.LongType
                }
            )
        ) { entry ->
            val bookId = entry.arguments?.getLong(AppRoutes.ARG_BOOK_ID)
                ?: error("Missing ${AppRoutes.ARG_BOOK_ID}")
            AnnotationsScreen(
                bookId = bookId,
                onBack = { navController.popBackStack() },
                onOpenLocator = { locatorEncoded ->
                    navController.popBackStack()
                    navController.navigate(
                        ReaderRoute.create(
                            bookId = bookId,
                            locator = locatorEncoded
                        )
                    )
                }
            )
        }
        composable(AppRoutes.SEARCH) {
            SearchScreen()
        }
        composable(AppRoutes.SETTINGS) {
            SettingsScreen()
        }
    }
}
