package com.ireader.feature.library.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ireader.core.data.book.IndexState
import com.ireader.core.data.book.LibraryBookItem
import com.ireader.core.data.book.LibrarySort
import com.ireader.core.data.book.ReadingStatus
import com.ireader.core.designsystem.PrototypeIcons
import com.ireader.core.designsystem.ReaderTokens
import com.ireader.feature.library.presentation.LibraryUiState
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
    val isDark = isSystemInDarkTheme()
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var selectedBook by remember { mutableStateOf<LibraryBookItem?>(null) }
    var relinkTargetBookId by remember { mutableLongStateOf(-1L) }
    var isEditMode by rememberSaveable { mutableStateOf(false) }
    var selectedBookIds by rememberSaveable { mutableStateOf<Set<Long>>(emptySet()) }

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

    LaunchedEffect(state.books.size) {
        val allIds = state.books.map { it.book.bookId }.toSet()
        selectedBookIds = selectedBookIds.intersect(allIds)
    }

    val topColor = if (isDark) {
        ReaderTokens.Palette.LibraryBackgroundTopNight
    } else {
        ReaderTokens.Palette.LibraryBackgroundTopDay
    }
    val bottomColor = if (isDark) {
        ReaderTokens.Palette.LibraryBackgroundBottomNight
    } else {
        ReaderTokens.Palette.LibraryBackgroundBottomDay
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Surface(
                tonalElevation = 0.dp,
                color = Color.Transparent
            ) {
                Crossfade(targetState = isEditMode, label = "library_top_bar") { editing ->
                    if (editing) {
                        EditModeTopBar(
                            selectedCount = selectedBookIds.size,
                            allSelected = selectedBookIds.size == state.books.size && state.books.isNotEmpty(),
                            onToggleSelectAll = {
                                selectedBookIds = if (selectedBookIds.size == state.books.size) {
                                    emptySet()
                                } else {
                                    state.books.map { it.book.bookId }.toSet()
                                }
                            },
                            onDone = {
                                isEditMode = false
                                selectedBookIds = emptySet()
                            }
                        )
                    } else {
                        NormalTopBar(
                            isDark = isDark,
                            onOpenSearch = { },
                            onOpenMore = { filterMenuExpanded = true },
                            onEnterEdit = { isEditMode = true }
                        )
                    }
                }
            }
        },
        bottomBar = {
            Crossfade(targetState = isEditMode, label = "library_bottom_bar") { editing ->
                if (editing) {
                    BatchActionBar(
                        isDark = isDark,
                        enabled = selectedBookIds.isNotEmpty(),
                        onFavorite = {
                            state.books
                                .filter { it.book.bookId in selectedBookIds }
                                .forEach { item ->
                                    vm.toggleFavorite(item.book.bookId, item.book.favorite)
                                }
                        },
                        onMarkRead = {
                            selectedBookIds.forEach { id ->
                                vm.setReadingStatus(id, ReadingStatus.FINISHED)
                            }
                        },
                        onDelete = {
                            selectedBookIds.forEach(vm::deleteBook)
                            selectedBookIds = emptySet()
                        }
                    )
                } else {
                    LibraryBottomBar(isDark = isDark)
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(topColor, bottomColor)))
                .padding(padding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                AnimatedVisibility(
                    visible = !isEditMode,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    StatusRow(
                        state = state,
                        isDark = isDark,
                        onSort = { sortMenuExpanded = true },
                        onFilter = { filterMenuExpanded = true },
                        onImport = { importLauncher.launch(arrayOf("*/*")) },
                        onEnterEdit = { isEditMode = true }
                    )
                }

                LibrarySearchBar(
                    keyword = state.keyword,
                    isDark = isDark,
                    isEditMode = isEditMode,
                    onKeywordChange = vm::setKeyword
                )

                AnimatedVisibility(
                    visible = !state.importStatusText.isNullOrBlank(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    ImportStatusCard(
                        statusText = state.importStatusText.orEmpty(),
                        isDark = isDark,
                        onClose = vm::dismissImportStatus
                    )
                }

                if (state.books.isEmpty()) {
                    EmptyLibrary(
                        onImportBooks = { importLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(
                            items = state.books,
                            key = { item -> item.book.bookId }
                        ) { item ->
                            val id = item.book.bookId
                            val selected = id in selectedBookIds
                            BookGridItem(
                                book = item,
                                isEditMode = isEditMode,
                                isSelected = selected,
                                onClick = {
                                    if (isEditMode) {
                                        selectedBookIds = selectedBookIds.toggle(id)
                                    } else {
                                        onOpenBook(id)
                                    }
                                },
                                onLongClick = {
                                    if (isEditMode) {
                                        selectedBookIds = selectedBookIds.toggle(id)
                                    } else {
                                        selectedBook = item
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
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
private fun NormalTopBar(
    isDark: Boolean,
    onOpenSearch: () -> Unit,
    onOpenMore: () -> Unit,
    onEnterEdit: () -> Unit
) {
    val titleColor = if (isDark) Color(0xFFE4E4E4) else ReaderTokens.Palette.PrototypeTextPrimary
    val bodyColor = if (isDark) ReaderTokens.Palette.PrototypeTextTertiary else ReaderTokens.Palette.PrototypeTextSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "书架",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = titleColor
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Badge(
                containerColor = ReaderTokens.Palette.PrototypeBlue,
                contentColor = Color.White
            ) {
                Text("福利")
            }
            CircleActionButton(onClick = onOpenSearch) {
                PrototypeIcons.Search(modifier = Modifier.size(22.dp), tint = bodyColor)
            }
            CircleActionButton(onClick = onOpenMore) {
                PrototypeIcons.MoreHorizontal(modifier = Modifier.size(22.dp), tint = bodyColor)
            }
            TextButton(onClick = onEnterEdit) { Text("编辑", color = titleColor) }
        }
    }
}

@Composable
private fun CircleActionButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    IconButton(onClick = onClick) {
        icon()
    }
}

@Composable
private fun EditModeTopBar(
    selectedCount: Int,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    onDone: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onToggleSelectAll) {
            Text(if (allSelected) "取消全选" else "全选")
        }
        Text(
            text = "已选择${selectedCount}本书",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        TextButton(onClick = onDone) {
            Text("完成")
        }
    }
}

@Composable
private fun StatusRow(
    state: LibraryUiState,
    isDark: Boolean,
    onSort: () -> Unit,
    onFilter: () -> Unit,
    onImport: () -> Unit,
    onEnterEdit: () -> Unit
) {
    val progressPercent = (state.books.map { it.progression }.average().takeIf { !it.isNaN() } ?: 0.0) * 100.0
    val readTip = "本周读${(progressPercent * 0.6).toInt()}分钟"
    val pillColor = if (isDark) ReaderTokens.Palette.LibraryPillNight else ReaderTokens.Palette.PrototypeSurfaceMuted
    val itemColor = if (isDark) ReaderTokens.Palette.SecondaryTextNight else ReaderTokens.Palette.PrototypeTextSecondary
    val strongText = if (isDark) Color(0xFFE4E4E4) else ReaderTokens.Palette.PrototypeTextPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(color = pillColor, shape = RoundedCornerShape(999.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Text(
                text = readTip,
                style = MaterialTheme.typography.labelLarge,
                color = itemColor
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onSort) { Text("排序", color = itemColor) }
            TextButton(onClick = onFilter) {
                Text(filterLabel(state), color = itemColor)
            }
            Text("｜", color = itemColor.copy(alpha = 0.5f))
            TextButton(onClick = onEnterEdit) { Text("编辑", color = strongText) }
            TextButton(onClick = onImport) { Text("导入", color = itemColor) }
        }
    }
}

@Composable
private fun LibrarySearchBar(
    keyword: String,
    isDark: Boolean,
    isEditMode: Boolean,
    onKeywordChange: (String) -> Unit
) {
    val searchBg = if (isDark) ReaderTokens.Palette.LibrarySearchNight else ReaderTokens.Palette.LibrarySearchDay
    val placeholderColor = if (isDark) ReaderTokens.Palette.SecondaryTextNight else ReaderTokens.Palette.PrototypeTextTertiary
    val leadingTint = if (isDark) ReaderTokens.Palette.SecondaryTextNight else ReaderTokens.Palette.PrototypeTextSecondary
    TextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        value = keyword,
        onValueChange = onKeywordChange,
        singleLine = true,
        shape = RoundedCornerShape(ReaderTokens.Shape.CapsuleRadius),
        placeholder = { Text("搜索标题 / 作者 / 文件名", color = placeholderColor) },
        leadingIcon = {
            PrototypeIcons.Search(modifier = Modifier.size(20.dp), tint = leadingTint)
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = searchBg,
            unfocusedContainerColor = searchBg,
            disabledContainerColor = searchBg,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        enabled = !isEditMode
    )
}

@Composable
private fun ImportStatusCard(
    statusText: String,
    isDark: Boolean,
    onClose: () -> Unit
) {
    val bg = if (isDark) {
        ReaderTokens.Palette.ReaderPanelElevatedNight
    } else {
        ReaderTokens.Palette.ReaderPanelElevatedDay
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = bg,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = statusText,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = onClose) {
                Text("关闭")
            }
        }
    }
}

@Composable
private fun BatchActionBar(
    isDark: Boolean,
    enabled: Boolean,
    onFavorite: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit
) {
    val bg = if (isDark) ReaderTokens.Palette.ReaderPanelElevatedNight else ReaderTokens.Palette.PrototypeSurface
    val contentColor = if (isDark) ReaderTokens.Palette.SecondaryTextNight else ReaderTokens.Palette.PrototypeTextSecondary
    Surface(
        tonalElevation = 2.dp,
        shadowElevation = 10.dp,
        color = bg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BatchIconAction(
                label = "置顶",
                icon = { tint -> PrototypeIcons.PinTop(tint = tint) },
                enabled = enabled,
                tint = contentColor,
                onClick = onFavorite
            )
            BatchIconAction(
                label = "加入书单",
                icon = { tint -> PrototypeIcons.FolderPlus(tint = tint) },
                enabled = enabled,
                tint = contentColor,
                onClick = onMarkRead
            )
            BatchIconAction(
                label = "移动至",
                icon = { tint -> PrototypeIcons.Folder(tint = tint) },
                enabled = enabled,
                tint = contentColor,
                onClick = onFavorite
            )
            BatchIconAction(
                label = "分享",
                icon = { tint -> PrototypeIcons.Share(tint = tint) },
                enabled = enabled,
                tint = contentColor,
                onClick = onFavorite
            )
            BatchIconAction(
                label = "删除",
                icon = { tint -> PrototypeIcons.Trash(tint = tint) },
                enabled = enabled,
                tint = ReaderTokens.Palette.PrototypeDanger,
                onClick = onDelete
            )
            BatchIconAction(
                label = "更多",
                icon = { tint -> PrototypeIcons.MoreHorizontal(tint = tint) },
                enabled = enabled,
                tint = contentColor,
                onClick = onFavorite
            )
        }
    }
}

@Composable
private fun BatchIconAction(
    label: String,
    icon: @Composable (Color) -> Unit,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, enabled = enabled) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon(if (enabled) tint else tint.copy(alpha = 0.45f))
            Text(
                text = label,
                color = if (enabled) tint else tint.copy(alpha = 0.45f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun LibraryBottomBar(
    isDark: Boolean
) {
    val bg = if (isDark) ReaderTokens.Palette.LibraryBackgroundBottomNight else ReaderTokens.Palette.PrototypeSurface
    val divider = if (isDark) ReaderTokens.Palette.LibraryDividerNight else ReaderTokens.Palette.PrototypeBorder
    Surface(
        color = bg,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = divider, shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            LibraryTabItem(
                label = "书架",
                icon = { tint -> PrototypeIcons.Book(tint = tint) },
                selected = true
            )
            LibraryTabItem(
                label = "书城",
                icon = { tint -> PrototypeIcons.Store(tint = tint) },
                selected = false
            )
            LibraryTabItem(
                label = "会员",
                icon = { tint -> PrototypeIcons.Crown(tint = tint) },
                selected = false
            )
            LibraryTabItem(
                label = "我的",
                icon = { tint -> PrototypeIcons.Smile(tint = tint) },
                selected = false
            )
        }
    }
}

@Composable
private fun LibraryTabItem(
    label: String,
    icon: @Composable (Color) -> Unit,
    selected: Boolean
) {
    val tint = if (selected) {
        ReaderTokens.Palette.PrototypeBlue
    } else {
        ReaderTokens.Palette.PrototypeTextSecondary
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        icon(tint)
        Spacer(modifier = Modifier.size(2.dp))
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun FilterMenu(
    expanded: Boolean,
    state: LibraryUiState,
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

                TextField(
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

                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = ReaderTokens.Palette.PrototypeDanger)) {
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
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "书架为空", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "导入后将自动解析封面和元数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onImportBooks) {
                    Text("导入书籍")
                }
            }
        }
    }
}

private fun filterLabel(state: LibraryUiState): String {
    val count = state.statuses.size + state.indexStates.size + (if (state.onlyFavorites) 1 else 0) +
        (if (state.selectedCollectionId != null) 1 else 0)
    return if (count <= 0) "筛选" else "筛选($count)"
}

private fun Set<Long>.toggle(id: Long): Set<Long> {
    return if (contains(id)) this - id else this + id
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
