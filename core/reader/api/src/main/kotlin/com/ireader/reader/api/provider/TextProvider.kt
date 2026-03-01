package com.ireader.reader.api.provider

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange

interface TextProvider {
    suspend fun getText(range: LocatorRange): ReaderResult<String>
    suspend fun getTextAround(locator: Locator, maxChars: Int = 512): ReaderResult<String>
}


