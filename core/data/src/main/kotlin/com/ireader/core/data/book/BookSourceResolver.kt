package com.ireader.core.data.book

import com.ireader.reader.api.open.DocumentSource

interface BookSourceResolver {
    fun resolve(book: BookRecord): DocumentSource?
}
