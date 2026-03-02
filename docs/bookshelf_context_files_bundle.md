# Bookshelf Context Files Bundle

Generated at: 2026-03-02 17:53:00 +08:00
Total requested files: 63

## settings.gradle.kts

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    includeBuild("build-logic")
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ireader"
include(":app")
include(":core:common")
include(":core:common-android")
include(":core:model")
include(":core:files")
include(":core:data")
include(":core:database")
include(":core:datastore")
include(":core:designsystem")
include(":core:navigation")
include(":core:testing")
include(":core:work")
include(":core:reader:api")
include(":core:reader:runtime")
include(":engines:engine-common")
include(":engines:txt")
include(":engines:epub")
include(":engines:pdf")
include(":feature:library")
include(":feature:reader")
include(":feature:annotations")
include(":feature:search")
include(":feature:settings")
 
```

## app/build.gradle.kts

```kotlin
plugins {
    id("com.ireader.android.application")
    id("com.ireader.android.compose")
    id("com.ireader.android.hilt")
}

android {
    namespace = "com.ireader"

    defaultConfig {
        applicationId = "com.ireader"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(project(":core:navigation"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:files"))
    implementation(project(":core:work"))
    implementation(project(":core:reader:runtime"))
    implementation(project(":core:designsystem"))

    implementation(project(":feature:library"))
    implementation(project(":feature:reader"))
    implementation(project(":feature:annotations"))
    implementation(project(":feature:search"))
    implementation(project(":feature:settings"))

    implementation(project(":engines:txt"))
    implementation(project(":engines:epub"))
    implementation(project(":engines:pdf"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

## app/src/main/AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <application
        android:name=".IReaderApplication"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Ireader">
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.Ireader">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

## app/src/main/java/com/ireader/IReaderApplication.kt

```kotlin
package com.ireader

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class IReaderApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
```

## app/src/main/java/com/ireader/MainActivity.kt

```kotlin
package com.ireader

import android.os.Bundle
import androidx.activity.ComponentActivity
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
class MainActivity : ComponentActivity() {
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
```

## core/navigation/src/main/kotlin/com/ireader/core/navigation/AppRoutes.kt

```kotlin
package com.ireader.core.navigation

object AppRoutes {
    const val LIBRARY = "library"
    const val ARG_BOOK_ID = "bookId"
    const val READER = "reader/{$ARG_BOOK_ID}"
    fun reader(bookId: String): String = "reader/$bookId"
    const val ANNOTATIONS = "annotations"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
}
```

## feature/library/build.gradle.kts

```kotlin
plugins {
    id("com.ireader.android.feature")
    id("com.ireader.android.compose")
    id("com.ireader.android.hilt")
}

android {
    namespace = "com.ireader.feature.library"
}

dependencies {
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:files"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.core)
}
```

## feature/library/src/main/kotlin/com/ireader/feature/library/navigation/LibraryNavGraph.kt

```kotlin
package com.ireader.feature.library.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ireader.core.navigation.AppRoutes
import com.ireader.feature.library.ui.LibraryScreen

fun NavGraphBuilder.libraryNavGraph(
    onImportBooks: () -> Unit,
    importStatusText: String?,
    onOpenBook: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    composable(AppRoutes.LIBRARY) {
        LibraryScreen(
            onImportBooks = onImportBooks,
            importStatusText = importStatusText,
            onOpenBook = onOpenBook,
            onOpenSettings = onOpenSettings
        )
    }
}
```

## feature/library/src/main/kotlin/com/ireader/feature/library/navigation/LibraryRoute.kt

```kotlin
package com.ireader.feature.library.navigation

import com.ireader.core.navigation.AppRoutes

object LibraryRoute {
    const val route: String = AppRoutes.LIBRARY
}
```

## feature/library/src/main/kotlin/com/ireader/feature/library/ui/LibraryScreen.kt

```kotlin
package com.ireader.feature.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ireader.core.data.book.LibrarySort
import com.ireader.feature.library.presentation.LibraryViewModel
import com.ireader.feature.library.ui.components.BookGridItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionNaming")
fun LibraryScreen(
    onImportBooks: () -> Unit,
    importStatusText: String?,
    onOpenBook: (String) -> Unit,
    onOpenSettings: () -> Unit,
    vm: LibraryViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("书架") },
                actions = {
                    TextButton(onClick = { sortMenuExpanded = true }) {
                        Text("排序")
                    }
                    SortMenu(
                        expanded = sortMenuExpanded,
                        current = state.sort,
                        onDismiss = { sortMenuExpanded = false },
                        onSelect = { sort ->
                            vm.setSort(sort)
                            sortMenuExpanded = false
                        }
                    )
                    TextButton(onClick = onImportBooks) {
                        Text("导入")
                    }
                    TextButton(onClick = onOpenSettings) {
                        Text("设置")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!importStatusText.isNullOrBlank()) {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    text = importStatusText,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (state.books.isEmpty()) {
                EmptyLibrary(
                    onImportBooks = onImportBooks,
                    modifier = Modifier.fillMaxSize()
                )
                return@Column
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 132.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = state.books,
                    key = { book -> book.id }
                ) { book ->
                    BookGridItem(
                        book = book,
                        onClick = { onOpenBook(book.id) },
                        onDelete = { vm.deleteBook(book.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SortMenu(
    expanded: Boolean,
    current: LibrarySort,
    onDismiss: () -> Unit,
    onSelect: (LibrarySort) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        SortItem(label = "最近更新", sort = LibrarySort.RECENTLY_UPDATED, current = current, onSelect = onSelect)
        SortItem(label = "最近导入", sort = LibrarySort.RECENTLY_ADDED, current = current, onSelect = onSelect)
        SortItem(label = "标题 A-Z", sort = LibrarySort.TITLE_AZ, current = current, onSelect = onSelect)
        SortItem(label = "作者 A-Z", sort = LibrarySort.AUTHOR_AZ, current = current, onSelect = onSelect)
    }
}

@Composable
private fun SortItem(
    label: String,
    sort: LibrarySort,
    current: LibrarySort,
    onSelect: (LibrarySort) -> Unit
) {
    DropdownMenuItem(
        text = { Text(if (sort == current) "✓ $label" else label) },
        onClick = { onSelect(sort) }
    )
}

@Composable
private fun EmptyLibrary(
    onImportBooks: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "书架为空")
        Button(onClick = onImportBooks) {
            Text("导入书籍")
        }
    }
}
```

## feature/library/src/main/kotlin/com/ireader/feature/library/presentation/LibraryViewModel.kt

```kotlin
package com.ireader.feature.library.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ireader.core.data.book.LibrarySort
import com.ireader.feature.library.domain.usecase.DeleteBookUseCase
import com.ireader.feature.library.domain.usecase.LoadLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val loadLibrary: LoadLibraryUseCase,
    private val deleteBook: DeleteBookUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sortFlow = MutableStateFlow(
        savedStateHandle.get<String>(KEY_SORT)
            ?.let { runCatching { LibrarySort.valueOf(it) }.getOrNull() }
            ?: LibrarySort.RECENTLY_UPDATED
    )

    val uiState: StateFlow<LibraryUiState> = sortFlow
        .flatMapLatest { sort ->
            loadLibrary(sort).map { books ->
                LibraryUiState(books = books, sort = sort)
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            LibraryUiState()
        )

    fun setSort(sort: LibrarySort) {
        sortFlow.value = sort
        savedStateHandle[KEY_SORT] = sort.name
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            deleteBook(bookId)
        }
    }

    private companion object {
        private const val KEY_SORT = "library_sort"
    }
}
```

## feature/library/src/main/kotlin/com/ireader/feature/library/presentation/LibraryUiState.kt

```kotlin
package com.ireader.feature.library.presentation

import com.ireader.core.data.book.LibrarySort
import com.ireader.core.database.book.BookEntity

data class LibraryUiState(
    val books: List<BookEntity> = emptyList(),
    val sort: LibrarySort = LibrarySort.RECENTLY_UPDATED
)
```

## feature/library/src/main/kotlin/com/ireader/feature/library/domain/usecase/LoadLibraryUseCase.kt

```kotlin
package com.ireader.feature.library.domain.usecase

import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.book.LibrarySort
import com.ireader.core.database.book.BookEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class LoadLibraryUseCase @Inject constructor(
    private val bookRepo: BookRepo
) {
    operator fun invoke(sort: LibrarySort): Flow<List<BookEntity>> = bookRepo.observeLibrary(sort)
}
```

## feature/library/src/main/kotlin/com/ireader/feature/library/domain/usecase/DeleteBookUseCase.kt

```kotlin
package com.ireader.feature.library.domain.usecase

import com.ireader.core.data.book.BookRepo
import com.ireader.core.files.storage.BookStorage
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeleteBookUseCase @Inject constructor(
    private val bookRepo: BookRepo,
    private val storage: BookStorage
) {
    suspend operator fun invoke(bookId: String) = withContext(Dispatchers.IO) {
        bookRepo.deleteById(bookId)
        runCatching { storage.deleteBookFiles(bookId) }
    }
}
```

## feature/library/src/main/kotlin/com/ireader/feature/library/domain/usecase/StartImportUseCase.kt

```kotlin
package com.ireader.feature.library.domain.usecase

import android.net.Uri
import com.ireader.core.files.importing.DuplicateStrategy
import com.ireader.core.files.importing.ImportManager
import com.ireader.core.files.importing.ImportRequest

class StartImportUseCase(
    private val importManager: ImportManager
) {
    suspend operator fun invoke(
        uris: List<Uri>,
        strategy: DuplicateStrategy = DuplicateStrategy.SKIP
    ): String {
        return importManager.enqueue(
            ImportRequest(
                uris = uris,
                duplicateStrategy = strategy
            )
        )
    }
}
```

## feature/library/src/main/kotlin/com/ireader/feature/library/domain/usecase/ObserveImportJobUseCase.kt

```kotlin
package com.ireader.feature.library.domain.usecase

import com.ireader.core.files.importing.ImportJobState
import com.ireader.core.files.importing.ImportManager
import kotlinx.coroutines.flow.Flow

class ObserveImportJobUseCase(
    private val importManager: ImportManager
) {
    operator fun invoke(jobId: String): Flow<ImportJobState> = importManager.observe(jobId)
}
```

## feature/library/src/main/kotlin/com/ireader/feature/library/ui/components/BookGridItem.kt

```kotlin
package com.ireader.feature.library.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ireader.core.database.book.BookEntity
import java.io.File
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookGridItem(
    book: BookEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val title = bookTitle(book)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { confirmDelete = true }
            )
    ) {
        BookCover(
            coverPath = book.coverPath,
            titleFallback = title
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium
        )

        val author = book.author.orEmpty().trim()
        if (author.isNotBlank()) {
            Text(
                text = author,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Spacer(modifier = Modifier.height(2.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = book.format.name,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = sizeText(book.sizeBytes),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除书籍？") },
            text = { Text("将从书架移除，并删除本地已导入文件。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    }
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }
}

private fun bookTitle(book: BookEntity): String {
    val title = book.title?.trim().orEmpty()
    if (title.isNotBlank()) return title

    val displayName = book.displayName?.trim().orEmpty()
    if (displayName.isNotBlank()) return displayName.substringBeforeLast('.', displayName)

    return File(book.canonicalPath).nameWithoutExtension.ifBlank { "Untitled" }
}

private fun sizeText(bytes: Long): String {
    if (bytes <= 0L) return "0B"

    val kb = bytes / 1024.0
    if (kb < 1024.0) {
        return String.format(Locale.US, "%.0fKB", kb)
    }

    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1fMB", mb)
}
```

## feature/library/src/main/kotlin/com/ireader/feature/library/ui/components/BookCover.kt

```kotlin
package com.ireader.feature.library.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun BookCover(
    coverPath: String?,
    titleFallback: String,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(3f / 4f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val density = LocalDensity.current
        val reqWidth = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val reqHeight = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        val bitmap = rememberCoverBitmap(path = coverPath, reqWidth = reqWidth, reqHeight = reqHeight)

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = titleFallback.take(1).ifBlank { "?" },
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }
    }
}

@Composable
private fun rememberCoverBitmap(path: String?, reqWidth: Int, reqHeight: Int): Bitmap? {
    val key = "${path.orEmpty()}@$reqWidth@$reqHeight"
    val state = produceState<Bitmap?>(initialValue = null, key1 = key) {
        value = withContext(Dispatchers.IO) {
            val trimmedPath = path?.trim().orEmpty()
            if (trimmedPath.isBlank()) return@withContext null

            CoverBitmapCache.get(key)?.let { return@withContext it }

            val file = File(trimmedPath)
            val bitmap = runCatching {
                BitmapDecodeExt.decodeSampled(file = file, reqWidth = reqWidth, reqHeight = reqHeight)
            }.getOrNull()
            if (bitmap != null) {
                CoverBitmapCache.put(key, bitmap)
            }
            bitmap
        }
    }
    return state.value
}
```

## feature/library/src/main/kotlin/com/ireader/feature/library/ui/components/CoverBitmapCache.kt

```kotlin
package com.ireader.feature.library.ui.components

import android.graphics.Bitmap
import android.util.LruCache

internal object CoverBitmapCache {
    private val cache = object : LruCache<String, Bitmap>(48) {}

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}
```

## feature/library/src/main/kotlin/com/ireader/feature/library/ui/components/BitmapDecodeExt.kt

```kotlin
package com.ireader.feature.library.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.max

internal object BitmapDecodeExt {

    fun decodeSampled(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (!file.exists()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sample = calcInSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            reqWidth = reqWidth.coerceAtLeast(1),
            reqHeight = reqHeight.coerceAtLeast(1)
        )

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun calcInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return max(1, inSampleSize)
    }
}
```

## core/data/src/main/kotlin/com/ireader/core/data/book/BookRepo.kt

```kotlin
package com.ireader.core.data.book

import com.ireader.core.database.book.BookDao
import com.ireader.core.database.book.BookEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class BookRepo @Inject constructor(
    private val dao: BookDao
) {
    suspend fun upsert(entity: BookEntity) = dao.upsert(entity)

    suspend fun findByFingerprint(fingerprint: String) = dao.findByFingerprint(fingerprint)

    suspend fun getById(bookId: String) = dao.getById(bookId)

    suspend fun deleteById(bookId: String) = dao.deleteById(bookId)

    fun observeById(bookId: String): Flow<BookEntity?> = dao.observeById(bookId)

    fun observeLibrary(sort: LibrarySort): Flow<List<BookEntity>> {
        return when (sort) {
            LibrarySort.RECENTLY_UPDATED -> dao.observeByUpdatedDesc()
            LibrarySort.RECENTLY_ADDED -> dao.observeByCreatedDesc()
            LibrarySort.TITLE_AZ -> dao.observeByTitleAsc()
            LibrarySort.AUTHOR_AZ -> dao.observeByAuthorAsc()
        }
    }
}
```

## core/data/src/main/kotlin/com/ireader/core/data/book/LibrarySort.kt

```kotlin
package com.ireader.core.data.book

enum class LibrarySort {
    RECENTLY_UPDATED,
    RECENTLY_ADDED,
    TITLE_AZ,
    AUTHOR_AZ
}
```

## core/database/src/main/kotlin/com/ireader/core/database/book/BookDao.kt

```kotlin
package com.ireader.core.database.book

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BookEntity)

    @Query("SELECT * FROM books WHERE fingerprintSha256 = :fingerprint LIMIT 1")
    suspend fun findByFingerprint(fingerprint: String): BookEntity?

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getById(bookId: String): BookEntity?

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteById(bookId: String)

    @Query("SELECT * FROM books ORDER BY updatedAtEpochMs DESC")
    fun observeByUpdatedDesc(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY createdAtEpochMs DESC")
    fun observeByCreatedDesc(): Flow<List<BookEntity>>

    @Query(
        """
        SELECT * FROM books
        ORDER BY COALESCE(title, displayName, '') COLLATE NOCASE ASC
        """
    )
    fun observeByTitleAsc(): Flow<List<BookEntity>>

    @Query(
        """
        SELECT * FROM books
        ORDER BY COALESCE(author, '') COLLATE NOCASE ASC,
                 COALESCE(title, displayName, '') COLLATE NOCASE ASC
        """
    )
    fun observeByAuthorAsc(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    fun observeById(bookId: String): Flow<BookEntity?>
}
```

## core/database/src/main/kotlin/com/ireader/core/database/book/BookEntity.kt

```kotlin
package com.ireader.core.database.book

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ireader.reader.model.BookFormat

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val format: BookFormat,
    val title: String?,
    val author: String?,
    val language: String?,
    val identifier: String?,
    val canonicalPath: String,
    val originalUri: String?,
    val displayName: String?,
    val mimeType: String?,
    val fingerprintSha256: String,
    val sizeBytes: Long,
    val coverPath: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
```

## core/database/src/main/kotlin/com/ireader/core/database/ReaderDatabase.kt

```kotlin
package com.ireader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ireader.core.database.book.BookDao
import com.ireader.core.database.book.BookEntity
import com.ireader.core.database.importing.ImportItemDao
import com.ireader.core.database.importing.ImportItemEntity
import com.ireader.core.database.importing.ImportJobDao
import com.ireader.core.database.importing.ImportJobEntity

@Database(
    entities = [
        BookEntity::class,
        ImportJobEntity::class,
        ImportItemEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(DbConverters::class)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun importJobDao(): ImportJobDao
    abstract fun importItemDao(): ImportItemDao
}
```

## core/database/src/main/kotlin/com/ireader/core/database/DatabaseModule.kt

```kotlin
package com.ireader.core.database

import android.content.Context
import androidx.room.Room
import com.ireader.core.database.book.BookDao
import com.ireader.core.database.importing.ImportItemDao
import com.ireader.core.database.importing.ImportJobDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideReaderDatabase(@ApplicationContext context: Context): ReaderDatabase {
        return Room.databaseBuilder(
            context,
            ReaderDatabase::class.java,
            "ireader.db"
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }

    @Provides
    fun provideBookDao(database: ReaderDatabase): BookDao = database.bookDao()

    @Provides
    fun provideImportJobDao(database: ReaderDatabase): ImportJobDao = database.importJobDao()

    @Provides
    fun provideImportItemDao(database: ReaderDatabase): ImportItemDao = database.importItemDao()
}
```

## core/database/src/main/kotlin/com/ireader/core/database/DbConverters.kt

```kotlin
package com.ireader.core.database

import androidx.room.TypeConverter
import com.ireader.core.database.importing.ImportItemStatus
import com.ireader.core.database.importing.ImportStatus
import com.ireader.reader.model.BookFormat

class DbConverters {
    @TypeConverter
    fun bookFormatToString(value: BookFormat): String = value.name

    @TypeConverter
    fun stringToBookFormat(value: String): BookFormat = BookFormat.valueOf(value)

    @TypeConverter
    fun importStatusToString(value: ImportStatus): String = value.name

    @TypeConverter
    fun stringToImportStatus(value: String): ImportStatus = ImportStatus.valueOf(value)

    @TypeConverter
    fun importItemStatusToString(value: ImportItemStatus): String = value.name

    @TypeConverter
    fun stringToImportItemStatus(value: String): ImportItemStatus = ImportItemStatus.valueOf(value)
}
```

## core/files/src/main/kotlin/com/ireader/core/files/importing/ImportManager.kt

```kotlin
package com.ireader.core.files.importing

import kotlinx.coroutines.flow.Flow

interface ImportManager {
    suspend fun enqueue(request: ImportRequest): String
    fun observe(jobId: String): Flow<ImportJobState>
    suspend fun cancel(jobId: String)
}
```

## core/files/src/main/kotlin/com/ireader/core/files/importing/DefaultImportManager.kt

```kotlin
package com.ireader.core.files.importing

import android.content.Context
import com.ireader.core.data.importing.ImportItemRepo
import com.ireader.core.data.importing.ImportJobRepo
import com.ireader.core.database.importing.ImportItemEntity
import com.ireader.core.database.importing.ImportItemStatus
import com.ireader.core.database.importing.ImportJobEntity
import com.ireader.core.database.importing.ImportStatus
import com.ireader.core.files.permission.UriPermissionStore
import com.ireader.core.files.source.ContentUriDocumentSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DefaultImportManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val jobRepo: ImportJobRepo,
    private val itemRepo: ImportItemRepo,
    private val permissionStore: UriPermissionStore,
    private val workScheduler: ImportWorkScheduler
) : ImportManager {

    override suspend fun enqueue(request: ImportRequest): String = withContext(Dispatchers.IO) {
        require(request.uris.isNotEmpty() || request.treeUri != null) {
            "ImportRequest must contain uris or treeUri"
        }

        val now = System.currentTimeMillis()
        val jobId = UUID.randomUUID().toString()

        request.treeUri?.let { permissionStore.takePersistableRead(it) }
        request.uris.forEach { permissionStore.takePersistableRead(it) }

        val items = request.uris.map { uri ->
            val source = ContentUriDocumentSource(appContext, uri)
            ImportItemEntity(
                jobId = jobId,
                uri = uri.toString(),
                displayName = source.displayName,
                mimeType = source.mimeType,
                sizeBytes = source.sizeBytes,
                status = ImportItemStatus.PENDING,
                bookId = null,
                fingerprintSha256 = null,
                errorCode = null,
                errorMessage = null,
                updatedAtEpochMs = now
            )
        }

        val job = ImportJobEntity(
            jobId = jobId,
            status = ImportStatus.QUEUED,
            total = items.size,
            done = 0,
            currentTitle = null,
            errorMessage = null,
            sourceTreeUri = request.treeUri?.toString(),
            duplicateStrategy = request.duplicateStrategy.name,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )

        jobRepo.upsert(job)
        if (items.isNotEmpty()) {
            itemRepo.upsertAll(items)
        }
        workScheduler.enqueue(jobId)

        return@withContext jobId
    }

    override fun observe(jobId: String): Flow<ImportJobState> {
        return jobRepo.observe(jobId)
            .filterNotNull()
            .map { entity ->
                ImportJobState(
                    jobId = entity.jobId,
                    status = entity.status,
                    total = entity.total,
                    done = entity.done,
                    currentTitle = entity.currentTitle,
                    errorMessage = entity.errorMessage
                )
            }
    }

    override suspend fun cancel(jobId: String) {
        workScheduler.cancel(jobId)
        val now = System.currentTimeMillis()
        val current = jobRepo.get(jobId) ?: return
        jobRepo.upsert(
            current.copy(
                status = ImportStatus.CANCELLED,
                updatedAtEpochMs = now
            )
        )
    }
}
```

## core/files/src/main/kotlin/com/ireader/core/files/importing/ImportRequest.kt

```kotlin
package com.ireader.core.files.importing

import android.net.Uri

data class ImportRequest(
    val uris: List<Uri> = emptyList(),
    val treeUri: Uri? = null,
    val duplicateStrategy: DuplicateStrategy = DuplicateStrategy.SKIP
)
```

## core/files/src/main/kotlin/com/ireader/core/files/importing/ImportJobState.kt

```kotlin
package com.ireader.core.files.importing

import com.ireader.core.database.importing.ImportStatus

data class ImportJobState(
    val jobId: String,
    val status: ImportStatus,
    val total: Int,
    val done: Int,
    val currentTitle: String?,
    val errorMessage: String?
)
```

## core/files/src/main/kotlin/com/ireader/core/files/importing/DuplicateStrategy.kt

```kotlin
package com.ireader.core.files.importing

enum class DuplicateStrategy {
    SKIP,
    KEEP_BOTH,
    REPLACE
}
```

## core/files/src/main/kotlin/com/ireader/core/files/importing/ImportWorkScheduler.kt

```kotlin
package com.ireader.core.files.importing

interface ImportWorkScheduler {
    fun enqueue(jobId: String)
    suspend fun cancel(jobId: String)
}
```

## core/files/src/main/kotlin/com/ireader/core/files/storage/BookStorage.kt

```kotlin
package com.ireader.core.files.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun bookDir(bookId: String): File {
        return File(context.filesDir, "books/$bookId").apply { mkdirs() }
    }

    fun canonicalFile(bookId: String, ext: String): File {
        return File(bookDir(bookId), "original.$ext")
    }

    fun coverFile(bookId: String): File {
        return File(bookDir(bookId), "cover.png")
    }

    fun importTempFile(): File {
        val dir = File(context.filesDir, "import_tmp").apply { mkdirs() }
        return File(dir, ".tmp-${UUID.randomUUID()}")
    }

    fun atomicMove(from: File, to: File) {
        try {
            Files.move(
                from.toPath(),
                to.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Throwable) {
            if (to.exists()) {
                to.delete()
            }
            from.renameTo(to)
        }
    }

    fun deleteBookFiles(bookId: String) {
        bookDir(bookId).deleteRecursively()
    }

    fun deleteCanonical(bookId: String) {
        val dir = bookDir(bookId)
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("original.") }
            ?.forEach { file -> runCatching { file.delete() } }
    }
}
```

## core/files/src/main/kotlin/com/ireader/core/files/permission/UriPermissionStore.kt

```kotlin
package com.ireader.core.files.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UriPermissionStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun takePersistableRead(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
    }

    fun hasPersistedRead(uri: Uri): Boolean {
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }
}
```

## core/files/src/main/kotlin/com/ireader/core/files/di/ImportModule.kt

```kotlin
package com.ireader.core.files.di

import com.ireader.core.files.importing.DefaultImportManager
import com.ireader.core.files.importing.ImportManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ImportModule {
    @Binds
    @Singleton
    abstract fun bindImportManager(impl: DefaultImportManager): ImportManager
}
```

## core/work/src/main/kotlin/com/ireader/core/work/WorkImportScheduler.kt

```kotlin
package com.ireader.core.work

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ireader.core.files.importing.ImportWorkScheduler
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkImportScheduler @Inject constructor(
    private val workManager: WorkManager
) : ImportWorkScheduler {
    override fun enqueue(jobId: String) {
        val work = OneTimeWorkRequestBuilder<ImportWorker>()
            .setInputData(ImportWorker.input(jobId))
            .addTag(WorkNames.tagForJob(jobId))
            .build()

        workManager.enqueueUniqueWork(
            WorkNames.uniqueForJob(jobId),
            ExistingWorkPolicy.KEEP,
            work
        )
    }

    override suspend fun cancel(jobId: String) {
        workManager.cancelUniqueWork(WorkNames.uniqueForJob(jobId))
    }
}
```

## core/work/src/main/kotlin/com/ireader/core/work/WorkNames.kt

```kotlin
package com.ireader.core.work

object WorkNames {
    fun uniqueForJob(jobId: String): String = "import:$jobId"
    fun tagForJob(jobId: String): String = "import_tag:$jobId"
    fun uniqueEnrichForJob(jobId: String): String = "enrich:$jobId"
    fun tagEnrichForJob(jobId: String): String = "enrich_tag:$jobId"
}
```

## core/work/src/main/kotlin/com/ireader/core/work/ImportWorker.kt

```kotlin
package com.ireader.core.work

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.importing.ImportItemRepo
import com.ireader.core.data.importing.ImportJobRepo
import com.ireader.core.database.book.BookEntity
import com.ireader.core.database.importing.ImportItemEntity
import com.ireader.core.database.importing.ImportItemStatus
import com.ireader.core.database.importing.ImportStatus
import com.ireader.core.files.hash.Fingerprint
import com.ireader.core.files.importing.DuplicateStrategy
import com.ireader.core.files.scan.TreeScanner
import com.ireader.core.files.source.ContentUriDocumentSource
import com.ireader.core.files.source.FileDocumentSource
import com.ireader.core.files.storage.BookStorage
import com.ireader.core.work.notification.ImportForeground
import com.ireader.core.work.enrich.EnrichWorker
import com.ireader.core.work.enrich.EnrichWorkerInput
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.BookFormat
import com.ireader.reader.runtime.format.BookFormatDetector
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@HiltWorker
class ImportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val jobRepo: ImportJobRepo,
    private val itemRepo: ImportItemRepo,
    private val bookRepo: BookRepo,
    private val storage: BookStorage,
    private val treeScanner: TreeScanner,
    private val formatDetector: BookFormatDetector
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return@withContext Result.failure()
        val currentJob = jobRepo.get(jobId) ?: return@withContext Result.failure()

        if (currentJob.sourceTreeUri != null) {
            val existingItems = itemRepo.list(jobId)
            if (existingItems.isEmpty()) {
                val uris = treeScanner.scan(Uri.parse(currentJob.sourceTreeUri))
                val now = System.currentTimeMillis()
                val scannedItems = uris.map { uri ->
                    val source = ContentUriDocumentSource(applicationContext, uri)
                    ImportItemEntity(
                        jobId = jobId,
                        uri = uri.toString(),
                        displayName = source.displayName,
                        mimeType = source.mimeType,
                        sizeBytes = source.sizeBytes,
                        status = ImportItemStatus.PENDING,
                        bookId = null,
                        fingerprintSha256 = null,
                        errorCode = null,
                        errorMessage = null,
                        updatedAtEpochMs = now
                    )
                }
                itemRepo.upsertAll(scannedItems)
                jobRepo.updateProgress(
                    jobId = jobId,
                    status = ImportStatus.QUEUED,
                    total = scannedItems.size,
                    done = 0,
                    currentTitle = null,
                    errorMessage = null,
                    now = now
                )
            }
        }

        val duplicateStrategy = runCatching {
            DuplicateStrategy.valueOf(currentJob.duplicateStrategy)
        }.getOrDefault(DuplicateStrategy.SKIP)

        val initial = jobRepo.get(jobId) ?: currentJob
        var done = initial.done
        var total = initial.total

        jobRepo.updateProgress(
            jobId = jobId,
            status = ImportStatus.RUNNING,
            total = total,
            done = done,
            currentTitle = null,
            errorMessage = null,
            now = System.currentTimeMillis()
        )

        val pendingItems = itemRepo.listPendingOrFailed(jobId)
        if (total == 0) {
            total = pendingItems.size
        }

        return@withContext try {
            setForegroundSafe(done, total, null)

            for (item in pendingItems) {
                currentCoroutineContext().ensureActive()

                val title = item.displayName ?: item.uri
                val now = System.currentTimeMillis()

                itemRepo.update(
                    jobId = jobId,
                    uri = item.uri,
                    status = ImportItemStatus.RUNNING,
                    bookId = null,
                    fingerprint = null,
                    errorCode = null,
                    errorMessage = null,
                    now = now
                )
                jobRepo.updateProgress(
                    jobId = jobId,
                    status = ImportStatus.RUNNING,
                    total = total,
                    done = done,
                    currentTitle = title,
                    errorMessage = null,
                    now = now
                )
                setForegroundSafe(done, total, title)

                val source = ContentUriDocumentSource(applicationContext, Uri.parse(item.uri))
                val outcome = runCatching { importOne(source, duplicateStrategy) }
                    .getOrElse { throwable ->
                        val (code, message) = throwable.toImportError()
                        ImportOneResult.Fail(code, message)
                    }

                when (outcome) {
                    is ImportOneResult.Ok -> {
                        done += 1
                        itemRepo.update(
                            jobId = jobId,
                            uri = item.uri,
                            status = ImportItemStatus.SUCCEEDED,
                            bookId = outcome.bookId,
                            fingerprint = outcome.fingerprint,
                            errorCode = null,
                            errorMessage = null,
                            now = System.currentTimeMillis()
                        )
                    }

                    is ImportOneResult.Skipped -> {
                        done += 1
                        itemRepo.update(
                            jobId = jobId,
                            uri = item.uri,
                            status = ImportItemStatus.SKIPPED,
                            bookId = outcome.bookId,
                            fingerprint = outcome.fingerprint,
                            errorCode = null,
                            errorMessage = "duplicate",
                            now = System.currentTimeMillis()
                        )
                    }

                    is ImportOneResult.Fail -> {
                        done += 1
                        itemRepo.update(
                            jobId = jobId,
                            uri = item.uri,
                            status = ImportItemStatus.FAILED,
                            bookId = null,
                            fingerprint = null,
                            errorCode = outcome.code,
                            errorMessage = outcome.message,
                            now = System.currentTimeMillis()
                        )
                    }
                }

                jobRepo.updateProgress(
                    jobId = jobId,
                    status = ImportStatus.RUNNING,
                    total = total,
                    done = done,
                    currentTitle = null,
                    errorMessage = null,
                    now = System.currentTimeMillis()
                )
                setForegroundSafe(done, total, null)
            }

            jobRepo.updateProgress(
                jobId = jobId,
                status = ImportStatus.SUCCEEDED,
                total = total,
                done = done,
                currentTitle = null,
                errorMessage = null,
                now = System.currentTimeMillis()
            )

            val enrichConstraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()
            val enrichWork = OneTimeWorkRequestBuilder<EnrichWorker>()
                .setInputData(EnrichWorkerInput.data(jobId))
                .addTag(WorkNames.tagEnrichForJob(jobId))
//                .setConstraints(enrichConstraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.MINUTES
                )
                .build()
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                WorkNames.uniqueEnrichForJob(jobId),
                ExistingWorkPolicy.KEEP,
                enrichWork
            )

            Result.success()
        } catch (cancelled: CancellationException) {
            jobRepo.updateProgress(
                jobId = jobId,
                status = ImportStatus.CANCELLED,
                total = total,
                done = done,
                currentTitle = null,
                errorMessage = "Cancelled",
                now = System.currentTimeMillis()
            )
            throw cancelled
        } catch (throwable: Throwable) {
            val (_, message) = throwable.toImportError()
            jobRepo.updateProgress(
                jobId = jobId,
                status = ImportStatus.FAILED,
                total = total,
                done = done,
                currentTitle = null,
                errorMessage = message,
                now = System.currentTimeMillis()
            )
            Result.failure()
        }
    }

    private sealed interface ImportOneResult {
        data class Ok(val bookId: String, val fingerprint: String) : ImportOneResult
        data class Skipped(val bookId: String?, val fingerprint: String) : ImportOneResult
        data class Fail(val code: String, val message: String) : ImportOneResult
    }

    private suspend fun importOne(
        source: ContentUriDocumentSource,
        duplicateStrategy: DuplicateStrategy
    ): ImportOneResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val displayName = source.displayName ?: "unknown"
        val extension = guessExtension(displayName, source.mimeType)
        val tempFile = storage.importTempFile()
        val digest = Fingerprint.newSha256()

        try {
            val copiedBytes = copyWithDigest(source, tempFile, digest)
            val fingerprint = Fingerprint.sha256Hex(digest.digest())
            val existing = bookRepo.findByFingerprint(fingerprint)

            val targetBookId = when {
                existing == null -> UUID.randomUUID().toString()
                duplicateStrategy == DuplicateStrategy.SKIP -> {
                    runCatching { tempFile.delete() }
                    return@withContext ImportOneResult.Skipped(existing.id, fingerprint)
                }

                duplicateStrategy == DuplicateStrategy.REPLACE -> existing.id
                else -> UUID.randomUUID().toString()
            }

            if (existing != null && duplicateStrategy == DuplicateStrategy.REPLACE) {
                storage.deleteCanonical(targetBookId)
            }

            val finalFile = storage.canonicalFile(targetBookId, extension)
            storage.atomicMove(tempFile, finalFile)

            val detectedFormat = detectFormatFromFile(finalFile)
            val defaultTitle = displayName.substringBeforeLast('.', displayName)
            val title = if (existing != null && duplicateStrategy == DuplicateStrategy.REPLACE) {
                existing.title ?: defaultTitle
            } else {
                defaultTitle
            }

            val entity = if (existing != null && duplicateStrategy == DuplicateStrategy.REPLACE) {
                existing.copy(
                    format = detectedFormat,
                    title = title,
                    canonicalPath = finalFile.absolutePath,
                    originalUri = source.uri.toString(),
                    displayName = displayName,
                    mimeType = source.mimeType,
                    fingerprintSha256 = fingerprint,
                    sizeBytes = copiedBytes,
                    coverPath = null,
                    updatedAtEpochMs = now
                )
            } else {
                BookEntity(
                    id = targetBookId,
                    format = detectedFormat,
                    title = title,
                    author = null,
                    language = null,
                    identifier = null,
                    canonicalPath = finalFile.absolutePath,
                    originalUri = source.uri.toString(),
                    displayName = displayName,
                    mimeType = source.mimeType,
                    fingerprintSha256 = fingerprint,
                    sizeBytes = copiedBytes,
                    coverPath = null,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now
                )
            }

            bookRepo.upsert(entity)
            ImportOneResult.Ok(targetBookId, fingerprint)
        } catch (throwable: Throwable) {
            runCatching { tempFile.delete() }
            val (code, message) = throwable.toImportError()
            ImportOneResult.Fail(code, message)
        }
    }

    private suspend fun copyWithDigest(
        source: ContentUriDocumentSource,
        outputFile: File,
        digest: java.security.MessageDigest
    ): Long {
        var total = 0L
        source.openInputStream().use { rawInput ->
            BufferedInputStream(rawInput).use { input ->
                outputFile.outputStream().use { fileOutput ->
                    BufferedOutputStream(fileOutput).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read <= 0) {
                                break
                            }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            total += read
                        }
                        output.flush()
                    }
                }
            }
        }
        return total
    }

    private suspend fun detectFormatFromFile(file: File): BookFormat {
        val source = FileDocumentSource(file, displayName = file.name)
        return when (val result = formatDetector.detect(source, hint = null)) {
            is ReaderResult.Ok -> result.value
            is ReaderResult.Err -> BookFormat.TXT
        }
    }

    private fun guessExtension(displayName: String, mimeType: String?): String {
        val lowerName = displayName.lowercase()
        return when {
            lowerName.endsWith(".epub") -> "epub"
            lowerName.endsWith(".pdf") -> "pdf"
            lowerName.endsWith(".txt") -> "txt"
            mimeType == "application/epub+zip" -> "epub"
            mimeType == "application/pdf" -> "pdf"
            else -> "txt"
        }
    }

    private suspend fun setForegroundSafe(done: Int, total: Int, currentTitle: String?) {
        runCatching {
            setForeground(ImportForeground.info(applicationContext, done, total, currentTitle))
        }
    }

    companion object {
        private const val KEY_JOB_ID = "job_id"
        private const val BUFFER_SIZE = 128 * 1024

        fun input(jobId: String): Data {
            return Data.Builder()
                .putString(KEY_JOB_ID, jobId)
                .build()
        }
    }
}
```

## core/work/src/main/kotlin/com/ireader/core/work/ImportErrorMapper.kt

```kotlin
package com.ireader.core.work

import com.ireader.reader.api.error.ReaderError
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.ZipException
import kotlinx.coroutines.CancellationException

internal fun Throwable.toImportError(): Pair<String, String> {
    return when (this) {
        is ReaderError -> code to (message ?: code)
        is CancellationException -> "CANCELLED" to (message ?: "Cancelled")
        is FileNotFoundException -> "NOT_FOUND" to (message ?: "Not found")
        is SecurityException -> "PERMISSION_DENIED" to (message ?: "Permission denied")
        is ZipException -> "CORRUPT_OR_INVALID" to (message ?: "Corrupt or invalid document")
        is IOException -> "IO" to (message ?: "I/O error")
        else -> "INTERNAL" to (message ?: this::class.java.simpleName)
    }
}
```

## core/work/src/main/kotlin/com/ireader/core/work/notification/ImportForeground.kt

```kotlin
package com.ireader.core.work.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo

object ImportForeground {
    const val CHANNEL_ID = "import"
    private const val CHANNEL_NAME = "Book Import"
    private const val NOTIFICATION_ID = 4242

    fun info(context: Context, done: Int, total: Int, currentTitle: String?): ForegroundInfo {
        ensureChannel(context)

        val contentTitle = currentTitle?.takeIf { it.isNotBlank() } ?: "Importing books"
        val contentText = if (total > 0) "$done / $total" else done.toString()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(total.coerceAtLeast(0), done.coerceAtLeast(0), total <= 0)
            .build()

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress while importing books"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
```

## core/work/src/main/kotlin/com/ireader/core/work/di/WorkModule.kt

```kotlin
package com.ireader.core.work.di

import android.content.Context
import androidx.work.WorkManager
import com.ireader.core.files.importing.ImportWorkScheduler
import com.ireader.core.work.WorkImportScheduler
import com.ireader.reader.runtime.format.BookFormatDetector
import com.ireader.reader.runtime.format.DefaultBookFormatDetector
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkBindingsModule {
    @Binds
    @Singleton
    abstract fun bindImportWorkScheduler(impl: WorkImportScheduler): ImportWorkScheduler
}

@Module
@InstallIn(SingletonComponent::class)
object WorkProvidesModule {
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideBookFormatDetector(): BookFormatDetector = DefaultBookFormatDetector()
}
```

## core/work/src/main/AndroidManifest.xml

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

    <application>
        <service
            android:name="androidx.work.impl.foreground.SystemForegroundService"
            android:foregroundServiceType="dataSync"
            tools:node="merge" />
    </application>
</manifest>
```

## core/data/src/main/kotlin/com/ireader/core/data/importing/ImportJobRepo.kt

```kotlin
package com.ireader.core.data.importing

import com.ireader.core.database.importing.ImportJobDao
import com.ireader.core.database.importing.ImportJobEntity
import com.ireader.core.database.importing.ImportStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class ImportJobRepo @Inject constructor(
    private val dao: ImportJobDao
) {
    suspend fun upsert(entity: ImportJobEntity) = dao.upsert(entity)

    suspend fun get(jobId: String) = dao.get(jobId)

    fun observe(jobId: String): Flow<ImportJobEntity?> = dao.observe(jobId)

    suspend fun updateProgress(
        jobId: String,
        status: ImportStatus,
        total: Int,
        done: Int,
        currentTitle: String?,
        errorMessage: String?,
        now: Long
    ) = dao.updateProgress(
        jobId = jobId,
        status = status,
        total = total,
        done = done,
        currentTitle = currentTitle,
        errorMessage = errorMessage,
        updatedAt = now
    )
}
```

## core/data/src/main/kotlin/com/ireader/core/data/importing/ImportItemRepo.kt

```kotlin
package com.ireader.core.data.importing

import com.ireader.core.database.importing.ImportItemDao
import com.ireader.core.database.importing.ImportItemEntity
import com.ireader.core.database.importing.ImportItemStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportItemRepo @Inject constructor(
    private val dao: ImportItemDao
) {
    suspend fun upsertAll(items: List<ImportItemEntity>) = dao.upsertAll(items)

    suspend fun list(jobId: String) = dao.list(jobId)

    suspend fun listPendingOrFailed(jobId: String) = dao.listPendingOrFailed(jobId)

    suspend fun listSucceededBookIds(jobId: String): List<String> = dao.listSucceededBookIds(jobId)

    suspend fun update(
        jobId: String,
        uri: String,
        status: ImportItemStatus,
        bookId: String?,
        fingerprint: String?,
        errorCode: String?,
        errorMessage: String?,
        now: Long
    ) = dao.update(
        jobId = jobId,
        uri = uri,
        status = status,
        bookId = bookId,
        fingerprint = fingerprint,
        errorCode = errorCode,
        errorMessage = errorMessage,
        updatedAt = now
    )
}
```

## core/database/src/main/kotlin/com/ireader/core/database/importing/ImportJobDao.kt

```kotlin
package com.ireader.core.database.importing

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ImportJobEntity)

    @Query("SELECT * FROM import_jobs WHERE jobId = :jobId LIMIT 1")
    suspend fun get(jobId: String): ImportJobEntity?

    @Query("SELECT * FROM import_jobs WHERE jobId = :jobId LIMIT 1")
    fun observe(jobId: String): Flow<ImportJobEntity?>

    @Query(
        "UPDATE import_jobs " +
            "SET status = :status, total = :total, done = :done, currentTitle = :currentTitle, " +
            "errorMessage = :errorMessage, updatedAtEpochMs = :updatedAt " +
            "WHERE jobId = :jobId"
    )
    suspend fun updateProgress(
        jobId: String,
        status: ImportStatus,
        total: Int,
        done: Int,
        currentTitle: String?,
        errorMessage: String?,
        updatedAt: Long
    )
}
```

## core/database/src/main/kotlin/com/ireader/core/database/importing/ImportItemDao.kt

```kotlin
package com.ireader.core.database.importing

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ImportItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ImportItemEntity>)

    @Query("SELECT * FROM import_items WHERE jobId = :jobId ORDER BY updatedAtEpochMs ASC")
    suspend fun list(jobId: String): List<ImportItemEntity>

    @Query(
        "SELECT * FROM import_items " +
            "WHERE jobId = :jobId AND status IN ('PENDING', 'FAILED') " +
            "ORDER BY updatedAtEpochMs ASC"
    )
    suspend fun listPendingOrFailed(jobId: String): List<ImportItemEntity>

    @Query(
        "UPDATE import_items " +
            "SET status = :status, bookId = :bookId, fingerprintSha256 = :fingerprint, " +
            "errorCode = :errorCode, errorMessage = :errorMessage, updatedAtEpochMs = :updatedAt " +
            "WHERE jobId = :jobId AND uri = :uri"
    )
    suspend fun update(
        jobId: String,
        uri: String,
        status: ImportItemStatus,
        bookId: String?,
        fingerprint: String?,
        errorCode: String?,
        errorMessage: String?,
        updatedAt: Long
    )

    @Query("DELETE FROM import_items WHERE jobId = :jobId")
    suspend fun deleteByJob(jobId: String)

    @Query(
        "SELECT DISTINCT bookId FROM import_items " +
            "WHERE jobId = :jobId AND status = 'SUCCEEDED' AND bookId IS NOT NULL"
    )
    suspend fun listSucceededBookIds(jobId: String): List<String>
}
```

## core/database/src/main/kotlin/com/ireader/core/database/importing/ImportJobEntity.kt

```kotlin
package com.ireader.core.database.importing

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "import_jobs")
data class ImportJobEntity(
    @PrimaryKey val jobId: String,
    val status: ImportStatus,
    val total: Int,
    val done: Int,
    val currentTitle: String?,
    val errorMessage: String?,
    val sourceTreeUri: String?,
    val duplicateStrategy: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
```

## core/database/src/main/kotlin/com/ireader/core/database/importing/ImportItemEntity.kt

```kotlin
package com.ireader.core.database.importing

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "import_items",
    primaryKeys = ["jobId", "uri"],
    indices = [Index("jobId")]
)
data class ImportItemEntity(
    val jobId: String,
    val uri: String,
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val status: ImportItemStatus,
    val bookId: String?,
    val fingerprintSha256: String?,
    val errorCode: String?,
    val errorMessage: String?,
    val updatedAtEpochMs: Long
)
```

## core/database/src/main/kotlin/com/ireader/core/database/importing/ImportStatus.kt

```kotlin
package com.ireader.core.database.importing

enum class ImportStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
```

## core/database/src/main/kotlin/com/ireader/core/database/importing/ImportItemStatus.kt

```kotlin
package com.ireader.core.database.importing

enum class ImportItemStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    SKIPPED,
    FAILED
}
```

## app/src/main/java/com/ireader/di/ReaderRuntimeModule.kt

```kotlin
package com.ireader.di

