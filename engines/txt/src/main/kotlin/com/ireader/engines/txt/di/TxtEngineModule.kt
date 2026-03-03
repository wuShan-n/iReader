package com.ireader.engines.txt.di

import android.content.Context
import com.ireader.engines.txt.TxtEngine
import com.ireader.engines.txt.TxtEngineConfig
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
object TxtEngineModule {

    @Provides
    @IntoSet
    @Singleton
    fun provideTxtEngine(
        @ApplicationContext context: Context
    ): ReaderEngine {
        return TxtEngine(
            config = TxtEngineConfig(
                cacheDir = context.cacheDir,
                persistPagination = true,
                persistOutline = false
            )
        )
    }
}

