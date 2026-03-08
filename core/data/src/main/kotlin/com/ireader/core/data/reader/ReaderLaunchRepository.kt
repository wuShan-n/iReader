package com.ireader.core.data.reader

import com.ireader.core.data.book.BookRepo
import com.ireader.core.data.book.BookSourceResolver
import com.ireader.core.data.book.IndexState
import com.ireader.core.data.book.LocatorCodec
import com.ireader.core.data.book.ProgressRepo
import com.ireader.reader.api.error.ReaderError
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.open.OpenOptions
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.BookFormat
import com.ireader.reader.model.Locator
import com.ireader.reader.runtime.ReaderHandle
import com.ireader.reader.runtime.ReaderRuntime
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ReaderOpenResult {
    data object BookNotFound : ReaderOpenResult
    data object MissingSource : ReaderOpenResult
    data class Error(val error: ReaderError) : ReaderOpenResult
    data class Success(
        val book: com.ireader.core.data.book.BookRecord,
        val handle: ReaderHandle,
        val initialLocator: Locator?
    ) : ReaderOpenResult
}

@Singleton
class ReaderLaunchRepository @Inject constructor(
    private val bookRepo: BookRepo,
    private val progressRepo: ProgressRepo,
    private val locatorCodec: LocatorCodec,
    private val sourceResolver: BookSourceResolver,
    private val preferencesRepository: ReaderPreferencesRepository,
    private val runtime: ReaderRuntime
) {
    suspend fun openBook(
        bookId: Long,
        locatorArg: String?,
        password: String?
    ): ReaderOpenResult {
        val book = bookRepo.getRecordById(bookId) ?: return ReaderOpenResult.BookNotFound
        val source = sourceResolver.resolve(book)
        if (source == null) {
            bookRepo.setIndexState(book.bookId, IndexState.MISSING, "File not found")
            return ReaderOpenResult.MissingSource
        }

        val routeLocator = locatorArg
            ?.let(locatorCodec::decode)
            ?.takeIf { it.isSupportedFor(book.format) }
        val historyLocator = if (routeLocator == null) {
            runCatching {
                progressRepo.getByBookId(book.bookId)
                    ?.locatorJson
                    ?.let(locatorCodec::decode)
                    ?.takeIf { it.isSupportedFor(book.format) }
            }.getOrNull()
        } else {
            null
        }
        val initialLocator = routeLocator ?: historyLocator
        val snapshot = preferencesRepository.getOpenSettingsSnapshot()
        val txtInitialConfig = if (book.format == BookFormat.TXT) {
            snapshot.reflowConfig.withReaderAppearance(snapshot.displayPrefs)
        } else {
            null
        }
        val openResult = runtime.openSession(
            source = source,
            options = OpenOptions(
                hintFormat = book.format,
                password = password
            ),
            initialLocator = initialLocator,
            initialConfig = txtInitialConfig,
            resolveInitialConfig = if (txtInitialConfig == null) {
                { capabilities ->
                    if (capabilities.fixedLayout) {
                        snapshot.fixedConfig.withReaderAppearance(snapshot.displayPrefs)
                    } else {
                        snapshot.reflowConfig.withReaderAppearance(snapshot.displayPrefs)
                    }
                }
            } else {
                null
            }
        )
        return when (openResult) {
            is ReaderResult.Err -> ReaderOpenResult.Error(openResult.error)
            is ReaderResult.Ok -> ReaderOpenResult.Success(
                book = book,
                handle = openResult.value,
                initialLocator = initialLocator
            )
        }
    }

    fun decodeLocator(raw: String): Locator? = locatorCodec.decode(raw)

    fun encodeLocator(locator: Locator): String = locatorCodec.encode(locator)

    suspend fun saveProgress(bookId: Long, locator: Locator, progression: Double) {
        progressRepo.upsert(
            bookId = bookId,
            locatorJson = locatorCodec.encode(locator),
            progression = progression.coerceIn(0.0, 1.0),
            updatedAtEpochMs = System.currentTimeMillis(),
            pageAnchorProfile = locator.extras[com.ireader.reader.model.LocatorExtraKeys.REFLOW_PAGE_PROFILE],
            pageAnchorsJson = locator.extras[com.ireader.reader.model.LocatorExtraKeys.REFLOW_PAGE_ANCHORS]
        )
    }

    suspend fun touchLastOpened(bookId: Long) {
        bookRepo.touchLastOpened(bookId)
    }

    private fun Locator.isSupportedFor(format: BookFormat): Boolean {
        return when (format) {
            BookFormat.TXT -> scheme == com.ireader.reader.model.LocatorSchemes.TXT_STABLE_ANCHOR
            else -> true
        }
    }
}
