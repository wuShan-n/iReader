package com.ireader.core.data.book

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

object LibrarySqlBuilder {
    fun build(query: LibraryQuery): SupportSQLiteQuery {
        val sql = StringBuilder(
            """
            SELECT books.*, progress.progression AS progression, progress.updatedAtEpochMs AS progressUpdatedAtEpochMs
            FROM books
            LEFT JOIN progress ON progress.bookId = books.bookId
            WHERE 1 = 1
            """.trimIndent()
        )
        val args = mutableListOf<Any>()

        query.keyword
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { keyword ->
                sql.append(
                    " AND (COALESCE(books.title, '') LIKE ? OR COALESCE(books.author, '') LIKE ? OR books.fileName LIKE ?)"
                )
                val pattern = "%$keyword%"
                args += pattern
                args += pattern
                args += pattern
            }

        if (query.onlyFavorites) {
            sql.append(" AND books.favorite = 1")
        }

        if (query.statuses.isNotEmpty()) {
            val placeholders = query.statuses.joinToString(separator = ",") { "?" }
            sql.append(" AND books.readingStatus IN ($placeholders)")
            query.statuses.forEach { args += it.name }
        }

        if (query.indexStates.isNotEmpty()) {
            val placeholders = query.indexStates.joinToString(separator = ",") { "?" }
            sql.append(" AND books.indexState IN ($placeholders)")
            query.indexStates.forEach { args += it.name }
        }

        if (query.formats.isNotEmpty()) {
            val placeholders = query.formats.joinToString(separator = ",") { "?" }
            sql.append(" AND books.format IN ($placeholders)")
            query.formats.forEach { args += it.name }
        }

        query.collectionId?.let { collectionId ->
            sql.append(
                " AND EXISTS (SELECT 1 FROM book_collection bc WHERE bc.bookId = books.bookId AND bc.collectionId = ?)"
            )
            args += collectionId
        }

        sql.append(" ORDER BY ")
        sql.append(
            when (query.sort) {
                LibrarySort.RECENTLY_UPDATED -> "books.updatedAtEpochMs DESC"
                LibrarySort.RECENTLY_ADDED -> "books.addedAtEpochMs DESC"
                LibrarySort.LAST_OPENED -> "COALESCE(books.lastOpenedAtEpochMs, 0) DESC, books.updatedAtEpochMs DESC"
                LibrarySort.TITLE_AZ -> "COALESCE(books.title, books.fileName, '') COLLATE NOCASE ASC"
                LibrarySort.AUTHOR_AZ -> "COALESCE(books.author, '') COLLATE NOCASE ASC, COALESCE(books.title, books.fileName, '') COLLATE NOCASE ASC"
                LibrarySort.PROGRESSION_DESC -> "COALESCE(progress.progression, 0.0) DESC, books.updatedAtEpochMs DESC"
            }
        )

        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }
}
