package com.ireader.feature.reader.web

import android.net.Uri
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.model.Locator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object ReaderWebViewLinkRouter {

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

            "external" -> true
            else -> true
        }
    }
}
