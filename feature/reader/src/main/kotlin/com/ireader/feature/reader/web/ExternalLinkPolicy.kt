package com.ireader.feature.reader.web

import java.net.URI
import java.util.Locale

object ExternalLinkPolicy {
    sealed interface Decision {
        data class Allow(val url: String) : Decision
        data class Block(val reason: String) : Decision
    }

    private val allowedSchemes: Set<String> = setOf(
        "https",
        "http",
        "mailto",
        "tel"
    )

    fun evaluate(rawUrl: String): Decision {
        val candidate = rawUrl.trim()
        if (candidate.isBlank()) return Decision.Block("empty")

        val uri = runCatching { URI(candidate) }.getOrNull()
            ?: return Decision.Block("invalid_uri")
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
            ?: return Decision.Block("missing_scheme")
        if (scheme !in allowedSchemes) return Decision.Block("unsupported_scheme")

        if ((scheme == "http" || scheme == "https") && uri.host.isNullOrBlank()) {
            return Decision.Block("missing_host")
        }
        return Decision.Allow(uri.toString())
    }
}

