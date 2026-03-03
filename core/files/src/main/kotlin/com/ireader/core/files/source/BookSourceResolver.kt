package com.ireader.core.files.source

import com.ireader.core.data.book.BookRecord

interface BookSourceResolver {
    fun resolve(book: BookRecord): DocumentSource?
}
