package com.ireader.feature.library.domain.usecase

import android.net.Uri
import com.ireader.core.files.importing.DuplicateStrategy
import com.ireader.core.files.importing.ImportManager
import com.ireader.core.files.importing.ImportRequest
import javax.inject.Inject

class StartImportUseCase @Inject constructor(
    private val importManager: ImportManager
) {
    suspend operator fun invoke(
        uris: List<Uri>,
        strategy: DuplicateStrategy = DuplicateStrategy.SKIP
    ): String {
        return importManager.enqueue(
            ImportRequest(
                uris = uris,
                duplicateStrategy = strategy
            )
        )
    }
}
