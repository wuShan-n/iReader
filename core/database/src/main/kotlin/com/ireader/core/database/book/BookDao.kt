package com.ireader.core.database.book

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BookEntity)

    @Query("SELECT * FROM books WHERE fingerprintSha256 = :fingerprint LIMIT 1")
    suspend fun findByFingerprint(fingerprint: String): BookEntity?

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getById(bookId: String): BookEntity?

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteById(bookId: String)

    @Query("SELECT * FROM books ORDER BY updatedAtEpochMs DESC")
    fun observeByUpdatedDesc(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY createdAtEpochMs DESC")
    fun observeByCreatedDesc(): Flow<List<BookEntity>>

    @Query(
        """
        SELECT * FROM books
        ORDER BY COALESCE(title, displayName, '') COLLATE NOCASE ASC
        """
    )
    fun observeByTitleAsc(): Flow<List<BookEntity>>

    @Query(
        """
        SELECT * FROM books
        ORDER BY COALESCE(author, '') COLLATE NOCASE ASC,
                 COALESCE(title, displayName, '') COLLATE NOCASE ASC
        """
    )
    fun observeByAuthorAsc(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    fun observeById(bookId: String): Flow<BookEntity?>
}
