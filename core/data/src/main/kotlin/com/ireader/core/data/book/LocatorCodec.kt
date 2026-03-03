package com.ireader.core.data.book

import com.ireader.reader.model.Locator

interface LocatorCodec {
    fun encode(locator: Locator): String
    fun decode(raw: String): Locator?
}

