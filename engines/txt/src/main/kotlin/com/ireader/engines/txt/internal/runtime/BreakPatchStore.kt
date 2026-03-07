package com.ireader.engines.txt.internal.runtime

import com.ireader.engines.common.io.prepareTempFile
import com.ireader.engines.common.io.replaceFileAtomically
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.softbreak.BreakMapState
import java.io.File

internal class BreakPatchStore(
    private val files: TxtBookFiles,
    private val sampleHash: String
) {
    fun read(): Map<Long, BreakMapState> {
        val file = files.breakPatch
        if (!file.exists()) {
            return emptyMap()
        }
        val raw = runCatching { file.readText() }.getOrNull()?.trim().orEmpty()
        if (raw.isBlank()) {
            return emptyMap()
        }
        val version = VERSION_REGEX.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        if (version != 1) {
            return emptyMap()
        }
        val storedSampleHash = SAMPLE_HASH_REGEX.find(raw)?.groupValues?.getOrNull(1).orEmpty()
        if (storedSampleHash.isNotBlank() && storedSampleHash != sampleHash) {
            return emptyMap()
        }
        val patchesSection = PATCHES_REGEX.find(raw)?.groupValues?.getOrNull(1).orEmpty()
        if (patchesSection.isBlank()) {
            return emptyMap()
        }
        return buildMap {
            PATCH_ENTRY_REGEX.findAll(patchesSection).forEach { match ->
                val offset = match.groupValues[1].toLongOrNull() ?: return@forEach
                val state = runCatching { BreakMapState.valueOf(match.groupValues[2]) }.getOrNull()
                    ?: return@forEach
                put(offset, state)
            }
        }
    }

    fun write(patches: Map<Long, BreakMapState>) {
        val temp = tempFile()
        prepareTempFile(temp)
        temp.writeText(encode(patches))
        replaceFileAtomically(tempFile = temp, targetFile = files.breakPatch)
    }

    fun clear() {
        runCatching { files.breakPatch.delete() }
        runCatching { tempFile().delete() }
    }

    private fun encode(patches: Map<Long, BreakMapState>): String {
        val entries = patches.entries
            .sortedBy { it.key }
            .joinToString(separator = ",") { (offset, state) ->
                "\"$offset\":\"${state.name}\""
            }
        return buildString {
            append("{\"version\":1,\"sampleHash\":\"")
            append(escape(sampleHash))
            append("\",\"patches\":{")
            append(entries)
            append("}}")
        }
    }

    private fun tempFile(): File {
        return File(files.bookDir, "break.patch.tmp")
    }

    private fun escape(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private companion object {
        private val VERSION_REGEX = Regex(""""version"\s*:\s*(\d+)""")
        private val SAMPLE_HASH_REGEX = Regex(""""sampleHash"\s*:\s*"([^"]*)"""")
        private val PATCHES_REGEX = Regex(""""patches"\s*:\s*\{(.*?)}""", setOf(RegexOption.DOT_MATCHES_ALL))
        private val PATCH_ENTRY_REGEX = Regex(""""(\d+)"\s*:\s*"([A-Z_]+)"""")
    }
}
