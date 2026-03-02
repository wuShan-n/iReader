package com.ireader.engines.txt.internal.paging

import com.ireader.engines.txt.TxtEngineConfig
import com.ireader.reader.model.Locator
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Properties
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

    suspend fun load(key: RenderKey): Locator? = withContext(ioDispatcher) {
        if (!config.persistLastPosition) return@withContext null
        val file = fileFor(key) ?: return@withContext null
        if (!file.exists()) return@withContext null
        runCatching { decodeLocator(file.readBytes()) }.getOrNull()
    }

    fun save(key: RenderKey, locator: Locator) {
        if (!config.persistLastPosition) return
        val file = fileFor(key) ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeBytes(encodeLocator(locator))
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
            "reader-txt-v2/lastpos/${docNamespace.hashCode()}_${charsetName.hashCode()}"
        )
        return File(folder, "${KeyHash.stableName(key)}.pos")
    }

    private fun encodeLocator(locator: Locator): ByteArray {
        val properties = Properties().apply {
            setProperty("scheme", locator.scheme)
            setProperty("value", locator.value)
            setProperty("extras.size", locator.extras.size.toString())
            locator.extras.entries.forEachIndexed { index, entry ->
                setProperty("extras.$index.key", entry.key)
                setProperty("extras.$index.value", entry.value)
            }
        }
        val out = ByteArrayOutputStream()
        properties.store(out, null)
        return out.toByteArray()
    }

    private fun decodeLocator(bytes: ByteArray): Locator? {
        val properties = Properties()
        ByteArrayInputStream(bytes).use { input ->
            properties.load(input)
        }
        val scheme = properties.getProperty("scheme") ?: return null
        val value = properties.getProperty("value") ?: return null
        val size = properties.getProperty("extras.size")?.toIntOrNull() ?: 0
        val extras = buildMap<String, String> {
            for (index in 0 until size) {
                val key = properties.getProperty("extras.$index.key") ?: continue
                val entryValue = properties.getProperty("extras.$index.value") ?: continue
                put(key, entryValue)
            }
        }
        return Locator(scheme = scheme, value = value, extras = extras)
    }
}
