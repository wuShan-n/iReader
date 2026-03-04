package com.ireader.engines.common.android.reflow

import com.ireader.engines.common.android.pagination.ReflowPaginationProfile
import com.ireader.engines.common.pagination.PageMap
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.LocatorExtraKeys
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

    fun locatorExtras(maxAnchors: Int = 96): Map<String, String> {
        if (!hasActiveProfile()) return emptyMap()
        val profile = profileKey ?: return emptyMap()
        val anchors = sampledAnchors(maxAnchors = maxAnchors)
        if (anchors.isEmpty()) {
            return mapOf(LocatorExtraKeys.REFLOW_PAGE_PROFILE to profile)
        }
        return mapOf(
            LocatorExtraKeys.REFLOW_PAGE_PROFILE to profile,
            LocatorExtraKeys.REFLOW_PAGE_ANCHORS to anchors.joinToString(separator = ",")
        )
    }

    fun mergeLocatorAnchors(locatorExtras: Map<String, String>) {
        if (!hasActiveProfile()) return
        val profile = locatorExtras[LocatorExtraKeys.REFLOW_PAGE_PROFILE] ?: return
        if (profile != profileKey) return
        val serialized = locatorExtras[LocatorExtraKeys.REFLOW_PAGE_ANCHORS] ?: return
        val parsed = serialized
            .split(',')
            .asSequence()
            .map { it.trim() }
            .mapNotNull { it.toLongOrNull() }
            .filter { it >= 0L }
            .toList()
        if (parsed.isEmpty()) return
        val before = pageStarts.size
        pageStarts.addAll(parsed)
        if (pageStarts.size != before) {
            dirty = true
        }
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

    private fun sampledAnchors(maxAnchors: Int): List<Long> {
        if (pageStarts.isEmpty()) return emptyList()
        val target = maxAnchors.coerceAtLeast(1)
        if (pageStarts.size <= target) return pageStarts.toList()
        if (target == 1) return listOf(pageStarts.first())

        val values = pageStarts.toList()
        val step = (values.size - 1).toDouble() / (target - 1).toDouble()
        return buildList(target) {
            for (i in 0 until target) {
                val idx = (i * step).roundToInt().coerceIn(0, values.lastIndex)
                add(values[idx])
            }
        }.distinct()
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
