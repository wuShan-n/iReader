package com.ireader.feature.reader.domain

import com.ireader.reader.model.Locator

interface LocatorCodec {
    fun encode(locator: Locator): String
    fun decode(raw: String): Locator?
}

