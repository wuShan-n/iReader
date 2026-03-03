package com.ireader.core.files.source

import android.content.Context
import android.net.Uri
import com.ireader.core.database.book.BookEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultBookSourceResolver @Inject constructor(
    @ApplicationContext private val context: Context
) : BookSourceResolver {
    override fun resolve(book: BookEntity): DocumentSource? {
        val sourceUri = book.sourceUri
        if (!sourceUri.isNullOrBlank()) {
            val uri = Uri.parse(sourceUri)
            if (uri.scheme == "content") {
                return ContentUriDocumentSource(context, uri)
            }
            if (uri.scheme == "file") {
                val path = uri.path ?: return null
                val file = File(path)
                if (!file.exists()) return null
                return FileDocumentSource(
                    file = file,
                    displayName = book.fileName,
                    mimeType = book.mimeType
                )
            }
        }

        val file = File(book.canonicalPath)
        if (!file.exists()) return null
        return FileDocumentSource(
            file = file,
            displayName = book.fileName,
            mimeType = book.mimeType
        )
    }
}

