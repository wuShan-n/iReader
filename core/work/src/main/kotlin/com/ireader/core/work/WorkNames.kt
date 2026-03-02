package com.ireader.core.work

object WorkNames {
    const val REINDEX_TAG: String = "reindex_tag"
    const val MISSING_CHECK_TAG: String = "missing_check_tag"
    const val MISSING_CHECK_UNIQUE: String = "missing_check"

    fun uniqueForJob(jobId: String): String = "import:$jobId"
    fun tagForJob(jobId: String): String = "import_tag:$jobId"
    fun uniqueEnrichForJob(jobId: String): String = "enrich:$jobId"
    fun tagEnrichForJob(jobId: String): String = "enrich_tag:$jobId"

    fun uniqueReindex(bookIds: List<Long>): String {
        val suffix = bookIds.sorted().joinToString(separator = ",")
        return "reindex:$suffix"
    }
}
