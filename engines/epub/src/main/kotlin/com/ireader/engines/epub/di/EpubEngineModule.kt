package com.ireader.engines.epub.di

import android.content.Context
import com.ireader.engines.epub.EpubEngine
import com.ireader.reader.api.engine.ReaderEngine
import com.ireader.reader.api.provider.AnnotationStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EpubEngineModule {

    @Provides
    @IntoSet
    @Singleton
    fun provideEpubEngine(
        @ApplicationContext context: Context,
        annotationStore: AnnotationStore
    ): ReaderEngine = EpubEngine(
        context = context.applicationContext,
        annotationStore = annotationStore
    )
}
