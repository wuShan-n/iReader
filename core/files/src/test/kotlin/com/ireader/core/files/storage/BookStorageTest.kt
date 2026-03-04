package com.ireader.core.files.storage

import android.content.Context
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BookStorageTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val storage = BookStorage(context)

    @Test
    fun `canonical and cover files should be created under book directory`() {
        val bookId = uniqueBookId()

        val canonical = storage.canonicalFile(bookId, "epub")
        val cover = storage.coverFile(bookId)

        assertEquals("original.epub", canonical.name)
        assertEquals("cover.png", cover.name)
        assertEquals(storage.bookDir(bookId).absolutePath, canonical.parentFile?.absolutePath)
        assertEquals(storage.bookDir(bookId).absolutePath, cover.parentFile?.absolutePath)
    }

    @Test
    fun `atomicMove should replace existing destination file`() {
        val bookId = uniqueBookId()
        val from = File(storage.bookDir(bookId), "from.tmp").apply { writeText("new") }
        val to = File(storage.bookDir(bookId), "to.tmp").apply { writeText("old") }

        storage.atomicMove(from, to)

        assertFalse(from.exists())
        assertTrue(to.exists())
        assertEquals("new", to.readText())
    }

    @Test
    fun `deleteCanonicalExcept should keep only selected canonical file`() {
        val bookId = uniqueBookId()
        val keep = storage.canonicalFile(bookId, "epub").apply { writeText("keep") }
        val removePdf = storage.canonicalFile(bookId, "pdf").apply { writeText("remove") }
        val removeTxt = storage.canonicalFile(bookId, "txt").apply { writeText("remove") }

        storage.deleteCanonicalExcept(bookId, keep.absolutePath)

        assertTrue(keep.exists())
        assertFalse(removePdf.exists())
        assertFalse(removeTxt.exists())
    }

    @Test
    fun `deleteBookFiles should remove whole book directory`() {
        val bookId = uniqueBookId()
        val bookDir = storage.bookDir(bookId)
        File(bookDir, "original.txt").writeText("x")

        storage.deleteBookFiles(bookId)

        assertFalse(bookDir.exists())
    }

    private fun uniqueBookId(): Long = System.nanoTime()
}
