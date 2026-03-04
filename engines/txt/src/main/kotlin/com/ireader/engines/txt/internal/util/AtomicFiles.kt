package com.ireader.engines.txt.internal.util

import java.io.File
import java.io.IOException

internal fun prepareTempFile(file: File) {
    file.parentFile?.mkdirs()
    if (file.exists() && !file.delete()) {
        throw IOException("Failed to delete stale temp file: ${file.absolutePath}")
    }
}

internal fun replaceFileAtomically(
    tempFile: File,
    targetFile: File,
    rename: (File, File) -> Boolean = { src, dst -> src.renameTo(dst) }
) {
    targetFile.parentFile?.mkdirs()
    if (targetFile.exists() && !targetFile.delete()) {
        throw IOException("Failed to delete target file: ${targetFile.absolutePath}")
    }
    if (rename(tempFile, targetFile)) {
        return
    }
    tempFile.copyTo(targetFile, overwrite = true)
    if (tempFile.exists() && !tempFile.delete()) {
        tempFile.deleteOnExit()
    }
}
