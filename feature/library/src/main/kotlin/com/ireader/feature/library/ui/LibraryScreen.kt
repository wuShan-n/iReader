package com.ireader.feature.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
    onOpenBook: (Long) -> Unit,
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
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                value = state.keyword,
                onValueChange = vm::setKeyword,
                singleLine = true,
                label = { Text("搜索标题/作者/文件名") }
            )

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
                    key = { item -> item.book.bookId }
                ) { item ->
                    BookGridItem(
                        book = item,
                        onClick = { onOpenBook(item.book.bookId) },
                        onDelete = { vm.deleteBook(item.book.bookId) }
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
