package com.ireader.reader.api.render

import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorRange

/**
 * reflow 文本映射：用于 “字符索引 <-> Locator/Range”
 * 支撑：复制、标注范围定位、搜索命中定位等。
 */
interface TextMapping {
    fun locatorAt(charIndex: Int): Locator
    fun rangeFor(startChar: Int, endChar: Int): LocatorRange
    fun charRangeFor(range: LocatorRange): IntRange?
}