import android.content.Context
import com.ireader.engines.epub.EpubEngine
import com.ireader.engines.pdf.PdfEngine
import com.ireader.engines.txt.TxtEngine
import com.ireader.engines.txt.TxtEngineConfig
import com.ireader.reader.api.engine.EngineRegistry
import com.ireader.reader.runtime.DefaultReaderRuntime
import com.ireader.reader.runtime.ReaderRuntime
import com.ireader.reader.runtime.registry.EngineRegistryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReaderRuntimeModule {

    @Provides
    @Singleton
    fun provideEngineRegistry(
        @ApplicationContext context: Context
    ): EngineRegistry {
        return EngineRegistryImpl(
            setOf(
                TxtEngine(
                    config = TxtEngineConfig(
                        cacheDir = context.cacheDir,
                        persistPagination = true,
                        persistOutline = false
                    )
                ),
                EpubEngine(context = context),
                PdfEngine(context = context)
            )
        )
    }

    @Provides
    @Singleton
    fun provideReaderRuntime(
        engineRegistry: EngineRegistry
    ): ReaderRuntime {
        return DefaultReaderRuntime(engineRegistry)
    }
}
```

## core/work/src/main/kotlin/com/ireader/core/work/enrich/EnrichWorker.kt

```kotlin
package com.ireader.core.work.enrich

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.importing.ImportItemRepo
import com.ireader.core.database.book.BookEntity
import com.ireader.core.files.source.FileDocumentSource
import com.ireader.core.files.storage.BookStorage
import com.ireader.core.work.notification.ImportForeground
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.DocumentMetadata
import com.ireader.reader.runtime.ReaderRuntime
import com.ireader.core.work.enrich.epub.EpubZipEnricher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@HiltWorker
class EnrichWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val importItemRepo: ImportItemRepo,
    private val bookRepo: BookRepo,
    private val storage: BookStorage,
    private val runtime: ReaderRuntime
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobId = EnrichWorkerInput.jobId(inputData) ?: return@withContext Result.failure()
        val bookIds = importItemRepo.listSucceededBookIds(jobId)
        if (bookIds.isEmpty()) {
            return@withContext Result.success()
        }

        val metrics = applicationContext.resources.displayMetrics
        val thumbWidth = minOf(720, metrics.widthPixels.coerceAtLeast(480))
        val thumbHeight = (thumbWidth * 4 / 3).coerceAtLeast(720)

        var done = 0
        val total = bookIds.size
        val throttle = ProgressThrottle(minIntervalMs = 500L)

        try {
            updateProgress(done, total, "Enriching…")

            for (bookId in bookIds) {
                currentCoroutineContext().ensureActive()

                val book = bookRepo.getById(bookId) ?: run {
                    done += 1
                    if (throttle.shouldUpdate()) {
                        updateProgress(done, total, "Enriching…")
                    }
                    continue
                }

                val needMeta = book.title.isNullOrBlank() ||
                    book.author.isNullOrBlank() ||
                    book.language.isNullOrBlank() ||
                    book.identifier.isNullOrBlank()
                val needCover = book.coverPath?.let { path ->
                    path.isBlank() || !File(path).exists()
                } ?: true

                if (!needMeta && !needCover) {
                    done += 1
                    if (throttle.shouldUpdate()) {
                        updateProgress(done, total, book.title ?: "Enriching…")
                    }
                    continue
                }

                val file = File(book.canonicalPath)
                if (!file.exists()) {
                    done += 1
                    if (throttle.shouldUpdate()) {
                        updateProgress(done, total, book.title ?: "Enriching…")
                    }
                    continue
                }

                runCatching {
                    when (book.format) {
                        BookFormat.EPUB -> enrichEpub(
                            book = book,
                            file = file,
                            needMeta = needMeta,
                            needCover = needCover,
                            thumbWidth = thumbWidth,
                            thumbHeight = thumbHeight
                        )

                        BookFormat.TXT -> enrichTxt(
                            book = book,
                            file = file,
                            needMeta = needMeta,
                            needCover = needCover,
                            thumbWidth = thumbWidth,
                            thumbHeight = thumbHeight
                        )

                        BookFormat.PDF -> enrichPdf(
                            book = book,
                            file = file,
                            needMeta = needMeta
                        )
                    }
                }

                done += 1
                if (throttle.shouldUpdate()) {
                    updateProgress(done, total, book.title ?: "Enriching…")
                }
            }

            throttle.force()
            updateProgress(done, total, "Enrich complete")
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Enrich is best-effort; do not block import main flow.
            Result.success()
        }
    }

    private suspend fun enrichEpub(
        book: BookEntity,
        file: File,
        needMeta: Boolean,
        needCover: Boolean,
        thumbWidth: Int,
        thumbHeight: Int
    ) {
        // 统一由引擎提供元数据（包括 extra["coverPath"]）
        val metadata = if (needMeta || needCover) {
            readMetadataFromRuntime(book = book, file = file)
        } else {
            null
        }

        var newTitle: String? = book.title
        var newAuthor: String? = book.author
        var newLanguage: String? = book.language
        var newIdentifier: String? = book.identifier
        var coverPath: String? = book.coverPath

        if (needMeta && metadata != null) {
            if (newTitle.isNullOrBlank()) newTitle = metadata.title
            if (newAuthor.isNullOrBlank()) newAuthor = metadata.author
            if (newLanguage.isNullOrBlank()) newLanguage = metadata.language
            if (newIdentifier.isNullOrBlank()) newIdentifier = metadata.identifier
        }

        if (needCover) {
            val coverFile = storage.coverFile(book.id)
            val titleForCover = newTitle ?: book.title ?: book.displayName ?: "Untitled"

            val coverPathInZip = metadata?.extra?.get("coverPath")

            val extracted = coverPathInZip?.let { pathInZip ->
                EpubZipEnricher.tryExtractCoverToPng(
                    file = file,
                    coverPathInZip = pathInZip,
                    outFile = coverFile,
                    reqWidth = thumbWidth,
                    reqHeight = thumbHeight
                )
            } ?: false

            if (!extracted) {
                val placeholder = CoverRenderer.placeholderBitmap(thumbWidth, thumbHeight, titleForCover)
                BitmapIO.savePng(coverFile, placeholder)
            }
            coverPath = coverFile.absolutePath
        }

        upsertIfChanged(
            book = book,
            title = newTitle,
            author = newAuthor,
            language = newLanguage,
            identifier = newIdentifier,
            coverPath = coverPath
        )
    }
    private suspend fun enrichTxt(
        book: BookEntity,
        file: File,
        needMeta: Boolean,
        needCover: Boolean,
        thumbWidth: Int,
        thumbHeight: Int
    ) {
        var newTitle: String? = book.title
        var newAuthor: String? = book.author
        var newLanguage: String? = book.language
        var newIdentifier: String? = book.identifier
        var coverPath: String? = book.coverPath

        if (needMeta) {
            val metadata = readMetadataFromRuntime(book = book, file = file)
            if (metadata != null) {
                if (newTitle.isNullOrBlank()) {
                    newTitle = metadata.title
                }
                if (newAuthor.isNullOrBlank()) {
                    newAuthor = metadata.author
                }
                if (newLanguage.isNullOrBlank()) {
                    newLanguage = metadata.language
                }
                if (newIdentifier.isNullOrBlank()) {
                    newIdentifier = metadata.identifier
                }
            }
        }

        if (needCover) {
            val coverFile = storage.coverFile(book.id)
            val titleForCover = newTitle ?: book.title ?: book.displayName ?: "Untitled"
            val placeholder = CoverRenderer.placeholderBitmap(thumbWidth, thumbHeight, titleForCover)
            BitmapIO.savePng(coverFile, placeholder)
            coverPath = coverFile.absolutePath
        }

        upsertIfChanged(
            book = book,
            title = newTitle,
            author = newAuthor,
            language = newLanguage,
            identifier = newIdentifier,
            coverPath = coverPath
        )
    }

    private suspend fun enrichPdf(
        book: BookEntity,
        file: File,
        needMeta: Boolean
    ) {
        if (!needMeta) {
            return
        }

        var newTitle: String? = book.title
        var newAuthor: String? = book.author
        var newLanguage: String? = book.language
        var newIdentifier: String? = book.identifier

        val metadata = readMetadataFromRuntime(book = book, file = file)
        if (metadata != null) {
            if (newTitle.isNullOrBlank()) {
                newTitle = metadata.title
            }
            if (newAuthor.isNullOrBlank()) {
                newAuthor = metadata.author
            }
            if (newLanguage.isNullOrBlank()) {
                newLanguage = metadata.language
            }
            if (newIdentifier.isNullOrBlank()) {
                newIdentifier = metadata.identifier
            }
        }

        upsertIfChanged(
            book = book,
            title = newTitle,
            author = newAuthor,
            language = newLanguage,
            identifier = newIdentifier,
            coverPath = book.coverPath
        )
    }

    private suspend fun readMetadataFromRuntime(
        book: BookEntity,
        file: File
    ): DocumentMetadata? {
        val source = FileDocumentSource(file, displayName = book.displayName ?: file.name)
        val documentResult = runtime.openDocument(
            source = source,
            options = OpenOptions(hintFormat = book.format)
        )
        if (documentResult !is ReaderResult.Ok) {
            return null
        }

        return documentResult.value.use { document ->
            when (val metadataResult = document.metadata()) {
                is ReaderResult.Ok -> metadataResult.value
                is ReaderResult.Err -> null
            }
        }
    }

    private suspend fun upsertIfChanged(
        book: BookEntity,
        title: String?,
        author: String?,
        language: String?,
        identifier: String?,
        coverPath: String?
    ) {
        val changed = title != book.title ||
            author != book.author ||
            language != book.language ||
            identifier != book.identifier ||
            coverPath != book.coverPath

        if (!changed) {
            return
        }

        bookRepo.upsert(
            book.copy(
                title = title,
                author = author,
                language = language,
                identifier = identifier,
                coverPath = coverPath,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    private suspend fun updateProgress(done: Int, total: Int, title: String?) {
        runCatching {
            setProgress(EnrichProgress.data(done, total, title))
        }
        runCatching {
            setForeground(ImportForeground.info(applicationContext, done, total, title))
        }
    }
}
```

## core/work/src/main/kotlin/com/ireader/core/work/enrich/EnrichWorkerInput.kt

```kotlin
package com.ireader.core.work.enrich

import androidx.work.Data

object EnrichWorkerInput {
    private const val KEY_JOB_ID = "job_id"

    fun data(jobId: String): Data {
        return Data.Builder()
            .putString(KEY_JOB_ID, jobId)
            .build()
    }

    fun jobId(data: Data): String? = data.getString(KEY_JOB_ID)
}
```

## core/work/src/main/kotlin/com/ireader/core/work/enrich/EnrichProgress.kt

```kotlin
package com.ireader.core.work.enrich

import androidx.work.Data
import androidx.work.workDataOf

object EnrichProgress {
    const val KEY_DONE = "done"
    const val KEY_TOTAL = "total"
    const val KEY_TITLE = "title"

    fun data(done: Int, total: Int, title: String?): Data {
        return workDataOf(
            KEY_DONE to done,
            KEY_TOTAL to total,
            KEY_TITLE to (title ?: "")
        )
    }
}
```

## core/work/src/main/kotlin/com/ireader/core/work/enrich/ProgressThrottle.kt

```kotlin
package com.ireader.core.work.enrich

class ProgressThrottle(
    private val minIntervalMs: Long = 500L
) {
    private var lastUpdateMs: Long = 0L

    fun shouldUpdate(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (nowMs - lastUpdateMs < minIntervalMs) {
            return false
        }
        lastUpdateMs = nowMs
        return true
    }

    fun force(nowMs: Long = System.currentTimeMillis()) {
        lastUpdateMs = nowMs
    }
}
```

## core/work/src/main/kotlin/com/ireader/core/work/enrich/CoverRenderer.kt

```kotlin
package com.ireader.core.work.enrich

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.ireader.reader.api.render.RenderContent
import com.ireader.reader.api.render.RenderPage
import com.ireader.reader.api.render.TileRequest

object CoverRenderer {
    suspend fun renderCoverBitmap(
        page: RenderPage,
        desiredWidth: Int,
        desiredHeight: Int,
        titleFallback: String
    ): Bitmap {
        return when (val content = page.content) {
            is RenderContent.BitmapPage -> scaleTo(content.bitmap, desiredWidth, desiredHeight)
            is RenderContent.Tiles -> {
                val scale = minScale(
                    desiredWidth.toFloat() / content.pageWidthPx.toFloat(),
                    desiredHeight.toFloat() / content.pageHeightPx.toFloat()
                )
                val tile = runCatching {
                    content.tileProvider.renderTile(
                        TileRequest(
                            leftPx = 0,
                            topPx = 0,
                            widthPx = content.pageWidthPx,
                            heightPx = content.pageHeightPx,
                            scale = scale
                        )
                    )
                }.getOrNull()

                if (tile != null) {
                    scaleTo(tile, desiredWidth, desiredHeight)
                } else {
                    placeholder(desiredWidth, desiredHeight, titleFallback)
                }
            }

            is RenderContent.Text,
            is RenderContent.Html -> placeholder(desiredWidth, desiredHeight, titleFallback)
        }
    }

    private fun scaleTo(src: Bitmap, width: Int, height: Int): Bitmap {
        if (src.width == width && src.height == height) {
            return src
        }
        return Bitmap.createScaledBitmap(src, width, height, true)
    }

    private fun minScale(a: Float, b: Float): Float = if (a < b) a else b

    fun placeholderBitmap(width: Int, height: Int, title: String): Bitmap {
        return placeholder(width, height, title)
    }

    private fun placeholder(width: Int, height: Int, title: String): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = (width * 0.06f).coerceAtLeast(24f)
        }

        val lines = breakLines(
            text = title.ifBlank { "Untitled" },
            maxCharsPerLine = 18,
            maxLines = 6
        )
        val padding = width * 0.08f
        var y = height * 0.25f
        for (line in lines) {
            canvas.drawText(line, padding, y, paint)
            y += paint.textSize * 1.25f
        }

        return bitmap
    }

    private fun breakLines(text: String, maxCharsPerLine: Int, maxLines: Int): List<String> {
        val result = ArrayList<String>(maxLines)
        var index = 0
        while (index < text.length && result.size < maxLines) {
            val end = (index + maxCharsPerLine).coerceAtMost(text.length)
            result += text.substring(index, end)
            index = end
        }
        return result
    }
}
```

## core/work/src/main/kotlin/com/ireader/core/work/enrich/BitmapIO.kt

```kotlin
package com.ireader.core.work.enrich

