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
    fun bookDir(bookId: Long): File {
        return File(context.filesDir, "books/$bookId").apply { mkdirs() }
    }

    fun canonicalFile(bookId: Long, ext: String): File {
        return File(bookDir(bookId), "original.$ext")
    }

    fun coverFile(bookId: Long): File {
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

    fun deleteBookFiles(bookId: Long) {
        bookDir(bookId).deleteRecursively()
    }

    fun deleteCanonical(bookId: Long) {
        val dir = bookDir(bookId)
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("original.") }
            ?.forEach { file -> runCatching { file.delete() } }
    }
}
