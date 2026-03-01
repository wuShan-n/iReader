package com.ireader.reader.runtime.render

import com.ireader.reader.model.DocumentCapabilities
import com.ireader.reader.api.render.RenderConfig

object RenderDefaults {

    fun configFor(capabilities: DocumentCapabilities): RenderConfig =
        when {
            capabilities.fixedLayout -> RenderConfig.FixedPage()
            else -> RenderConfig.ReflowText()
        }
}


