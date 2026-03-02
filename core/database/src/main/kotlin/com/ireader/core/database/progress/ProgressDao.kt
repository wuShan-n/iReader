package com.ireader.core.database.progress

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProgressEntity)

    @Query("SELECT * FROM progress WHERE bookId = :bookId LIMIT 1")
    suspend fun getByBookId(bookId: Long): ProgressEntity?

    @Query("SELECT * FROM progress WHERE bookId = :bookId LIMIT 1")
    fun observeByBookId(bookId: Long): Flow<ProgressEntity?>

    @Query("DELETE FROM progress WHERE bookId = :bookId")
    suspend fun deleteByBookId(bookId: Long)
}
