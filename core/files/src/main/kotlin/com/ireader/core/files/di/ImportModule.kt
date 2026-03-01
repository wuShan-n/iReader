package com.ireader.core.files.di

import com.ireader.core.files.importing.DefaultImportManager
import com.ireader.core.files.importing.ImportManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ImportModule {
    @Binds
    @Singleton
    abstract fun bindImportManager(impl: DefaultImportManager): ImportManager
}
