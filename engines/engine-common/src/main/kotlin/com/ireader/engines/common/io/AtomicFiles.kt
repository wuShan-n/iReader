package com.ireader.engines.common.io

import java.io.File
import java.io.IOException

fun prepareTempFile(file: File) {
    file.parentFile?.mkdirs()
    if (file.exists() && !file.delete()) {
        throw IOException("Failed to delete stale temp file: ${file.absolutePath}")
    }
}

fun replaceFileAtomically(
    tempFile: File,
    targetFile: File,
    rename: (File, File) -> Boolean = { src, dst -> src.renameTo(dst) }
) {
    targetFile.parentFile?.mkdirs()
    val backupFile = File(
        targetFile.parentFile ?: targetFile.absoluteFile.parentFile,
        "${targetFile.name}.bak"
    )

    if (backupFile.exists() && !backupFile.delete()) {
        throw IOException("Failed to clear backup file: ${backupFile.absolutePath}")
    }

    var movedTargetToBackup = false
    if (targetFile.exists()) {
        movedTargetToBackup = moveReplacing(src = targetFile, dst = backupFile, rename = rename)
    }

    try {
        if (moveReplacing(src = tempFile, dst = targetFile, rename = rename)) {
            cleanupFile(backupFile)
            return
        }
        throw IOException("Failed to replace target file atomically: ${targetFile.absolutePath}")
    } catch (error: Throwable) {
        if (movedTargetToBackup) {
            runCatching {
                if (targetFile.exists() && !targetFile.delete()) {
                    targetFile.deleteOnExit()
                }
                moveReplacing(src = backupFile, dst = targetFile, rename = rename)
            }
        }
        throw error
    } finally {
        cleanupFile(tempFile)
    }
}

private fun moveReplacing(
    src: File,
    dst: File,
    rename: (File, File) -> Boolean
): Boolean {
    if (rename(src, dst)) {
        return true
    }
    if (!src.exists()) {
        return false
    }
    src.copyTo(dst, overwrite = true)
    if (src.exists() && !src.delete()) {
        src.deleteOnExit()
    }
    return true
}

private fun cleanupFile(file: File) {
    if (file.exists() && !file.delete()) {
        file.deleteOnExit()
    }
}