import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

object BitmapIO {
    fun savePng(file: File, bitmap: Bitmap) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.flush()
        }
    }
}
```

## core/work/src/main/kotlin/com/ireader/core/work/enrich/BitmapDecode.kt

```kotlin
package com.ireader.core.work.enrich

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.max

object BitmapDecode {
    fun decodeSampled(bytes: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        val sample = calcInSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            reqWidth = reqWidth,
            reqHeight = reqHeight
        )
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }

    private fun calcInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return max(1, inSampleSize)
    }
}
```

## core/work/src/main/kotlin/com/ireader/core/work/enrich/epub/EpubZipEnricher.kt

```kotlin
package com.ireader.core.work.enrich.epub

import android.graphics.Bitmap
import android.util.Xml
import com.ireader.core.work.enrich.BitmapDecode
import com.ireader.core.work.enrich.BitmapIO
import com.ireader.reader.model.DocumentMetadata
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.ArrayDeque
import java.util.zip.ZipFile
import org.xmlpull.v1.XmlPullParser

object EpubZipEnricher {

    fun tryExtractCoverToPng(
        file: File,
        coverPathInZip: String,
        outFile: File,
        reqWidth: Int,
        reqHeight: Int
    ): Boolean {
        return runCatching {
            ZipFile(file).use { zip ->
                val normalizedPath = normalizeZipPath(coverPathInZip)
                val coverEntry = zip.getEntry(normalizedPath) ?: return false
                val bytes = zip.getInputStream(coverEntry).use { stream ->
                    stream.readAllBytesCapped(limitBytes = 12 * 1024 * 1024)
                } ?: return false

                val decoded = BitmapDecode.decodeSampled(bytes, reqWidth, reqHeight) ?: return false
                val outputBitmap = if (decoded.width == reqWidth && decoded.height == reqHeight) {
                    decoded
                } else {
                    Bitmap.createScaledBitmap(decoded, reqWidth, reqHeight, true)
                }
                BitmapIO.savePng(outFile, outputBitmap)
                true
            }
        }.getOrDefault(false)
    }

