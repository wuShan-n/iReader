package com.ireader.engines.txt.internal.open

import com.ireader.engines.txt.internal.provider.TxtOutlineProvider
import com.ireader.engines.txt.internal.provider.TxtSearchProviderPro
import com.ireader.engines.txt.internal.provider.TxtTextProvider
import com.ireader.engines.txt.internal.store.Utf16TextStore
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.api.provider.TextProvider
import kotlinx.coroutines.CoroutineDispatcher

internal interface TxtSessionProviderFactory {
    fun createOutlineProvider(
        files: TxtBookFiles,
        meta: TxtMeta,
        store: Utf16TextStore,
        ioDispatcher: CoroutineDispatcher,
        persistOutline: Boolean
    ): OutlineProvider

    fun createSearchProvider(
        files: TxtBookFiles,
        store: Utf16TextStore,
        meta: TxtMeta,
        ioDispatcher: CoroutineDispatcher
    ): SearchProvider

    fun createTextProvider(
        store: Utf16TextStore,
        ioDispatcher: CoroutineDispatcher
    ): TextProvider
}

internal object DefaultTxtSessionProviderFactory : TxtSessionProviderFactory {
    override fun createOutlineProvider(
        files: TxtBookFiles,
        meta: TxtMeta,
        store: Utf16TextStore,
        ioDispatcher: CoroutineDispatcher,
        persistOutline: Boolean
    ): OutlineProvider {
        return TxtOutlineProvider(
            files = files,
            meta = meta,
            store = store,
            ioDispatcher = ioDispatcher,
            persistOutline = persistOutline
        )
    }

    override fun createSearchProvider(
        files: TxtBookFiles,
        store: Utf16TextStore,
        meta: TxtMeta,
        ioDispatcher: CoroutineDispatcher
    ): SearchProvider {
        return TxtSearchProviderPro(
            files = files,
            store = store,
            meta = meta,
            ioDispatcher = ioDispatcher
        )
    }

    override fun createTextProvider(
        store: Utf16TextStore,
        ioDispatcher: CoroutineDispatcher
    ): TextProvider {
        return TxtTextProvider(
            store = store,
            ioDispatcher = ioDispatcher
        )
    }
}
