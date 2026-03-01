package com.ireader.core.files.scan

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TreeScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun scan(treeUri: Uri): List<Uri> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val out = ArrayList<Uri>(128)
        traverse(root, out)
        return out
    }

    private fun traverse(node: DocumentFile, out: MutableList<Uri>) {
        val children = node.listFiles()
        for (child in children) {
            if (child.isDirectory) {
                traverse(child, out)
            } else if (child.isFile && isSupported(child.name)) {
                out += child.uri
            }
        }
    }

    private fun isSupported(name: String?): Boolean {
        val value = name?.lowercase() ?: return false
        return value.endsWith(".epub") || value.endsWith(".pdf") || value.endsWith(".txt")
    }
}