    private fun normalizeZipPath(path: String): String {
        val parts = path
            .replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() }

        val stack = ArrayDeque<String>(parts.size)
        for (part in parts) {
            when (part) {
                "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(part)
            }
        }
        return stack.joinToString("/")
    }

    private fun InputStream.readAllBytesCapped(limitBytes: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read <= 0) {
                break
            }
            total += read
            if (total > limitBytes) {
                return null
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

}
```

## feature/reader/src/main/kotlin/com/ireader/feature/reader/navigation/ReaderNavGraph.kt

```kotlin
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
```

## feature/reader/src/main/kotlin/com/ireader/feature/reader/navigation/ReaderRoute.kt

```kotlin
package com.ireader.feature.reader.navigation

import com.ireader.core.navigation.AppRoutes

object ReaderRoute {
    const val argBookId: String = AppRoutes.ARG_BOOK_ID
    const val route: String = AppRoutes.READER

    fun create(bookId: String): String = AppRoutes.reader(bookId)
}
```

## feature/reader/src/main/kotlin/com/ireader/feature/reader/domain/usecase/OpenBookUseCase.kt

```kotlin
package com.ireader.feature.reader.domain.usecase

import com.ireader.core.data.book.BookRepo
import com.ireader.core.database.book.BookEntity
import com.ireader.core.files.source.FileDocumentSource
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.runtime.ReaderRuntime
import com.ireader.reader.runtime.ReaderSessionHandle
import java.io.File
import javax.inject.Inject

