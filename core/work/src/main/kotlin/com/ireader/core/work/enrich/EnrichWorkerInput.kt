package com.ireader.core.work.enrich

import androidx.work.Data

object EnrichWorkerInput {
    private const val KEY_JOB_ID = "job_id"

    fun data(jobId: String): Data {
        return Data.Builder()
            .putString(KEY_JOB_ID, jobId)
            .build()
    }

    fun jobId(data: Data): String? = data.getString(KEY_JOB_ID)
}
