package com.ireader.di

import com.ireader.core.data.book.JsonLocatorCodec
import com.ireader.core.data.book.LocatorCodec
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocatorCodecModule {

    @Binds
    @Singleton
    abstract fun bindLocatorCodec(impl: JsonLocatorCodec): LocatorCodec
}

