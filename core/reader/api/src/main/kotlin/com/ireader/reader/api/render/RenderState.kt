package com.ireader.reader.api.render

import com.ireader.reader.model.Locator
import com.ireader.reader.model.Progression

data class NavigationAvailability(
    val canGoPrev: Boolean,
    val canGoNext: Boolean
)

data class RenderState(
    val locator: Locator,
    val progression: Progression,
    val nav: NavigationAvailability,
    val titleInView: String? = null,
    val config: RenderConfig
)


