package com.ireader.core.work.di

import android.content.Context
import androidx.work.WorkManager
import com.ireader.core.data.book.BookIndexer
import com.ireader.core.data.book.BookMaintenanceScheduler
import com.ireader.core.files.importing.ImportWorkScheduler
import com.ireader.core.work.WorkImportScheduler
import com.ireader.core.work.index.DefaultBookIndexer
import com.ireader.core.work.index.WorkBookMaintenanceScheduler
import com.ireader.reader.runtime.format.BookFormatDetector
import com.ireader.reader.runtime.format.DefaultBookFormatDetector
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkBindingsModule {
    @Binds
    @Singleton
    abstract fun bindImportWorkScheduler(impl: WorkImportScheduler): ImportWorkScheduler

    @Binds
    @Singleton
    abstract fun bindBookIndexer(impl: DefaultBookIndexer): BookIndexer

    @Binds
    @Singleton
    abstract fun bindBookMaintenanceScheduler(impl: WorkBookMaintenanceScheduler): BookMaintenanceScheduler
}

@Module
@InstallIn(SingletonComponent::class)
object WorkProvidesModule {
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideBookFormatDetector(): BookFormatDetector = DefaultBookFormatDetector()
}
