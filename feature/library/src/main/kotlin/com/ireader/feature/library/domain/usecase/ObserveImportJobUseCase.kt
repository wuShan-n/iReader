package com.ireader.feature.library.domain.usecase

import com.ireader.core.files.importing.ImportJobState
import com.ireader.core.files.importing.ImportManager
import kotlinx.coroutines.flow.Flow

class ObserveImportJobUseCase(
    private val importManager: ImportManager
) {
    operator fun invoke(jobId: String): Flow<ImportJobState> = importManager.observe(jobId)
}
