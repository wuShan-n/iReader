package com.ireader.engines.txt.internal.open

import android.net.Uri
import android.os.ParcelFileDescriptor
import com.ireader.core.files.source.DocumentSource
import com.ireader.reader.api.open.OpenOptions
import java.io.InputStream
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TxtOpenerFailureTest {

    @Test
    fun `open should rethrow cancellation exception`() = runBlocking {
        val cacheDir = Files.createTempDirectory("txt_open_cancel").toFile()
        val opener = TxtOpener(
            cacheDir = cacheDir,
            ioDispatcher = Dispatchers.IO
        )
        val source = object : DocumentSource {
            override val uri: Uri = Uri.parse("file:///books/cancel.txt")
            override val displayName: String? = "cancel.txt"
            override val mimeType: String? = "text/plain"
            override val sizeBytes: Long? = null

            override suspend fun openInputStream(): InputStream {
                throw CancellationException("cancelled by test")
            }

            override suspend fun openFileDescriptor(mode: String): ParcelFileDescriptor? = null
        }

        try {
            opener.open(source, OpenOptions())
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            // expected
        } finally {
            cacheDir.deleteRecursively()
        }
    }
}
