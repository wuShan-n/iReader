package com.ireader.core.database.collection

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CollectionEntity): Long

    @Query("SELECT * FROM collections WHERE collectionId = :collectionId LIMIT 1")
    suspend fun getById(collectionId: Long): CollectionEntity?

    @Query("SELECT * FROM collections WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): CollectionEntity?

    @Query("SELECT * FROM collections ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("DELETE FROM collections WHERE collectionId = :collectionId")
    suspend fun deleteById(collectionId: Long)
}
