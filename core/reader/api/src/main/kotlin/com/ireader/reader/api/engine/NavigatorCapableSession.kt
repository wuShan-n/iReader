package com.ireader.reader.api.engine

import com.ireader.reader.api.render.ReaderNavigatorAdapter

/**
 * Session exposing a native navigator host instead of RenderPage-based output.
 */
interface NavigatorCapableSession : ReaderSession {
    val navigatorAdapter: ReaderNavigatorAdapter
}
