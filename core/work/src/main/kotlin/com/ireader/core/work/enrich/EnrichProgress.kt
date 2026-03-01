package com.ireader.core.work.enrich

import androidx.work.Data
import androidx.work.workDataOf

object EnrichProgress {
    const val KEY_DONE = "done"
    const val KEY_TOTAL = "total"
    const val KEY_TITLE = "title"

    fun data(done: Int, total: Int, title: String?): Data {
        return workDataOf(
            KEY_DONE to done,
            KEY_TOTAL to total,
            KEY_TITLE to (title ?: "")
        )
    }
}
