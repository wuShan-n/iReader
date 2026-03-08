package com.ireader.engines.common.android.id

import android.net.Uri
import android.os.ParcelFileDescriptor
import com.ireader.reader.api.open.DocumentSource
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SourceDocumentIdsTest {

    @Test
    fun `display name should be ignored by default`() {
        val sourceA = fakeSource(
            uri = "file:///books/a.txt",
            sourceDisplayName = "book-a.txt"
        )
        val sourceB = fakeSource(
            uri = "file:///books/a.txt",
            sourceDisplayName = "another-name.txt"
        )

        val idA = SourceDocumentIds.fromSourceSha256(source = sourceA, length = 40, prefix = "txt")
        val idB = SourceDocumentIds.fromSourceSha256(source = sourceB, length = 40, prefix = "txt")

        assertEquals(idA, idB)
    }

    @Test
    fun `display name should change id when explicitly included`() {
        val sourceA = fakeSource(
            uri = "file:///books/a.txt",
            sourceDisplayName = "book-a.txt"
        )
        val sourceB = fakeSource(
            uri = "file:///books/a.txt",
            sourceDisplayName = "another-name.txt"
        )

        val idA = SourceDocumentIds.fromSourceSha256(
            source = sourceA,
            length = 40,
            prefix = "txt",
            includeDisplayName = true
        )
        val idB = SourceDocumentIds.fromSourceSha256(
            source = sourceB,
            length = 40,
            prefix = "txt",
            includeDisplayName = true
        )

        assertNotEquals(idA, idB)
    }

    @Test
    fun `canonical encoding should avoid delimiter collisions`() {
        val sourceA = fakeSource(
            uri = "file:///a|b",
            sourceDisplayName = "c.txt"
        )
        val sourceB = fakeSource(
            uri = "file:///a",
            sourceDisplayName = "b|c.txt"
        )

        val idA = SourceDocumentIds.fromSourceSha256(
            source = sourceA,
            length = 40,
            includeDisplayName = true
        )
        val idB = SourceDocumentIds.fromSourceSha256(
            source = sourceB,
            length = 40,
            includeDisplayName = true
        )

        assertNotEquals(idA, idB)
    }

    private fun fakeSource(
        uri: String,
        sourceDisplayName: String?
    ): DocumentSource {
        return object : DocumentSource {
            override val uri: Uri = Uri.parse(uri)
            override val displayName: String? = sourceDisplayName
            override val mimeType: String? = "text/plain"
            override val sizeBytes: Long? = 123L

            override suspend fun openInputStream(): InputStream {
                return ByteArrayInputStream(ByteArray(0))
            }

            override suspend fun openFileDescriptor(mode: String): ParcelFileDescriptor? {
                return null
            }
        }
    }
}
