package com.ireader.feature.reader.navigation

import com.ireader.core.navigation.AppRoutes

object ReaderRoute {
    const val argBookId: String = AppRoutes.ARG_BOOK_ID
    const val route: String = AppRoutes.READER

    fun create(bookId: Long): String = AppRoutes.reader(bookId)
}
