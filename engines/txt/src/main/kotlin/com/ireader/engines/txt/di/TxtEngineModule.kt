package com.ireader.engines.txt.di

import android.content.Context
import com.ireader.engines.txt.TxtEngine
import com.ireader.engines.txt.TxtEngineConfig
import com.ireader.engines.txt.internal.provider.StoredTxtAnnotationProvider
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
object TxtEngineModule {

    @Provides
    @IntoSet
    @Singleton
    fun provideTxtEngine(
        @ApplicationContext context: Context,
        annotationStore: AnnotationStore
    ): ReaderEngine {
        return TxtEngine(
            config = TxtEngineConfig(
                cacheDir = context.cacheDir,
                persistPagination = true,
                persistOutline = false,
                annotationProviderFactory = { documentId ->
                    StoredTxtAnnotationProvider(
                        documentId = documentId,
                        store = annotationStore
                    )
                }
            )
        )
    }
}
