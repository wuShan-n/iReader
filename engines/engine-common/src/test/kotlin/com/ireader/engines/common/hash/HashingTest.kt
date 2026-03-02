package com.ireader.engines.common.hash

import org.junit.Assert.assertEquals
import org.junit.Test

class HashingTest {

    @Test
    fun `sha1 should be stable`() {
        assertEquals(
            "a9993e364706816aba3e25717850c26c9cd0d89d",
            Hashing.sha1Hex("abc")
        )
    }

    @Test
    fun `sha256 should be stable`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Hashing.sha256Hex("abc")
        )
    }
}
