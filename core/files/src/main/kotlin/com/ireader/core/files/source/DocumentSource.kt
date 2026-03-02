package com.ireader.core.files.source

import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.InputStream

interface DocumentSource {
    val uri: Uri
    val displayName: String?
    val mimeType: String?
    val sizeBytes: Long?

    suspend fun openInputStream(): InputStream
    suspend fun openFileDescriptor(mode: String = "r"): ParcelFileDescriptor?
}