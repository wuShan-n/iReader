package com.ireader.feature.library.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ireader.core.data.book.LibraryBookItem
import com.ireader.core.data.book.LibrarySort
import com.ireader.core.database.book.IndexState
import com.ireader.core.database.book.ReadingStatus
import com.ireader.feature.library.presentation.LibraryViewModel
import com.ireader.feature.library.ui.components.BookGridItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionNaming")
fun LibraryScreen(
    onOpenBook: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    vm: LibraryViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var selectedBook by remember { mutableStateOf<LibraryBookItem?>(null) }
    var relinkTargetBookId by remember { mutableLongStateOf(-1L) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        vm.startImport(uris)
    }

    val relinkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && relinkTargetBookId > 0L) {
            vm.relinkBook(relinkTargetBookId, uri)
        }
        relinkTargetBookId = -1L
    }

    LaunchedEffect(state.activeImportJobId) {
        if (state.activeImportJobId == null && relinkTargetBookId > 0L) {
            relinkTargetBookId = -1L
        }
    }

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

                    TextButton(onClick = { filterMenuExpanded = true }) {
                        Text("筛选")
                    }
                    FilterMenu(
                        expanded = filterMenuExpanded,
                        state = state,
                        onDismiss = { filterMenuExpanded = false },
                        onToggleFavorite = { vm.setOnlyFavorites(!state.onlyFavorites) },
                        onToggleReadingStatus = { status, enabled ->
                            vm.setReadingStatusFilter(status, enabled)
                        },
                        onToggleIndexState = { status, enabled ->
                            vm.setIndexStateFilter(status, enabled)
                        },
                        onSelectCollection = { collectionId ->
                            vm.setCollectionFilter(collectionId)
                        }
                    )

                    TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
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
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                value = state.keyword,
                onValueChange = vm::setKeyword,
                singleLine = true,
                label = { Text("搜索标题/作者/文件名") }
            )

            if (!state.importStatusText.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.importStatusText.orEmpty(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = vm::dismissImportStatus) {
                        Text("关闭")
                    }
                }
            }

            if (state.books.isEmpty()) {
                EmptyLibrary(
                    onImportBooks = { importLauncher.launch(arrayOf("*/*")) },
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
                    key = { item -> item.book.bookId }
                ) { item ->
                    BookGridItem(
                        book = item,
                        onClick = { onOpenBook(item.book.bookId) },
                        onLongClick = { selectedBook = item }
                    )
                }
            }
        }
    }

    selectedBook?.let { book ->
        BookActionDialog(
            item = book,
            onDismiss = { selectedBook = null },
            onDelete = {
                vm.deleteBook(book.book.bookId)
                selectedBook = null
            },
            onToggleFavorite = {
                vm.toggleFavorite(book.book.bookId, book.book.favorite)
                selectedBook = null
            },
            onMarkStatus = { status ->
                vm.setReadingStatus(book.book.bookId, status)
                selectedBook = null
            },
            onAddToCollection = { name ->
                vm.addToCollection(book.book.bookId, name)
                selectedBook = null
            },
            onReindex = {
                vm.reindexBook(book.book.bookId)
                selectedBook = null
            },
            onRelink = {
                relinkTargetBookId = book.book.bookId
                relinkLauncher.launch(arrayOf("*/*"))
                selectedBook = null
            }
        )
    }
}

@Composable
private fun FilterMenu(
    expanded: Boolean,
    state: com.ireader.feature.library.presentation.LibraryUiState,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleReadingStatus: (ReadingStatus, Boolean) -> Unit,
    onToggleIndexState: (IndexState, Boolean) -> Unit,
    onSelectCollection: (Long?) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text(if (state.onlyFavorites) "✓ 仅收藏" else "仅收藏") },
            onClick = onToggleFavorite
        )
        HorizontalDivider()

        ReadingStatus.entries.forEach { status ->
            val enabled = state.statuses.contains(status)
            DropdownMenuItem(
                text = { Text(if (enabled) "✓ ${status.label()}" else status.label()) },
                onClick = { onToggleReadingStatus(status, !enabled) }
            )
        }

        HorizontalDivider()
        listOf(IndexState.PENDING, IndexState.ERROR, IndexState.MISSING).forEach { status ->
            val enabled = state.indexStates.contains(status)
            DropdownMenuItem(
                text = { Text(if (enabled) "✓ ${status.label()}" else status.label()) },
                onClick = { onToggleIndexState(status, !enabled) }
            )
        }

        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(if (state.selectedCollectionId == null) "✓ 全部合集" else "全部合集") },
            onClick = { onSelectCollection(null) }
        )
        state.collections.forEach { collection ->
            val checked = state.selectedCollectionId == collection.collectionId
            DropdownMenuItem(
                text = { Text(if (checked) "✓ ${collection.name}" else collection.name) },
                onClick = { onSelectCollection(collection.collectionId) }
            )
        }
    }
}

@Composable
private fun BookActionDialog(
    item: LibraryBookItem,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMarkStatus: (ReadingStatus) -> Unit,
    onAddToCollection: (String) -> Unit,
    onReindex: () -> Unit,
    onRelink: () -> Unit
) {
    var newCollectionName by rememberSaveable(item.book.bookId) { mutableStateOf("") }
    val favoriteText = if (item.book.favorite) "取消收藏" else "加入收藏"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.book.title?.ifBlank { item.book.fileName } ?: item.book.fileName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onToggleFavorite) { Text(favoriteText) }
                TextButton(onClick = { onMarkStatus(ReadingStatus.UNREAD) }) { Text("标记为未读") }
                TextButton(onClick = { onMarkStatus(ReadingStatus.READING) }) { Text("标记为在读") }
                TextButton(onClick = { onMarkStatus(ReadingStatus.FINISHED) }) { Text("标记为已读") }
                TextButton(onClick = onReindex) { Text("重新解析元数据") }
                TextButton(onClick = onRelink) { Text("重新定位文件") }

                OutlinedTextField(
                    value = newCollectionName,
                    onValueChange = { newCollectionName = it },
                    singleLine = true,
                    label = { Text("合集名称") }
                )
                TextButton(
                    onClick = {
                        onAddToCollection(newCollectionName.trim())
                        newCollectionName = ""
                    },
                    enabled = newCollectionName.isNotBlank()
                ) {
                    Text("加入合集")
                }

                TextButton(onClick = onDelete) {
                    Text("删除书籍")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
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
        SortItem(label = "最近打开", sort = LibrarySort.LAST_OPENED, current = current, onSelect = onSelect)
        SortItem(label = "标题 A-Z", sort = LibrarySort.TITLE_AZ, current = current, onSelect = onSelect)
        SortItem(label = "作者 A-Z", sort = LibrarySort.AUTHOR_AZ, current = current, onSelect = onSelect)
        SortItem(label = "阅读进度", sort = LibrarySort.PROGRESSION_DESC, current = current, onSelect = onSelect)
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

private fun ReadingStatus.label(): String {
    return when (this) {
        ReadingStatus.UNREAD -> "未读"
        ReadingStatus.READING -> "在读"
        ReadingStatus.FINISHED -> "已读"
    }
}

private fun IndexState.label(): String {
    return when (this) {
        IndexState.PENDING -> "待解析"
        IndexState.ERROR -> "解析失败"
        IndexState.MISSING -> "文件缺失"
        IndexState.INDEXED -> "已完成"
    }
}
