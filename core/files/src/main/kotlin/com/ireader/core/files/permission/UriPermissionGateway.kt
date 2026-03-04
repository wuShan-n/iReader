package com.ireader.core.files.permission

import android.net.Uri

interface UriPermissionGateway {
    fun takePersistableRead(uri: Uri): UriPermissionResult
    fun hasPersistedRead(uri: Uri): Boolean
}