sealed interface OpenBookResult {
    data class Success(
        val book: BookEntity,
        val session: ReaderSessionHandle
    ) : OpenBookResult

    data class Failure(
        val message: String
    ) : OpenBookResult
}

class OpenBookUseCase @Inject constructor(
    private val bookRepo: BookRepo,
    private val readerRuntime: ReaderRuntime
) {
    suspend operator fun invoke(bookId: String): OpenBookResult {
        val book = bookRepo.getById(bookId)
            ?: return OpenBookResult.Failure("未找到书籍记录")

        val file = File(book.canonicalPath)
        if (!file.exists()) {
            return OpenBookResult.Failure("书籍文件不存在")
        }

        val source = FileDocumentSource(
            file = file,
            displayName = book.displayName ?: file.name,
            mimeType = book.mimeType
        )
        return when (
            val result = readerRuntime.openSession(
                source = source,
                options = OpenOptions(hintFormat = book.format)
            )
        ) {
            is ReaderResult.Ok -> OpenBookResult.Success(book = book, session = result.value)
            is ReaderResult.Err -> OpenBookResult.Failure(result.error.toUserMessage())
        }
    }
}

private fun ReaderError.toUserMessage(): String {
    return when (this) {
        is ReaderError.NotFound -> "文件不存在"
        is ReaderError.PermissionDenied -> "没有文件访问权限"
        is ReaderError.UnsupportedFormat -> "当前格式暂不支持打开"
        is ReaderError.InvalidPassword -> "文档密码错误"
        is ReaderError.CorruptOrInvalid -> "文档损坏或格式无效"
        is ReaderError.DrmRestricted -> "文档受 DRM 限制"
        is ReaderError.Io -> "读取文件失败"
        is ReaderError.Cancelled -> "打开已取消"
        is ReaderError.Internal -> message ?: "打开书籍失败"
    }
}
```


