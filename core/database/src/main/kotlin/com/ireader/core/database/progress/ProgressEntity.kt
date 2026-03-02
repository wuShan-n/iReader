package com.ireader.core.database.progress

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ireader.core.database.book.BookEntity

@Entity(
    tableName = "progress",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["bookId"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index(value = ["bookId"])]
)
data class ProgressEntity(
    @PrimaryKey val bookId: Long,
    val locatorJson: String,
    val progression: Double,
    val updatedAtEpochMs: Long
)
