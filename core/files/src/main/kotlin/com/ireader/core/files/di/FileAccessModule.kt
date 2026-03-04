package com.ireader.core.files.di

import com.ireader.core.files.permission.UriPermissionGateway
import com.ireader.core.files.permission.UriPermissionStore
import com.ireader.core.files.source.DefaultUriDocumentSourceFactory
import com.ireader.core.files.source.UriDocumentSourceFactory
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FileAccessModule {

    @Binds
    @Singleton
    abstract fun bindUriPermissionGateway(impl: UriPermissionStore): UriPermissionGateway

    @Binds
    @Singleton
    abstract fun bindUriDocumentSourceFactory(impl: DefaultUriDocumentSourceFactory): UriDocumentSourceFactory
}
