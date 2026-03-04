package com.ireader.feature.annotations.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ireader.feature.annotations.presentation.AnnotationItemUi
import com.ireader.feature.annotations.presentation.AnnotationsViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionNaming")
fun AnnotationsScreen(
    bookId: Long,
    onBack: () -> Unit,
    onOpenLocator: (String) -> Unit,
    vm: AnnotationsViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val dateFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("返回")
            }
            Text(
                text = "笔记 · book=$bookId",
                style = MaterialTheme.typography.titleMedium
            )
        }

        state.errorMessage?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFFECEA),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(message, color = Color(0xFF9A2A1A))
                    TextButton(onClick = vm::onDismissError) {
                        Text("关闭")
                    }
                }
            }
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.draftContent,
            onValueChange = vm::onDraftContentChange,
            label = { Text("新建笔记内容") },
            maxLines = 3
        )
        Button(
            onClick = vm::createAnnotation,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("添加笔记")
        }

        if (state.isLoading) {
            Text("加载中...", color = Color.Gray)
        } else if (state.items.isEmpty()) {
            Text("暂无笔记", color = Color.Gray)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.items, key = { it.id }) { item ->
                    AnnotationCard(
                        item = item,
                        dateFormatter = dateFormatter,
                        isEditing = state.editingId == item.id,
                        editingText = state.editingContent,
                        onOpen = { onOpenLocator(item.locatorEncoded) },
                        onStartEdit = { vm.onStartEdit(item.id) },
                        onEditingTextChange = vm::onEditingContentChange,
                        onSaveEdit = vm::saveEditing,
                        onCancelEdit = vm::onCancelEdit,
                        onDelete = { vm.deleteAnnotation(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AnnotationCard(
    item: AnnotationItemUi,
    dateFormatter: DateTimeFormatter,
    isEditing: Boolean,
    editingText: String,
    onOpen: () -> Unit,
    onStartEdit: () -> Unit,
    onEditingTextChange: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val updatedAt = Instant.ofEpochMilli(item.updatedAtEpochMs)
        .atZone(ZoneId.systemDefault())
        .format(dateFormatter)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF7F7F8)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("${item.typeLabel} · $updatedAt", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            if (isEditing) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = editingText,
                    onValueChange = onEditingTextChange,
                    maxLines = 4
                )
            } else {
                Text(item.content, style = MaterialTheme.typography.bodyMedium)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onOpen) {
                    Text("跳转")
                }
                Row {
                    if (isEditing) {
                        TextButton(onClick = onSaveEdit) { Text("保存") }
                        TextButton(onClick = onCancelEdit) { Text("取消") }
                    } else {
                        TextButton(onClick = onStartEdit) { Text("编辑") }
                    }
                    TextButton(onClick = onDelete) { Text("删除") }
                }
            }
        }
    }
}
