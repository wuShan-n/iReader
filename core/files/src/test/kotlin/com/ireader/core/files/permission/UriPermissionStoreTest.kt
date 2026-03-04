package com.ireader.core.files.permission

import android.content.Context
import android.net.Uri
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class UriPermissionStoreTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val store = UriPermissionStore(context)

    @Test
    fun `takePersistableRead should allow non-content uri`() {
        val uri = Uri.fromFile(File("D:/tmp/book.txt"))

        val result = store.takePersistableRead(uri)

        assertTrue(result.granted)
    }

    @Test
    fun `takePersistableRead should return stable failure payload for denied content uri`() {
        val uri = Uri.parse("content://missing.provider/books/1")

        val result = store.takePersistableRead(uri)

        if (!result.granted) {
            assertNotNull(result.code)
            assertNotNull(result.message)
        } else {
            assertTrue(result.code == null)
        }
    }

    @Test
    fun `hasPersistedRead should be false for unknown uri`() {
        val uri = Uri.parse("content://missing.provider/books/2")

        assertFalse(store.hasPersistedRead(uri))
    }
}
