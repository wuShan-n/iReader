package com.ireader.reader.api.render

import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.model.Locator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable

sealed interface ReaderEvent {
    data class PageChanged(val locator: Locator) : ReaderEvent
    data class Rendered(val pageId: PageId, val metrics: RenderMetrics?) : ReaderEvent
    data class Error(val throwable: Throwable) : ReaderEvent
}

/**
 * Session 内的“唯一总控”：UI 层只需要跟它交互即可。
 * - 负责：布局约束、配置变更、跳转/翻页、产出 RenderPage
 * - 维护：状态流/事件流
 */
interface ReaderController : Closeable {

    val state: StateFlow<RenderState>
    val events: Flow<ReaderEvent>

    /**
     * 绑定渲染承载面（可选）。
     *
     * - 导航器型引擎（如 EPUB）会在该 surface 中渲染实际内容
     * - 非导航器型引擎可返回 Ok(Unit)
     */
    suspend fun bindSurface(surface: RenderSurface): ReaderResult<Unit>

    /**
     * 解除渲染承载面绑定（可选）。
     */
    suspend fun unbindSurface(): ReaderResult<Unit>

    suspend fun setLayoutConstraints(constraints: LayoutConstraints): ReaderResult<Unit>

    suspend fun setConfig(config: RenderConfig): ReaderResult<Unit>

    /**
     * 渲染当前 locator
     */
    suspend fun render(policy: RenderPolicy = RenderPolicy.Default): ReaderResult<RenderPage>

    suspend fun next(policy: RenderPolicy = RenderPolicy.Default): ReaderResult<RenderPage>

    suspend fun prev(policy: RenderPolicy = RenderPolicy.Default): ReaderResult<RenderPage>

    suspend fun goTo(locator: Locator, policy: RenderPolicy = RenderPolicy.Default): ReaderResult<RenderPage>

    /**
     * 按进度跳转：percent 0..1
     * TXT/EPUB 可能是近似（取决于实现）。
     */
    suspend fun goToProgress(percent: Double, policy: RenderPolicy = RenderPolicy.Default): ReaderResult<RenderPage>

    /**
     * 预取（可 no-op），通常用于：翻页更顺滑 / PDF 下一页 tiles 预热
     */
    suspend fun prefetchNeighbors(count: Int = 1): ReaderResult<Unit>

    /**
     * 当标注/装饰变化时，通知引擎让下一次 render 使用最新 decorations（或内部 cache 失效）。
     */
    suspend fun invalidate(reason: InvalidateReason = InvalidateReason.CONTENT_CHANGED): ReaderResult<Unit>
}

enum class InvalidateReason {
    CONTENT_CHANGED,   // 标注/主题变化导致页面装饰变动
    CONFIG_CHANGED,    // 字体/缩放等
    LAYOUT_CHANGED     // 横竖屏/窗口变化
}


