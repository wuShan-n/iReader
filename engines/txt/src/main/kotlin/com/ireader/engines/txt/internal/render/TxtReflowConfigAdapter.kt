package com.ireader.engines.txt.internal.render

import com.ireader.reader.api.render.RenderConfig

private val txtDefaults = RenderConfig.ReflowText()

internal fun RenderConfig.ReflowText.toTxtEffectiveConfig(): RenderConfig.ReflowText {
    return copy(
        cjkLineBreakStrict = txtDefaults.cjkLineBreakStrict,
        hangingPunctuation = txtDefaults.hangingPunctuation
    )
}
