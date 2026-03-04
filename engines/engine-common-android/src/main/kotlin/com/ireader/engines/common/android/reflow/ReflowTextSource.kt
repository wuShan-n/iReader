package com.ireader.engines.common.android.reflow

interface ReflowTextSource {
    val lengthChars: Long
    fun readString(start: Long, count: Int): String
}

