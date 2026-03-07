package com.ireader.engines.txt.internal.open

import java.io.File
import org.json.JSONObject

internal data class TxtArtifactManifest(
    val version: Int,
    val sampleHash: String,
    val contentRevision: Int,
    val blockIndexVersion: Int?,
    val breakMapVersion: Int?,
    val blockIndexReady: Boolean,
    val breakMapReady: Boolean
) {
    fun matches(meta: TxtMeta): Boolean {
        return sampleHash == meta.sampleHash && contentRevision == meta.contentRevision
    }

    fun markBlockIndexReady(version: Int): TxtArtifactManifest {
        return copy(blockIndexVersion = version, blockIndexReady = true)
    }

    fun markBreakMapReady(version: Int): TxtArtifactManifest {
        return copy(breakMapVersion = version, breakMapReady = true)
    }

    fun resetDerivedArtifacts(): TxtArtifactManifest {
        return copy(
            blockIndexVersion = null,
            breakMapVersion = null,
            blockIndexReady = false,
            breakMapReady = false
        )
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("version", version)
            put("sampleHash", sampleHash)
            put("contentRevision", contentRevision)
            if (blockIndexVersion != null) {
                put("blockIndexVersion", blockIndexVersion)
            }
            if (breakMapVersion != null) {
                put("breakMapVersion", breakMapVersion)
            }
            put("blockIndexReady", blockIndexReady)
            put("breakMapReady", breakMapReady)
        }
    }

    companion object {
        const val VERSION = 1

        fun initial(meta: TxtMeta): TxtArtifactManifest {
            return TxtArtifactManifest(
                version = VERSION,
                sampleHash = meta.sampleHash,
                contentRevision = meta.contentRevision,
                blockIndexVersion = null,
                breakMapVersion = null,
                blockIndexReady = false,
                breakMapReady = false
            )
        }

        fun readIfValid(file: File, meta: TxtMeta): TxtArtifactManifest? {
            if (!file.exists()) {
                return null
            }
            val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return null
            val manifest = runCatching {
                TxtArtifactManifest(
                    version = json.getInt("version"),
                    sampleHash = json.getString("sampleHash"),
                    contentRevision = json.optInt("contentRevision", meta.contentRevision),
                    blockIndexVersion = if (json.has("blockIndexVersion")) json.getInt("blockIndexVersion") else null,
                    breakMapVersion = if (json.has("breakMapVersion")) json.getInt("breakMapVersion") else null,
                    blockIndexReady = json.optBoolean("blockIndexReady", false),
                    breakMapReady = json.optBoolean("breakMapReady", false)
                )
            }.getOrNull() ?: return null
            if (manifest.version != VERSION || !manifest.matches(meta)) {
                return null
            }
            return manifest
        }
    }
}
