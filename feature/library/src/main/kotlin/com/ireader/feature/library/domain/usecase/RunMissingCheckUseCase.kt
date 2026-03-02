package com.ireader.feature.library.domain.usecase

import com.ireader.core.data.book.BookMaintenanceScheduler
import javax.inject.Inject

class RunMissingCheckUseCase @Inject constructor(
    private val scheduler: BookMaintenanceScheduler
) {
    operator fun invoke() {
        scheduler.enqueueMissingCheck()
    }
}
