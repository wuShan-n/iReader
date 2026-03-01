package com.ireader.di

import com.ireader.engines.epub.EpubEngine
import com.ireader.engines.pdf.PdfEngine
import com.ireader.engines.txt.TxtEngine
import com.ireader.reader.api.engine.EngineRegistry
import com.ireader.reader.runtime.DefaultReaderRuntime
import com.ireader.reader.runtime.ReaderRuntime
import com.ireader.reader.runtime.registry.EngineRegistryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReaderRuntimeModule {

    @Provides
    @Singleton
    fun provideEngineRegistry(): EngineRegistry {
        return EngineRegistryImpl(
            setOf(
                TxtEngine(),
                EpubEngine(),
                PdfEngine()
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
