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
        val version = extractIntField(raw, "version") ?: 1
        if (version != 1) {
            return emptyMap()
        }
        val storedSampleHash = extractStringField(raw, "sampleHash").orEmpty().trim()
        if (storedSampleHash.isNotBlank() && storedSampleHash != sampleHash) {
            return emptyMap()
        }
        val patchesBody = extractObjectBody(raw, "patches") ?: return emptyMap()
        if (patchesBody.isBlank()) {
            return emptyMap()
        }
        return buildMap {
            parseStringMap(patchesBody).forEach { (key, value) ->
                val offset = key.toLongOrNull() ?: return@forEach
                val state = runCatching { BreakMapState.valueOf(value) }.getOrNull()
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
                "\"${escape(offset.toString())}\":\"${escape(state.name)}\""
            }
        return buildString {
            append('{')
            append("\"version\":1,")
            append("\"sampleHash\":\"")
            append(escape(sampleHash))
            append("\",")
            append("\"patches\":{")
            append(entries)
            append("}}")
        }
    }

    private fun tempFile(): File {
        return File(files.bookDir, "break.patch.tmp")
    }

    private fun extractIntField(raw: String, key: String): Int? {
        val valueRange = locateFieldValue(raw, key) ?: return null
        val token = raw.substring(valueRange.first, valueRange.last + 1).trim()
        return token.toIntOrNull()
    }

    private fun extractStringField(raw: String, key: String): String? {
        val valueRange = locateFieldValue(raw, key) ?: return null
        val start = valueRange.first
        if (start > valueRange.last || raw[start] != '"') {
            return null
        }
        val parsed = readJsonString(raw, start) ?: return null
        return parsed.first
    }

    private fun extractObjectBody(raw: String, key: String): String? {
        val valueRange = locateFieldValue(raw, key) ?: return null
        val start = valueRange.first
        if (start > valueRange.last || raw[start] != '{') {
            return null
        }
        val end = findMatchingBrace(raw, start) ?: return null
        if (end > valueRange.last) {
            return null
        }
        return raw.substring(start + 1, end)
    }

    private fun locateFieldValue(raw: String, key: String): IntRange? {
        val keyToken = "\"$key\""
        var searchFrom = 0
        while (true) {
            val keyIndex = raw.indexOf(keyToken, startIndex = searchFrom)
            if (keyIndex < 0) {
                return null
            }
            var index = skipWhitespace(raw, keyIndex + keyToken.length)
            if (index >= raw.length || raw[index] != ':') {
                searchFrom = keyIndex + keyToken.length
                continue
            }
            index = skipWhitespace(raw, index + 1)
            if (index >= raw.length) {
                return null
            }
            val end = findValueEnd(raw, index) ?: return null
            return index..end
        }
    }

    private fun parseStringMap(raw: String): Map<String, String> {
        if (raw.isBlank()) {
            return emptyMap()
        }
        val result = LinkedHashMap<String, String>()
        var index = 0
        while (index < raw.length) {
            index = skipWhitespaceAndCommas(raw, index)
            if (index >= raw.length) {
                break
            }
            if (raw[index] != '"') {
                return emptyMap()
            }
            val key = readJsonString(raw, index) ?: return emptyMap()
            index = skipWhitespace(raw, key.second)
            if (index >= raw.length || raw[index] != ':') {
                return emptyMap()
            }
            index = skipWhitespace(raw, index + 1)
            if (index >= raw.length || raw[index] != '"') {
                return emptyMap()
            }
            val value = readJsonString(raw, index) ?: return emptyMap()
            result[key.first] = value.first
            index = value.second
            index = skipWhitespaceAndCommas(raw, index)
        }
        return result
    }

    private fun findValueEnd(raw: String, start: Int): Int? {
        return when (raw[start]) {
            '"' -> readJsonString(raw, start)?.second?.minus(1)
            '{' -> findMatchingBrace(raw, start)
            else -> {
                var index = start
                while (index < raw.length && raw[index] != ',' && raw[index] != '}') {
                    index++
                }
                (index - 1).takeIf { it >= start }
            }
        }
    }

    private fun findMatchingBrace(raw: String, start: Int): Int? {
        var depth = 0
        var index = start
        while (index < raw.length) {
            when (val char = raw[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return index
                    }
                }
                '"' -> {
                    val parsed = readJsonString(raw, index) ?: return null
                    index = parsed.second - 1
                }
            }
            index++
        }
        return null
    }

    private fun readJsonString(raw: String, start: Int): Pair<String, Int>? {
        if (start >= raw.length || raw[start] != '"') {
            return null
        }
        val out = StringBuilder()
        var index = start + 1
        while (index < raw.length) {
            when (val char = raw[index]) {
                '"' -> return out.toString() to (index + 1)
                '\\' -> {
                    if (index + 1 >= raw.length) {
                        return null
                    }
                    when (val escaped = raw[index + 1]) {
                        '"', '\\', '/' -> out.append(escaped)
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            if (index + 5 >= raw.length) {
                                return null
                            }
                            val codePoint = raw.substring(index + 2, index + 6).toIntOrNull(16)
                                ?: return null
                            out.append(codePoint.toChar())
                            index += 4
                        }
                        else -> return null
                    }
                    index += 2
                    continue
                }
                else -> out.append(char)
            }
            index++
        }
        return null
    }

    private fun skipWhitespace(raw: String, start: Int): Int {
        var index = start
        while (index < raw.length && raw[index].isWhitespace()) {
            index++
        }
        return index
    }

    private fun skipWhitespaceAndCommas(raw: String, start: Int): Int {
        var index = start
        while (index < raw.length && (raw[index].isWhitespace() || raw[index] == ',')) {
            index++
        }
        return index
    }

    private fun escape(value: String): String {
        return buildString(value.length + 8) {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
    }
}
