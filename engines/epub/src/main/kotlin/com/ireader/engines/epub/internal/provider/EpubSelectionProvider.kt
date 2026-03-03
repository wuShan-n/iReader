package com.ireader.engines.epub.internal.provider

import com.ireader.engines.epub.internal.locator.toAppLocator
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.model.NormalizedRect
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFragment

internal class EpubSelectionProvider(
    private val navigatorProvider: () -> EpubNavigatorFragment?,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : SelectionProvider {

    override suspend fun currentSelection(): ReaderResult<SelectionProvider.Selection?> {
        return withContext(mainDispatcher) {
            try {
                val navigator = navigatorProvider() ?: return@withContext ReaderResult.Ok(null)
                val selection = navigator.currentSelection() ?: return@withContext ReaderResult.Ok(null)
                val readiumLocator = selection.locator
                val locator = readiumLocator.toAppLocator()

                val view = navigator.view
                val rect = selection.rect
                val bounds = if (view != null && rect != null && view.width > 0 && view.height > 0) {
                    NormalizedRect(
                        left = (rect.left / view.width).coerceIn(0f, 1f),
                        top = (rect.top / view.height).coerceIn(0f, 1f),
                        right = (rect.right / view.width).coerceIn(0f, 1f),
                        bottom = (rect.bottom / view.height).coerceIn(0f, 1f)
                    )
                } else {
                    null
                }
                val text = readiumLocator.text.highlight?.takeIf { it.isNotBlank() }
                val rects = listOfNotNull(bounds)
                val fragment = readiumLocator.locations.fragments.firstOrNull()

                ReaderResult.Ok(
                    SelectionProvider.Selection(
                        locator = locator,
                        bounds = bounds,
                        start = locator,
                        end = locator,
                        selectedText = text,
                        rects = rects,
                        extras = buildMap {
                            fragment?.let { put("fragment", it) }
                        }
                    )
                )
            } catch (t: Throwable) {
                ReaderResult.Err(ReaderError.Internal(cause = t))
            }
        }
    }

    override suspend fun clearSelection(): ReaderResult<Unit> {
        return withContext(mainDispatcher) {
            try {
                navigatorProvider()?.clearSelection()
                ReaderResult.Ok(Unit)
            } catch (t: Throwable) {
                ReaderResult.Err(ReaderError.Internal(cause = t))
            }
        }
    }
}
