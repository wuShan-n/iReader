package com.ireader.core.work

object WorkNames {
    fun uniqueForJob(jobId: String): String = "import:$jobId"
    fun tagForJob(jobId: String): String = "import_tag:$jobId"
}
