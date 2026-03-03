package com.ireader.engines.pdf.di

import android.content.Context
import com.ireader.engines.pdf.PdfEngine
import com.ireader.reader.api.engine.ReaderEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PdfEngineModule {

    @Provides
    @IntoSet
    @Singleton
    fun providePdfEngine(
        @ApplicationContext context: Context
    ): ReaderEngine {
        return PdfEngine(context = context)
    }
}

