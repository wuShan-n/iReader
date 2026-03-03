package com.ireader.feature.reader.di

import com.ireader.feature.reader.domain.LocatorCodec
import com.ireader.feature.reader.domain.ReaderBookRepository
import com.ireader.feature.reader.domain.ReaderProgressRepository
import com.ireader.feature.reader.domain.ReaderSettingsRepository
import com.ireader.feature.reader.domain.impl.DataReaderBookRepository
import com.ireader.feature.reader.domain.impl.DataReaderProgressRepository
import com.ireader.feature.reader.domain.impl.InMemoryReaderSettingsRepository
import com.ireader.feature.reader.domain.impl.JsonLocatorCodec
import com.ireader.feature.reader.presentation.ReaderUiErrorMapper
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReaderFeatureBindingsModule {

    @Binds
    @Singleton
    abstract fun bindReaderBookRepository(impl: DataReaderBookRepository): ReaderBookRepository

    @Binds
    @Singleton
    abstract fun bindReaderProgressRepository(impl: DataReaderProgressRepository): ReaderProgressRepository

    @Binds
    @Singleton
    abstract fun bindReaderSettingsRepository(impl: InMemoryReaderSettingsRepository): ReaderSettingsRepository

    @Binds
    @Singleton
    abstract fun bindLocatorCodec(impl: JsonLocatorCodec): LocatorCodec
}

@Module
@InstallIn(SingletonComponent::class)
object ReaderFeatureModule {

    @Provides
    @Singleton
    fun provideReaderUiErrorMapper(): ReaderUiErrorMapper = ReaderUiErrorMapper()
}
