package com.ireader.engines.txt.internal.paging

import com.ireader.engines.txt.TxtEngineConfig
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal class TxtPaginationStore(
    private val config: TxtEngineConfig,
    private val docNamespace: String,
    private val charsetName: String
) {
    private data class Bucket(
        val index: PageStartsIndex,
        var newStartsSinceLastWrite: Int = 0
    )

    private val buckets = ConcurrentHashMap<RenderKey, Bucket>()

    fun getOrCreate(key: RenderKey): PageStartsIndex {
        return buckets.getOrPut(key) {
            val index = PageStartsIndex().apply { seedIfEmpty(0) }
            if (config.persistPagination) {
                loadFromDisk(key)?.let { restored -> index.mergeFrom(restored) }
            }
            Bucket(index)
        }.index
    }

    fun maybePersist(key: RenderKey, index: PageStartsIndex, newAdds: Int) {
        if (!config.persistPagination) return
        if (newAdds <= 0) return
        val bucket = buckets[key] ?: return
        val shouldPersist = synchronized(bucket) {
            bucket.newStartsSinceLastWrite += newAdds
            val threshold = config.paginationWriteEveryNewStarts.coerceAtLeast(1)
            if (bucket.newStartsSinceLastWrite >= threshold) {
                bucket.newStartsSinceLastWrite = 0
                true
            } else {
                false
            }
        }
        if (shouldPersist) {
            saveToDisk(key, index)
        }
    }

    fun flush(key: RenderKey, index: PageStartsIndex) {
        if (!config.persistPagination) return
        saveToDisk(key, index)
        buckets[key]?.let { bucket ->
            synchronized(bucket) {
                bucket.newStartsSinceLastWrite = 0
            }
        }
    }

    private fun loadFromDisk(key: RenderKey): IntArray? {
        val file = fileFor(key) ?: return null
        if (!file.exists()) return null
        return runCatching {
            PageStartsCodec.decode(file.readBytes())
        }.getOrNull()
    }

    private fun saveToDisk(key: RenderKey, index: PageStartsIndex) {
        val file = fileFor(key) ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeBytes(PageStartsCodec.encode(index.snapshot()))
            if (file.exists()) file.delete()
            val renamed = tmp.renameTo(file)
            if (!renamed) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }

    private fun fileFor(key: RenderKey): File? {
        val baseDir = config.cacheDir ?: return null
        val folder = File(
            baseDir,
            "reader-txt/pagination/${docNamespace.hashCode()}_${charsetName.hashCode()}"
        )
        return File(folder, "${KeyHash.stableName(key)}.bin")
    }
}
