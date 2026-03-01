package com.ireader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ireader.core.designsystem.IReaderTheme
import com.ireader.core.navigation.AppRoutes
import com.ireader.feature.annotations.ui.AnnotationsScreen
import com.ireader.feature.library.ui.LibraryScreen
import com.ireader.feature.reader.ui.ReaderScreen
import com.ireader.feature.search.ui.SearchScreen
import com.ireader.feature.settings.ui.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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
        composable(AppRoutes.LIBRARY) {
            LibraryScreen(
                onOpenReader = { navController.navigate(AppRoutes.READER) },
                onOpenSettings = { navController.navigate(AppRoutes.SETTINGS) }
            )
        }
        composable(AppRoutes.READER) {
            ReaderScreen(
                onOpenAnnotations = { navController.navigate(AppRoutes.ANNOTATIONS) },
                onOpenSearch = { navController.navigate(AppRoutes.SEARCH) }
            )
        }
        composable(AppRoutes.ANNOTATIONS) {
            AnnotationsScreen()
        }
        composable(AppRoutes.SEARCH) {
            SearchScreen()
        }
        composable(AppRoutes.SETTINGS) {
            SettingsScreen()
        }
    }
}
