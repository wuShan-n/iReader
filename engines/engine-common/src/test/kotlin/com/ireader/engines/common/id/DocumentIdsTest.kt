package com.ireader.engines.common.id

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DocumentIdsTest {

    @Test
    fun `sha1 prefixed id should be deterministic`() {
        val first = DocumentIds.fromSha1(prefix = "pdf", raw = "same-raw")
        val second = DocumentIds.fromSha1(prefix = "pdf", raw = "same-raw")
        assertEquals(first, second)
    }

    @Test
    fun `sha256 id should change when raw changes`() {
        val first = DocumentIds.fromSha256(raw = "raw-a", length = 40)
        val second = DocumentIds.fromSha256(raw = "raw-b", length = 40)
        assertNotEquals(first, second)
    }
}
