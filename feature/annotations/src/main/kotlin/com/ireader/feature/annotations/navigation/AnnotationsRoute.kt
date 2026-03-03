package com.ireader.feature.annotations.navigation

import com.ireader.core.navigation.AppRoutes

object AnnotationsRoute {
    const val route: String = AppRoutes.ANNOTATIONS

    fun create(bookId: String): String = AppRoutes.annotations(bookId)
}
