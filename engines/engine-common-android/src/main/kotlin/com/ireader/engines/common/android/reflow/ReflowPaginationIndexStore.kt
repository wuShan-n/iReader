package com.ireader.engines.common.android.reflow

import com.ireader.engines.common.android.pagination.ReflowPaginationProfile
import com.ireader.engines.common.pagination.PageMap
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import java.io.File
import java.util.TreeSet
import kotlin.math.roundToInt

class ReflowPaginationIndexStore(
    private val enabled: Boolean,
    private val documentKey: String,
    private val paginationDir: File
) {
    private var profileKey: String? = null
    private var pageStarts = sortedSetOf<Long>()
    private var dirty = false

    fun hasActiveProfile(): Boolean {
        return enabled && !profileKey.isNullOrBlank()
    }

    fun knownPageCount(): Int = pageStarts.size

    fun lastKnownStart(): Long? = pageStarts.lastOrNull()

    fun startForProgress(percent: Double): Long? {
        if (pageStarts.size < 8) return null
        val index = ((pageStarts.size - 1) * percent.coerceIn(0.0, 1.0))
            .roundToInt()
            .coerceIn(0, pageStarts.size - 1)
        return pageStarts.elementAt(index)
    }

    fun reloadIfNeeded(
        constraints: LayoutConstraints?,
        profileConfig: RenderConfig.ReflowText
    ) {
        if (!enabled) {
            clearState()
            return
        }
        val currentConstraints = constraints ?: return
        val nextProfile = ReflowPaginationProfile.keyFor(
            documentKey = documentKey,
            constraints = currentConstraints,
            config = profileConfig
        )
        if (nextProfile == profileKey) {
            return
        }
        saveIfDirty()
        profileKey = nextProfile
        pageStarts = loadPageStarts(nextProfile)
        dirty = false
    }

    fun record(start: Long) {
        if (!hasActiveProfile()) {
            return
        }
        if (pageStarts.add(start.coerceAtLeast(0L))) {
            dirty = true
            if (pageStarts.size % 16 == 0) {
                saveIfDirty()
            }
        }
    }

    fun saveIfDirty() {
        if (!hasActiveProfile() || !dirty) {
            return
        }
        val key = profileKey ?: return
        val file = profileFile(key)
        file.parentFile?.mkdirs()
        PageMap.save(file, pageStarts)
        dirty = false
    }

    fun invalidateProfile() {
        profileKey = null
        pageStarts.clear()
        dirty = false
    }

    private fun clearState() {
        profileKey = null
        pageStarts.clear()
        dirty = false
    }

    private fun loadPageStarts(profile: String): TreeSet<Long> {
        return PageMap.load(
            binaryFile = profileFile(profile),
            legacyTextFile = legacyProfileFile(profile)
        )
    }

    private fun profileFile(profile: String): File {
        return File(paginationDir, "pagemap_v2_$profile.bin")
    }

    private fun legacyProfileFile(profile: String): File {
        return File(paginationDir, "pagemap_v2_$profile.txt")
    }
}

