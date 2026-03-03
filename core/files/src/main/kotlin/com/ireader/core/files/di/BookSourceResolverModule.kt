package com.ireader.core.files.di

import com.ireader.core.files.source.BookSourceResolver
import com.ireader.core.files.source.DefaultBookSourceResolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BookSourceResolverModule {

    @Binds
    @Singleton
    abstract fun bindBookSourceResolver(impl: DefaultBookSourceResolver): BookSourceResolver
}

