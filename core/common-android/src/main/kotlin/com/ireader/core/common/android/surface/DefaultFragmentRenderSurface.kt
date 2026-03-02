package com.ireader.core.common.android.surface

import androidx.fragment.app.FragmentManager

data class DefaultFragmentRenderSurface(
    override val fragmentManager: FragmentManager,
    override val containerViewId: Int
) : FragmentRenderSurface
