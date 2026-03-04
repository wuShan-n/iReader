package com.ireader.engines.epub.internal.locator

import com.ireader.reader.model.Locator
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator as ReadiumLocator

internal object ReadiumLocatorSchemes {
    const val READIUM_LOCATOR_JSON = "readium.locator.json"
}

internal fun ReadiumLocator.toAppLocator(): Locator =
    Locator(
        scheme = ReadiumLocatorSchemes.READIUM_LOCATOR_JSON,
        value = toJSON().toString(),
        extras = buildMap {
            put("href", href.toString())
            locations.progression?.let { put("progression", it.toString()) }
            locations.totalProgression?.let { put("totalProgression", it.toString()) }
            locations.position?.let { put("position", it.toString()) }
            title?.let { put("title", it) }
            locations.fragments.firstOrNull()?.let { put("fragment", it) }
        }
    )

internal fun Locator.toReadiumLocatorOrNull(): ReadiumLocator? {
    if (scheme != ReadiumLocatorSchemes.READIUM_LOCATOR_JSON) {
        return null
    }

    return runCatching {
        ReadiumLocator.fromJSON(JSONObject(value))
    }.getOrNull()
}

internal fun Locator.withReadiumFragments(fragments: List<String>): Locator {
    if (scheme != ReadiumLocatorSchemes.READIUM_LOCATOR_JSON) {
        return this
    }
    val normalized = fragments.map { it.trim() }.filter { it.isNotEmpty() }
    return runCatching {
        val root = JSONObject(value)
        val locations = root.optJSONObject("locations") ?: JSONObject().also { root.put("locations", it) }
        val fragmentArray = JSONArray()
        normalized.forEach(fragmentArray::put)
        locations.put("fragments", fragmentArray)
        copy(
            value = root.toString(),
            extras = buildMap {
                putAll(this@withReadiumFragments.extras)
                val first = normalized.firstOrNull()
                if (first == null) {
                    remove("fragment")
                } else {
                    put("fragment", first)
                }
            }
        )
    }.getOrElse { this }
}
