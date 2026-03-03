package com.ireader.feature.reader.navigation

import android.net.Uri
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.ireader.core.navigation.AppRoutes

object ReaderRoute {
    const val argBookId: String = AppRoutes.ARG_BOOK_ID
    const val argLocator: String = AppRoutes.ARG_LOCATOR
    const val route: String = "reader"
    const val pattern: String = "$route/{$argBookId}?$argLocator={$argLocator}"

    val arguments = listOf(
        navArgument(argBookId) {
            type = NavType.LongType
        },
        navArgument(argLocator) {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        }
    )

    fun create(bookId: Long, locator: String? = null): String {
        val encodedLocator = locator?.let(Uri::encode)
        return AppRoutes.reader(bookId = bookId, locator = encodedLocator)
    }

    fun bookId(entry: NavBackStackEntry): Long =
        entry.arguments?.getLong(argBookId) ?: error("Missing $argBookId")

    fun locatorArg(entry: NavBackStackEntry): String? =
        entry.arguments?.getString(argLocator)?.let(Uri::decode)
}
