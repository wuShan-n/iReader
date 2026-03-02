package com.ireader.reader.api.engine

import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.model.SessionId
import java.io.Closeable

interface ReaderSession : Closeable {
    val id: SessionId

    /**
     * 核心控制器：导航 + 渲染 + 状态流
     */
    val controller: ReaderController

    /**
     * 可选能力（按 capabilities 决定是否为 null）
     */
    val outline: OutlineProvider?
    val search: SearchProvider?
    val text: TextProvider?
    val annotations: AnnotationProvider?
    val resources: ResourceProvider? // EPUB 常用：给 WebView/资源加载器
    val selection: SelectionProvider?
}
