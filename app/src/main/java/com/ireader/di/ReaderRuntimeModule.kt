package com.ireader.di

import android.content.Context
import com.ireader.engines.epub.EpubEngine
import com.ireader.engines.pdf.PdfEngine
import com.ireader.engines.txt.TxtEngine
import com.ireader.engines.txt.TxtEngineConfig
import com.ireader.reader.api.engine.EngineRegistry
import com.ireader.reader.api.provider.AnnotationStore
import com.ireader.reader.runtime.DefaultReaderRuntime
import com.ireader.reader.runtime.ReaderRuntime
import com.ireader.reader.runtime.registry.EngineRegistryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReaderRuntimeModule {

    @Provides
    @Singleton
    fun provideEngineRegistry(
        @ApplicationContext context: Context,
        annotationStore: AnnotationStore
    ): EngineRegistry {
        return EngineRegistryImpl(
            setOf(
                TxtEngine(
                    config = TxtEngineConfig(
                        cacheDir = context.cacheDir,
                        persistPagination = true,
                        persistOutline = false
                    )
                ),
                EpubEngine(
                    context = context,
                    annotationStore = annotationStore
                ),
                PdfEngine(context = context)
            )
        )
    }

    @Provides
    @Singleton
    fun provideReaderRuntime(
        engineRegistry: EngineRegistry
    ): ReaderRuntime {
        return DefaultReaderRuntime(engineRegistry)
    }
}
