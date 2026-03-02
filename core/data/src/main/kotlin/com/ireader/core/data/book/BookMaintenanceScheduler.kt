package com.ireader.core.data.book

interface BookMaintenanceScheduler {
    fun enqueueReindex(bookIds: List<Long>)
    fun enqueueMissingCheck()
}
