package com.ireader.core.database.collection

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.ireader.core.database.book.BookEntity

@Entity(
    tableName = "book_collection",
    primaryKeys = ["bookId", "collectionId"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["bookId"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["collectionId"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["collectionId"])
    ]
)
data class BookCollectionEntity(
    val bookId: Long,
    val collectionId: Long
)
