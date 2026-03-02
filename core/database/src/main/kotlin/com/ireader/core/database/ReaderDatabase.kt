package com.ireader.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ireader.core.database.annotation.AnnotationDao
import com.ireader.core.database.annotation.AnnotationEntity
import com.ireader.core.database.book.BookDao
import com.ireader.core.database.book.BookEntity
import com.ireader.core.database.collection.BookCollectionDao
import com.ireader.core.database.collection.BookCollectionEntity
import com.ireader.core.database.collection.CollectionDao
import com.ireader.core.database.collection.CollectionEntity
import com.ireader.core.database.importing.ImportItemDao
import com.ireader.core.database.importing.ImportItemEntity
import com.ireader.core.database.importing.ImportJobDao
import com.ireader.core.database.importing.ImportJobEntity
import com.ireader.core.database.progress.ProgressDao
import com.ireader.core.database.progress.ProgressEntity

@Database(
    entities = [
        BookEntity::class,
        ProgressEntity::class,
        CollectionEntity::class,
        BookCollectionEntity::class,
        ImportJobEntity::class,
        ImportItemEntity::class,
        AnnotationEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(DbConverters::class)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun progressDao(): ProgressDao
    abstract fun collectionDao(): CollectionDao
    abstract fun bookCollectionDao(): BookCollectionDao
    abstract fun importJobDao(): ImportJobDao
    abstract fun importItemDao(): ImportItemDao
    abstract fun annotationDao(): AnnotationDao
}
