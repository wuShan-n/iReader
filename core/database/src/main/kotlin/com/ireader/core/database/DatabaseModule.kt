package com.ireader.core.database

import android.content.Context
import androidx.room.Room
import com.ireader.core.database.book.BookDao
import com.ireader.core.database.collection.BookCollectionDao
import com.ireader.core.database.collection.CollectionDao
import com.ireader.core.database.importing.ImportItemDao
import com.ireader.core.database.importing.ImportJobDao
import com.ireader.core.database.progress.ProgressDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideReaderDatabase(@ApplicationContext context: Context): ReaderDatabase {
        return Room.databaseBuilder(
            context,
            ReaderDatabase::class.java,
            "ireader.db"
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }

    @Provides
    fun provideBookDao(database: ReaderDatabase): BookDao = database.bookDao()

    @Provides
    fun provideProgressDao(database: ReaderDatabase): ProgressDao = database.progressDao()

    @Provides
    fun provideCollectionDao(database: ReaderDatabase): CollectionDao = database.collectionDao()

    @Provides
    fun provideBookCollectionDao(database: ReaderDatabase): BookCollectionDao = database.bookCollectionDao()

    @Provides
    fun provideImportJobDao(database: ReaderDatabase): ImportJobDao = database.importJobDao()

    @Provides
    fun provideImportItemDao(database: ReaderDatabase): ImportItemDao = database.importItemDao()
}
