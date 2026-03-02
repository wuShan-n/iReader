package com.ireader.di

import com.ireader.di.annotation.RoomAnnotationStore
import com.ireader.reader.api.provider.AnnotationStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AnnotationStoreModule {

    @Binds
    @Singleton
    abstract fun bindAnnotationStore(impl: RoomAnnotationStore): AnnotationStore
}
