package com.ireader.core.common.android.surface

import androidx.fragment.app.FragmentManager
import com.ireader.reader.api.render.RenderSurface

/**
 * 以 Fragment 容器承载引擎渲染内容的 Surface。
 */
interface FragmentRenderSurface : RenderSurface {
    val fragmentManager: FragmentManager
    val containerViewId: Int
}
