package com.ireader

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.fragment.app.FragmentActivity
import com.ireader.core.designsystem.IReaderTheme
import com.ireader.core.files.importing.ImportManager
import com.ireader.core.files.importing.ImportRequest
import com.ireader.core.navigation.AppRoutes
import com.ireader.feature.annotations.ui.AnnotationsScreen
import com.ireader.feature.library.navigation.libraryNavGraph
import com.ireader.feature.reader.navigation.readerNavGraph
import com.ireader.feature.search.ui.SearchScreen
import com.ireader.feature.settings.ui.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var importManager: ImportManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IReaderTheme {
                iReaderApp(importManager = importManager)
            }
        }
    }
}

@Composable
private fun iReaderApp(importManager: ImportManager) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    var activeJobId by remember { mutableStateOf<String?>(null) }
    var importStatusText by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            runCatching {
                importManager.enqueue(ImportRequest(uris = uris))
            }.onSuccess { jobId ->
                activeJobId = jobId
            }.onFailure { throwable ->
                importStatusText = throwable.message ?: "Import enqueue failed"
            }
        }
    }

    LaunchedEffect(activeJobId) {
        val jobId = activeJobId ?: return@LaunchedEffect
        importManager.observe(jobId).collectLatest { state ->
            importStatusText = "${state.status} ${state.done}/${state.total}"
        }
    }

    NavHost(navController = navController, startDestination = AppRoutes.LIBRARY) {
        libraryNavGraph(
            onImportBooks = { launcher.launch(arrayOf("*/*")) },
            importStatusText = importStatusText,
            onOpenBook = { bookId ->
                navController.navigate(AppRoutes.reader(bookId))
            },
            onOpenSettings = { navController.navigate(AppRoutes.SETTINGS) }
        )
        readerNavGraph(
            onOpenAnnotations = { navController.navigate(AppRoutes.ANNOTATIONS) },
            onOpenSearch = { navController.navigate(AppRoutes.SEARCH) }
        )
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
