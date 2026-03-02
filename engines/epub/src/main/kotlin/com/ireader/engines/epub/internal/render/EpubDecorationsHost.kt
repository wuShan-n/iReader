package com.ireader.engines.epub.internal.render

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration

internal class EpubDecorationsHost(
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
    private var navigator: DecorableNavigator? = null
    private val pending: MutableMap<String, List<Decoration>> = linkedMapOf()

    suspend fun bind(navigator: DecorableNavigator) {
        this.navigator = navigator
        val snapshot = pending.toMap()
        pending.clear()
        withContext(mainDispatcher) {
            snapshot.forEach { (group, decorations) ->
                navigator.applyDecorations(decorations, group)
            }
        }
    }

    fun unbind() {
        navigator = null
    }

    suspend fun apply(group: String, decorations: List<Decoration>) {
        val current = navigator
        if (current == null) {
            pending[group] = decorations
            return
        }

        withContext(mainDispatcher) {
            current.applyDecorations(decorations, group)
        }
    }
}
