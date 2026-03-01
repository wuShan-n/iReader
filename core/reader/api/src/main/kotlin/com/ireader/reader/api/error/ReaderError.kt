package com.ireader.reader.api.error

/**
 * api 层错误类型：保证 UI/业务可以稳定识别并做兜底。
 */
sealed class ReaderError(
    message: String? = null,
    cause: Throwable? = null,
    val code: String
) : RuntimeException(message, cause) {

    class UnsupportedFormat(
        val detected: String? = null,
        message: String? = "Unsupported format",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "UNSUPPORTED_FORMAT")

    class NotFound(
        message: String? = "File not found",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "NOT_FOUND")

    class PermissionDenied(
        message: String? = "Permission denied",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "PERMISSION_DENIED")

    class InvalidPassword(
        message: String? = "Invalid password",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "INVALID_PASSWORD")

    class CorruptOrInvalid(
        message: String? = "Corrupt or invalid document",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "CORRUPT_OR_INVALID")

    class DrmRestricted(
        message: String? = "DRM restricted",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "DRM_RESTRICTED")

    class Io(
        message: String? = "I/O error",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "IO")

    class Cancelled(
        message: String? = "Cancelled",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "CANCELLED")

    class Internal(
        message: String? = "Internal error",
        cause: Throwable? = null
    ) : ReaderError(message, cause, code = "INTERNAL")
}

