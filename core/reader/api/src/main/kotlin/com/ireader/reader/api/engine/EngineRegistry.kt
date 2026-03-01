package com.ireader.reader.api.engine

import com.ireader.reader.model.BookFormat

interface EngineRegistry {
    fun engineFor(format: BookFormat): ReaderEngine?
}


