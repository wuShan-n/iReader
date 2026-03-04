package com.ireader.core.files.source

import android.content.Context
import android.net.Uri
import com.ireader.core.data.book.BookRecord
import com.ireader.core.data.book.IndexState
import com.ireader.core.data.book.ReadingStatus
import com.ireader.reader.model.BookFormat
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DefaultBookSourceResolverTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val resolver = DefaultBookSourceResolver(context)

    @Test
    fun `resolve should return content source when source uri is content scheme`() {
        val book = newBookRecord(
            sourceUri = "content://books/1",
            canonicalPath = "missing"
        )

        val source = resolver.resolve(book)

        assertTrue(source is ContentUriDocumentSource)
    }

    @Test
    fun `resolve should return null when file uri does not exist`() {
        val file = File(context.filesDir, "missing-${System.nanoTime()}.txt")
        val book = newBookRecord(
            sourceUri = Uri.fromFile(file).toString(),
            canonicalPath = "missing"
        )

        val source = resolver.resolve(book)

        assertNull(source)
    }

    @Test
    fun `resolve should fallback to canonical path when source uri is blank`() {
        val file = Files.createTempFile("ireader-canonical", ".txt").toFile().apply {
            writeText("hello")
        }
        val book = newBookRecord(
            sourceUri = null,
            canonicalPath = file.absolutePath
        )

        val source = resolver.resolve(book)

        assertNotNull(source)
        assertTrue(source is FileDocumentSource)
    }

    @Test
    fun `resolve should return null when canonical path is missing`() {
        val missing = File(context.filesDir, "missing-canonical-${System.nanoTime()}.txt")
        val book = newBookRecord(
            sourceUri = null,
            canonicalPath = missing.absolutePath
        )

        val source = resolver.resolve(book)

        assertNull(source)
    }

    private fun newBookRecord(
        sourceUri: String?,
        canonicalPath: String
    ): BookRecord {
        val now = System.currentTimeMillis()
        return BookRecord(
            bookId = 1L,
            documentId = null,
            sourceUri = sourceUri,
            format = BookFormat.TXT,
            fileName = "book.txt",
            mimeType = "text/plain",
            fileSizeBytes = 1L,
            lastModifiedEpochMs = now,
            canonicalPath = canonicalPath,
            title = "Book",
            author = "Author",
            language = "en",
            identifier = null,
            series = null,
            description = null,
            coverPath = null,
            favorite = false,
            readingStatus = ReadingStatus.UNREAD,
            indexState = IndexState.PENDING,
            indexError = null,
            capabilitiesJson = null,
            addedAtEpochMs = now,
            updatedAtEpochMs = now,
            lastOpenedAtEpochMs = null
        )
    }
}
