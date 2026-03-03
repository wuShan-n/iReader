package com.ireader.core.files.scan

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class TreeScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun scan(treeUri: Uri): List<Uri> {
        val out = ArrayList<Uri>(128)
        scan(treeUri) { out += it }
        return out
    }

    suspend fun scan(treeUri: Uri, onFile: suspend (Uri) -> Unit) = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext
        traverse(node = root, onFile = onFile)
    }

    private suspend fun traverse(node: DocumentFile, onFile: suspend (Uri) -> Unit) {
        val children = node.listFiles()
        for (child in children) {
            if (child.isDirectory) {
                traverse(child, onFile)
            } else if (child.isFile && isSupported(child.name)) {
                onFile(child.uri)
            }
        }
    }

    private fun isSupported(name: String?): Boolean {
        val value = name?.lowercase() ?: return false
        return value.endsWith(".epub") || value.endsWith(".pdf") || value.endsWith(".txt")
    }
}
