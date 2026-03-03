package com.ireader.feature.reader.domain.impl

import com.ireader.core.data.book.LocatorJsonCodec
import com.ireader.feature.reader.domain.LocatorCodec
import com.ireader.reader.model.Locator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsonLocatorCodec @Inject constructor() : LocatorCodec {
    override fun encode(locator: Locator): String = LocatorJsonCodec.encode(locator)

    override fun decode(raw: String): Locator? = LocatorJsonCodec.decode(raw)
}

