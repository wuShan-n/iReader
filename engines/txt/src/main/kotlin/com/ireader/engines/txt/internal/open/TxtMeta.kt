package com.ireader.engines.txt.internal.open

import org.json.JSONObject

internal data class TxtMeta(
    val version: Int,
    val sourceUri: String,
    val displayName: String?,
    val sizeBytes: Long?,
    val sampleHash: String,
    val contentFingerprint: String = sampleHash,
    val originalCharset: String,
    val lengthChars: Long,
    val hardWrapLikely: Boolean,
    val typicalLineLength: Int,
    val createdAtEpochMs: Long,
    val lengthCodeUnits: Long = lengthChars,
    val defaultBlockSizeCodeUnits: Int = 128 * 1024,
    val contentRevision: Int = 1
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("version", version)
            put("sourceUri", sourceUri)
            put("displayName", displayName)
            if (sizeBytes != null) {
                put("sizeBytes", sizeBytes)
            }
            put("sampleHash", sampleHash)
            put("contentFingerprint", contentFingerprint)
            put("originalCharset", originalCharset)
            put("lengthChars", lengthChars)
            put("lengthCodeUnits", lengthCodeUnits)
            put("hardWrapLikely", hardWrapLikely)
            put("typicalLineLength", typicalLineLength)
            put("createdAtEpochMs", createdAtEpochMs)
            put("defaultBlockSizeCodeUnits", defaultBlockSizeCodeUnits)
            put("contentRevision", contentRevision)
        }
    }

    companion object {
        private const val DEFAULT_BLOCK_SIZE_CODE_UNITS = 128 * 1024
        private const val DEFAULT_CONTENT_REVISION = 1
        private const val DEFAULT_TYPICAL_LINE_LENGTH = 72

        fun fromJson(json: JSONObject): TxtMeta {
            val lengthChars = json.getLong("lengthChars")
            return TxtMeta(
                version = json.getInt("version"),
                sourceUri = json.getString("sourceUri"),
                displayName = json.optString("displayName").takeIf { it.isNotEmpty() },
                sizeBytes = if (json.has("sizeBytes")) json.getLong("sizeBytes") else null,
                sampleHash = json.getString("sampleHash"),
                contentFingerprint = json.optString("contentFingerprint", json.getString("sampleHash")),
                originalCharset = json.getString("originalCharset"),
                lengthChars = lengthChars,
                hardWrapLikely = json.optBoolean("hardWrapLikely", false),
                typicalLineLength = json.optInt("typicalLineLength", DEFAULT_TYPICAL_LINE_LENGTH),
                createdAtEpochMs = json.optLong("createdAtEpochMs", 0L),
                lengthCodeUnits = json.optLong("lengthCodeUnits", lengthChars),
                defaultBlockSizeCodeUnits = json.optInt(
                    "defaultBlockSizeCodeUnits",
                    DEFAULT_BLOCK_SIZE_CODE_UNITS
                ),
                contentRevision = json.optInt("contentRevision", DEFAULT_CONTENT_REVISION)
            )
        }
    }
}
