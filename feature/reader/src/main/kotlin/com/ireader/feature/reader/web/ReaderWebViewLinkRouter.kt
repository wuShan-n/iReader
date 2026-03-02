package com.ireader.feature.reader.web

import android.net.Uri
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object ReaderWebViewLinkRouter {
    private val lastPages = ConcurrentHashMap<String, Int>()
    private val lastPage = ConcurrentHashMap<String, Int>()

    fun tryHandle(url: String, controller: ReaderController, scope: CoroutineScope): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        if (uri.scheme != "reader") return false

        return when (uri.host) {
            "goto" -> {
                val scheme = uri.getQueryParameter("scheme") ?: return true
                val value = uri.getQueryParameter("value") ?: return true
                scope.launch {
                    controller.goTo(Locator(scheme = scheme, value = value))
                }
                true
            }

            "metrics" -> {
                val spine = uri.getQueryParameter("spine")?.toIntOrNull() ?: return true
                val sig = uri.getQueryParameter("sig")?.toIntOrNull() ?: return true
                val pages = uri.getQueryParameter("pages")?.toIntOrNull() ?: return true
                val page = uri.getQueryParameter("page")?.toIntOrNull() ?: return true
                if (pages <= 0 || page < 0) return true

                val currentLocator = controller.state.value.locator
                val currentSpine = currentLocator.value.split(':').getOrNull(0)?.toIntOrNull()
                if (currentLocator.scheme == LocatorSchemes.REFLOW_PAGE &&
                    currentSpine != null &&
                    currentSpine != spine
                ) {
                    return true
                }

                val key = "$spine:$sig"
                val prevPages = lastPages[key]
                val prevPage = lastPage[key]
                if (prevPages == pages && prevPage == page) {
                    return true
                }
                lastPages[key] = pages
                lastPage[key] = page

                scope.launch {
                    controller.goTo(
                        Locator(
                            scheme = LocatorSchemes.REFLOW_PAGE,
                            value = "$spine:$page:$sig",
                            extras = mapOf(
                                "pages" to pages.toString(),
                                "sig" to sig.toString(),
                                "metricsOnly" to "1"
                            )
                        )
                    )
                }
                true
            }

            "linkbounds" -> {
                val spine = uri.getQueryParameter("spine")?.toIntOrNull() ?: return true
                val sig = uri.getQueryParameter("sig")?.toIntOrNull() ?: return true
                val page = uri.getQueryParameter("page")?.toIntOrNull() ?: return true
                if (page < 0) return true
                val data = uri.getQueryParameter("data") ?: return true

                val currentLocator = controller.state.value.locator
                val currentSpine = currentLocator.value.split(':').getOrNull(0)?.toIntOrNull()
                if (currentLocator.scheme == LocatorSchemes.REFLOW_PAGE &&
                    currentSpine != null &&
                    currentSpine != spine
                ) {
                    return true
                }

                scope.launch {
                    controller.goTo(
                        Locator(
                            scheme = LocatorSchemes.REFLOW_PAGE,
                            value = "$spine:$page:$sig",
                            extras = mapOf(
                                "sig" to sig.toString(),
                                "metricsOnly" to "1",
                                "linkBounds" to data
                            )
                        )
                    )
                }
                true
            }

            "external" -> true
            else -> true
        }
    }
}
