package com.ireader.core.files.source

import com.ireader.core.database.book.BookEntity

interface BookSourceResolver {
    fun resolve(book: BookEntity): DocumentSource?
}

