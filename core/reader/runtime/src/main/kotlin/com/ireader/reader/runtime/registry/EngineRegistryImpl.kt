package com.ireader.reader.runtime.registry

import com.ireader.reader.api.engine.EngineRegistry
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.model.BookFormat

class EngineRegistryImpl(
    engines: Set<ReaderEngine>
) : EngineRegistry {

    private val byFormat: Map<BookFormat, ReaderEngine> = buildFormatMap(engines)

    override fun engineFor(format: BookFormat): ReaderEngine? = byFormat[format]

    private fun buildFormatMap(engines: Set<ReaderEngine>): Map<BookFormat, ReaderEngine> {
        val map = mutableMapOf<BookFormat, ReaderEngine>()
        val duplicates = mutableMapOf<BookFormat, MutableList<String>>()

        for (engine in engines) {
            for (format in engine.supportedFormats) {
                val existing = map[format]
                if (existing == null) {
                    map[format] = engine
                } else {
                    duplicates.getOrPut(format) { mutableListOf() }
                        .add(existing::class.qualifiedName.orEmpty())
                    duplicates.getOrPut(format) { mutableListOf() }
                        .add(engine::class.qualifiedName.orEmpty())
                }
            }
        }

        require(duplicates.isEmpty()) {
            "Multiple engines registered for same format: $duplicates"
        }

        return map.toMap()
    }
}


