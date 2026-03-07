package com.ireader.feature.reader.domain.usecase

import com.ireader.core.data.book.LocatorCodec
import com.ireader.core.data.book.ProgressRepo
import com.ireader.core.data.reader.ReaderLaunchRepository
import com.ireader.reader.model.Locator
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SaveReadingProgress private constructor(
    private val launchRepository: ReaderLaunchRepository?,
    private val progressRepo: ProgressRepo?,
    private val locatorCodec: LocatorCodec?
) {
    @Inject
    constructor(
        launchRepository: ReaderLaunchRepository
    ) : this(
        launchRepository = launchRepository,
        progressRepo = null,
        locatorCodec = null
    )

    constructor(
        progressRepo: ProgressRepo,
        locatorCodec: LocatorCodec
    ) : this(
        launchRepository = null,
        progressRepo = progressRepo,
        locatorCodec = locatorCodec
    )

    suspend operator fun invoke(bookId: Long, locator: Locator, progression: Double) {
        withContext(Dispatchers.IO) {
            when {
                launchRepository != null -> {
                    launchRepository.saveProgress(bookId, locator, progression)
                }

                progressRepo != null && locatorCodec != null -> {
                    progressRepo.upsert(
                        bookId = bookId,
                        locatorJson = locatorCodec.encode(locator),
                        progression = progression.coerceIn(0.0, 1.0),
                        updatedAtEpochMs = System.currentTimeMillis(),
                        pageAnchorProfile = locator.extras[com.ireader.reader.model.LocatorExtraKeys.REFLOW_PAGE_PROFILE],
                        pageAnchorsJson = locator.extras[com.ireader.reader.model.LocatorExtraKeys.REFLOW_PAGE_ANCHORS]
                    )
                }

                else -> error("SaveReadingProgress is not configured")
            }
        }
    }
}
