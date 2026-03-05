package com.ireader.engines.epub.internal.locator

import com.ireader.reader.model.Locator
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator as ReadiumLocator

internal object ReadiumLocatorSchemes {
    const val READIUM_LOCATOR_JSON = "readium.locator.json"
}

internal object ReadiumLocatorExtras {
    const val HREF = "href"
    const val PROGRESSION = "progression"
    const val TOTAL_PROGRESSION = "totalProgression"
    const val POSITION = "position"
    const val TITLE = "title"
    const val FRAGMENT = "fragment"
    const val START_FRAGMENT = "startFragment"
    const val END_FRAGMENT = "endFragment"
}

internal fun ReadiumLocator.toAppLocator(): Locator =
    Locator(
        scheme = ReadiumLocatorSchemes.READIUM_LOCATOR_JSON,
        value = toJSON().toString(),
        extras = buildMap {
            put(ReadiumLocatorExtras.HREF, href.toString())
            locations.progression?.let { put(ReadiumLocatorExtras.PROGRESSION, it.toString()) }
            locations.totalProgression?.let { put(ReadiumLocatorExtras.TOTAL_PROGRESSION, it.toString()) }
            locations.position?.let { put(ReadiumLocatorExtras.POSITION, it.toString()) }
            title?.let { put(ReadiumLocatorExtras.TITLE, it) }
            locations.fragments.firstOrNull()?.let { put(ReadiumLocatorExtras.FRAGMENT, it) }
        }
    )

internal fun Locator.toReadiumLocatorOrNull(): ReadiumLocator? {
    if (scheme != ReadiumLocatorSchemes.READIUM_LOCATOR_JSON) return null
    if (value.isBlank()) return null

    return runCatching {
        ReadiumLocator.fromJSON(JSONObject(value))
    }.getOrNull()
}

internal fun Locator.withReadiumFragments(fragments: List<String>): Locator {
    if (scheme != ReadiumLocatorSchemes.READIUM_LOCATOR_JSON) return this

    val normalized = fragments.asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()

    return runCatching {
        val root = JSONObject(value)
        val locations = root.optJSONObject("locations") ?: JSONObject().also { root.put("locations", it) }

        if (normalized.isEmpty()) {
            locations.remove("fragments")
            return@runCatching copy(
                value = root.toString(),
                extras = buildMap {
                    putAll(this@withReadiumFragments.extras)
                    remove(ReadiumLocatorExtras.FRAGMENT)
                }
            )
        }

        val fragmentArray = JSONArray().apply { normalized.forEach(::put) }
        locations.put("fragments", fragmentArray)

        copy(
            value = root.toString(),
            extras = buildMap {
                putAll(this@withReadiumFragments.extras)
                put(ReadiumLocatorExtras.FRAGMENT, normalized.first())
            }
        )
    }.getOrElse { this }
}
