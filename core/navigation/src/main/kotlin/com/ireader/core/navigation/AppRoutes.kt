package com.ireader.core.navigation

object AppRoutes {
    const val LIBRARY = "library"
    const val ARG_BOOK_ID = "bookId"
    const val READER = "reader/{$ARG_BOOK_ID}"
    fun reader(bookId: Long): String = "reader/$bookId"
    const val ANNOTATIONS = "annotations"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
}
