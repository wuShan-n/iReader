package com.ireader.engines.epub.internal.render

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration

internal class EpubDecorationsHost(
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
    private val lock = Any()

    private var navigator: DecorableNavigator? = null
    private var generation: Long = 0L

    private val pending: MutableMap<String, List<Decoration>> = linkedMapOf()

    suspend fun bind(navigator: DecorableNavigator) {
        val snapshot: Map<String, List<Decoration>>
        synchronized(lock) {
            this.navigator = navigator
            generation++
            snapshot = pending.toMap()
            pending.clear()
        }

        withContext(mainDispatcher) {
            snapshot.forEach { (group, decorations) ->
                navigator.applyDecorations(decorations, group)
            }
        }
    }

    fun unbind() {
        synchronized(lock) {
            navigator = null
            generation++
        }
    }

    suspend fun apply(group: String, decorations: List<Decoration>) {
        applyAll(mapOf(group to decorations))
    }

    /**
     * 批量应用，避免大量 group 更新时重复切主线程。
     */
    suspend fun applyAll(updates: Map<String, List<Decoration>>) {
        val current: DecorableNavigator?
        val currentGen: Long
        synchronized(lock) {
            current = navigator
            currentGen = generation
            if (current == null) {
                // 未绑定时只更新 pending：空列表视为删除
                updates.forEach { (group, decorations) ->
                    if (decorations.isEmpty()) pending.remove(group) else pending[group] = decorations
                }
                return
            }
        }

        withContext(mainDispatcher) {
            // 再次确认 navigator 未发生变化（避免 unbind 后还 apply 到旧 navigator）
            val stillValid = synchronized(lock) { generation == currentGen && navigator === current }
            if (!stillValid || current == null) {
                synchronized(lock) {
                    updates.forEach { (group, decorations) ->
                        if (decorations.isEmpty()) pending.remove(group) else pending[group] = decorations
                    }
                }
                return@withContext
            }

            updates.forEach { (group, decorations) ->
                current.applyDecorations(decorations, group)
            }
        }
    }
}
