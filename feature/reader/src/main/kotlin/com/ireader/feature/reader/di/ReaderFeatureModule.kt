package com.ireader.feature.reader.di

import com.ireader.feature.reader.presentation.ReaderUiErrorMapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReaderFeatureModule {

    @Provides
    @Singleton
    fun provideReaderUiErrorMapper(): ReaderUiErrorMapper = ReaderUiErrorMapper()
}
