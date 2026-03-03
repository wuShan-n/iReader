package com.ireader.core.navigation

object AppRoutes {
    const val LIBRARY = "library"
    const val ARG_BOOK_ID = "bookId"
    const val ARG_LOCATOR = "locator"

    const val READER = "reader/{$ARG_BOOK_ID}?$ARG_LOCATOR={$ARG_LOCATOR}"
    fun reader(bookId: Long, locator: String? = null): String = reader(bookId.toString(), locator)
    fun reader(bookId: String, locator: String? = null): String {
        val locatorArg = locator?.takeIf { it.isNotBlank() }
        return if (locatorArg == null) {
            "reader/$bookId"
        } else {
            "reader/$bookId?$ARG_LOCATOR=$locatorArg"
        }
    }

    const val ANNOTATIONS = "annotations/{$ARG_BOOK_ID}"
    fun annotations(bookId: Long): String = annotations(bookId.toString())
    fun annotations(bookId: String): String = "annotations/$bookId"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
}
