package com.ireader.feature.reader.domain.usecase

import com.ireader.core.data.book.ProgressRepo
import com.ireader.core.data.book.LocatorCodec
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorExtraKeys
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SaveReadingProgress @Inject constructor(
    private val progressRepo: ProgressRepo,
    private val locatorCodec: LocatorCodec
) {
    suspend operator fun invoke(bookId: Long, locator: Locator, progression: Double) {
        withContext(Dispatchers.IO) {
            progressRepo.upsert(
                bookId = bookId,
                locatorJson = locatorCodec.encode(locator),
                progression = progression.coerceIn(0.0, 1.0),
                updatedAtEpochMs = System.currentTimeMillis(),
                pageAnchorProfile = locator.extras[LocatorExtraKeys.REFLOW_PAGE_PROFILE],
                pageAnchorsJson = locator.extras[LocatorExtraKeys.REFLOW_PAGE_ANCHORS]
            )
        }
    }
}
