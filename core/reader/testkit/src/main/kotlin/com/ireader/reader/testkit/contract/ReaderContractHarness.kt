package com.ireader.reader.testkit.contract

import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.TextLayouterFactory
import com.ireader.reader.model.Locator
import com.ireader.reader.runtime.ReaderHandle

interface ReaderContractHarness : AutoCloseable {
    val defaultLayout: LayoutConstraints
    val defaultLayouterFactory: TextLayouterFactory?
    val searchQuery: String
    val expectedSearchExcerpt: String?
    val expectedOutlineTitle: String?
    val selectionStartOffset: Long
    val selectionEndOffset: Long

    suspend fun openSession(): ReaderHandle

    fun locatorAt(offset: Long): Locator
}
