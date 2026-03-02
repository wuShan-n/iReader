package com.ireader.core.database.collection

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BookCollectionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: BookCollectionEntity): Long

    @Query("DELETE FROM book_collection WHERE bookId = :bookId AND collectionId = :collectionId")
    suspend fun delete(bookId: Long, collectionId: Long)

    @Query("DELETE FROM book_collection WHERE bookId = :bookId")
    suspend fun deleteAllForBook(bookId: Long)

    @Query("SELECT collectionId FROM book_collection WHERE bookId = :bookId")
    suspend fun listCollectionIdsForBook(bookId: Long): List<Long>
}
