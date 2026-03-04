package com.ireader.engines.pdf.di

import android.content.Context
import com.ireader.engines.pdf.PdfEngine
import com.ireader.engines.pdf.PdfEngineConfig
import com.ireader.engines.pdf.internal.provider.StoredPdfAnnotationProvider
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
object PdfEngineModule {

    @Provides
    @IntoSet
    @Singleton
    fun providePdfEngine(
        @ApplicationContext context: Context,
        annotationStore: AnnotationStore
    ): ReaderEngine {
        return PdfEngine(
            context = context,
            config = PdfEngineConfig(
                annotationProviderFactory = { documentId ->
                    StoredPdfAnnotationProvider(
                        documentId = documentId,
                        store = annotationStore
                    )
                }
            )
        )
    }
}
