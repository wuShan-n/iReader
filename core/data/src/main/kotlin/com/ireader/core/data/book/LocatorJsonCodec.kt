package com.ireader.core.data.book

import com.ireader.reader.model.Locator
import org.json.JSONObject

object LocatorJsonCodec {
    fun encode(locator: Locator): String {
        val extras = JSONObject().apply {
            locator.extras.forEach { (key, value) -> put(key, value) }
        }

        return JSONObject()
            .put("scheme", locator.scheme)
            .put("value", locator.value)
            .put("extras", extras)
            .toString()
    }

    fun decode(json: String): Locator? {
        return runCatching {
            val root = JSONObject(json)
            val scheme = root.optString("scheme").trim()
            val value = root.optString("value").trim()
            if (scheme.isEmpty() || value.isEmpty()) {
                return null
            }

            val extrasObject = root.optJSONObject("extras") ?: JSONObject()
            val extras = buildMap {
                val iterator = extrasObject.keys()
                while (iterator.hasNext()) {
                    val key = iterator.next()
                    put(key, extrasObject.optString(key))
                }
            }

            Locator(
                scheme = scheme,
                value = value,
                extras = extras
            )
        }.getOrNull()
    }
}
