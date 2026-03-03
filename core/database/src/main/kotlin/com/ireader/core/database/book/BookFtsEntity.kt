package com.ireader.core.database.book

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

@Fts4(
    contentEntity = BookEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61
)
@Entity(tableName = "books_fts")
data class BookFtsEntity(
    val title: String?,
    val author: String?,
    val fileName: String
)
