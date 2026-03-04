package com.ireader.reader.runtime

import com.ireader.reader.api.engine.ReaderDocument
import com.ireader.reader.api.engine.ReaderSession
import java.io.Closeable

class ReaderSessionHandle(
    val document: ReaderDocument,
    val session: ReaderSession
) : Closeable {

    // 方便 feature 直接拿 controller/providers
    val controller = session.controller
    val outline = session.outline
    val search = session.search
    val text = session.text
    val annotations = session.annotations
    val resources = session.resources
    val selection = session.selection
    val selectionController = session.selectionController

    override fun close() {
        // 逆序关闭更安全
        runCatching { session.close() }
        runCatching { document.close() }
    }
}

