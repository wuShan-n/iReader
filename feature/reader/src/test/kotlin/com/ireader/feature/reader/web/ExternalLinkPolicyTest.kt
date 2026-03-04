package com.ireader.feature.reader.web

import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalLinkPolicyTest {

    @Test
    fun `http links are allowed`() {
        val decision = ExternalLinkPolicy.evaluate("https://example.com/read")
        assertTrue(decision is ExternalLinkPolicy.Decision.Allow)
    }

    @Test
    fun `custom schemes are blocked`() {
        val decision = ExternalLinkPolicy.evaluate("intent://scan/#Intent;scheme=zxing;end")
        assertTrue(decision is ExternalLinkPolicy.Decision.Block)
    }

    @Test
    fun `javascript scheme is blocked`() {
        val decision = ExternalLinkPolicy.evaluate("javascript:alert(1)")
        assertTrue(decision is ExternalLinkPolicy.Decision.Block)
    }
}

