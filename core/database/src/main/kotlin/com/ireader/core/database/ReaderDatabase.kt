package com.ireader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ireader.core.database.book.BookDao
import com.ireader.core.database.book.BookEntity
import com.ireader.core.database.importing.ImportItemDao
import com.ireader.core.database.importing.ImportItemEntity
import com.ireader.core.database.importing.ImportJobDao
import com.ireader.core.database.importing.ImportJobEntity

@Database(
    entities = [
        BookEntity::class,
        ImportJobEntity::class,
        ImportItemEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(DbConverters::class)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun importJobDao(): ImportJobDao
    abstract fun importItemDao(): ImportItemDao
}
