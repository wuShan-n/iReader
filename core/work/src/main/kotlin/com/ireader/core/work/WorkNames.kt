package com.ireader.core.work

object WorkNames {
    fun uniqueForJob(jobId: String): String = "import:$jobId"
    fun tagForJob(jobId: String): String = "import_tag:$jobId"
    fun uniqueEnrichForJob(jobId: String): String = "enrich:$jobId"
    fun tagEnrichForJob(jobId: String): String = "enrich_tag:$jobId"
}
