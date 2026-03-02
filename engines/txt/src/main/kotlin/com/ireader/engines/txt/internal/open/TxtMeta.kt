package com.ireader.engines.txt.internal.open

import org.json.JSONObject

internal data class TxtMeta(
    val version: Int,
    val sourceUri: String,
    val displayName: String?,
    val sizeBytes: Long?,
    val sampleHash: String,
    val originalCharset: String,
    val lengthChars: Long,
    val hardWrapLikely: Boolean,
    val createdAtEpochMs: Long
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
            put("originalCharset", originalCharset)
            put("lengthChars", lengthChars)
            put("hardWrapLikely", hardWrapLikely)
            put("createdAtEpochMs", createdAtEpochMs)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): TxtMeta {
            return TxtMeta(
                version = json.getInt("version"),
                sourceUri = json.getString("sourceUri"),
                displayName = json.optString("displayName").takeIf { it.isNotEmpty() },
                sizeBytes = if (json.has("sizeBytes")) json.getLong("sizeBytes") else null,
                sampleHash = json.getString("sampleHash"),
                originalCharset = json.getString("originalCharset"),
                lengthChars = json.getLong("lengthChars"),
                hardWrapLikely = json.optBoolean("hardWrapLikely", false),
                createdAtEpochMs = json.optLong("createdAtEpochMs", 0L)
            )
        }
    }
}
