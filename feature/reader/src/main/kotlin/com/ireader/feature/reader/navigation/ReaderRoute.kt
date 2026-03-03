package com.ireader.feature.reader.navigation

import android.net.Uri
import androidx.navigation.NavBackStackEntry
import com.ireader.core.navigation.AppRoutes

object ReaderRoute {
    const val argBookId: String = AppRoutes.ARG_BOOK_ID
    const val argLocator: String = AppRoutes.ARG_LOCATOR
    const val route: String = AppRoutes.READER

    fun create(bookId: String, locator: String? = null): String {
        val encodedLocator = locator?.let(Uri::encode)
        return AppRoutes.reader(bookId = bookId, locator = encodedLocator)
    }

    fun bookId(entry: NavBackStackEntry): String =
        requireNotNull(entry.arguments?.getString(argBookId)) { "Missing $argBookId" }

    fun locatorArg(entry: NavBackStackEntry): String? =
        entry.arguments?.getString(argLocator)?.let(Uri::decode)
}
