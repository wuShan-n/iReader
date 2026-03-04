package com.ireader.core.files.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class UriPermissionResult(
    val granted: Boolean,
    val code: String? = null,
    val message: String? = null
)

@Singleton
class UriPermissionStore @Inject constructor(
    @ApplicationContext private val context: Context
) : UriPermissionGateway {
    override fun takePersistableRead(uri: Uri): UriPermissionResult {
        if (uri.scheme != "content") {
            return UriPermissionResult(granted = true)
        }
        if (hasPersistedRead(uri)) {
            return UriPermissionResult(granted = true)
        }

        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        return runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
            if (hasPersistedRead(uri)) {
                UriPermissionResult(granted = true)
            } else {
                UriPermissionResult(
                    granted = false,
                    code = "PERMISSION_NOT_PERSISTED",
                    message = "Persistable read permission was not retained for $uri"
                )
            }
        }.getOrElse { throwable ->
            val code = when (throwable) {
                is SecurityException -> "PERMISSION_DENIED"
                is IllegalArgumentException -> "INVALID_URI"
                else -> "PERMISSION_ERROR"
            }
            UriPermissionResult(
                granted = false,
                code = code,
                message = throwable.message ?: code
            )
        }
    }

    override fun hasPersistedRead(uri: Uri): Boolean {
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }
}
