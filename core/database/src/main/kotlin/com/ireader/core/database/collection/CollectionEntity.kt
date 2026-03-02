package com.ireader.core.database.collection

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "collections",
    indices = [Index(value = ["name"], unique = true)]
)
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val collectionId: Long = 0,
    val name: String,
    val createdAtEpochMs: Long,
    val sortOrder: Int = 0
)
