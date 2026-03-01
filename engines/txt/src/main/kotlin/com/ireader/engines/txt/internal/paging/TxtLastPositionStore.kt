package com.ireader.engines.txt.internal.paging

import com.ireader.engines.txt.TxtEngineConfig
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class TxtLastPositionStore(
    private val config: TxtEngineConfig,
    private val docNamespace: String,
    private val charsetName: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    val minIntervalMs: Long = config.lastPositionMinIntervalMs.coerceAtLeast(0L)

    suspend fun load(key: RenderKey): Int? = withContext(ioDispatcher) {
        if (!config.persistLastPosition) return@withContext null
        val file = fileFor(key) ?: return@withContext null
        if (!file.exists()) return@withContext null
        runCatching { decodeVarInt(file.readBytes()) }.getOrNull()
    }

    fun save(key: RenderKey, startChar: Int, force: Boolean = false) {
        if (!config.persistLastPosition) return
        val file = fileFor(key) ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeBytes(encodeVarInt(startChar.coerceAtLeast(0)))
            if (file.exists()) file.delete()
            val renamed = tmp.renameTo(file)
            if (!renamed) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }

    private fun fileFor(key: RenderKey): File? {
        val base = config.cacheDir ?: return null
        val folder = File(
            base,
            "reader-txt/lastpos/${docNamespace.hashCode()}_${charsetName.hashCode()}"
        )
        return File(folder, "${KeyHash.stableName(key)}.pos")
    }

    private fun encodeVarInt(value: Int): ByteArray {
        var v = value
        val out = ArrayList<Byte>(5)
        while (true) {
            val b = v and 0x7F
            v = v ushr 7
            if (v == 0) {
                out.add(b.toByte())
                break
            } else {
                out.add((b or 0x80).toByte())
            }
        }
        return out.toByteArray()
    }

    private fun decodeVarInt(bytes: ByteArray): Int {
        var result = 0
        var shift = 0
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            i += 1
            if ((b and 0x80) == 0) break
            shift += 7
        }
        return result
    }
}
