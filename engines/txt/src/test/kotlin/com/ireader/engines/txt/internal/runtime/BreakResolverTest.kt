package com.ireader.engines.txt.internal.runtime

import com.ireader.engines.txt.internal.softbreak.BreakMapState
import com.ireader.engines.txt.testing.buildTxtRuntimeFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BreakResolverTest {

    @Test
    fun `patch should persist and affect projected text`() = runBlocking {
        val fixture = buildTxtRuntimeFixture(
            text = "甲\n乙",
            sampleHash = "break-patch-persist",
            ioDispatcher = Dispatchers.IO
        )
        try {
            fixture.breakResolver.patch(offset = 1L, state = BreakMapState.SOFT_SPACE)

            val projected = fixture.breakResolver.projectRange(0L, fixture.sourceText.length.toLong())
            assertEquals("甲 乙", projected.displayText)

            val reopened = BreakResolver(
                store = fixture.store,
                files = fixture.files,
                meta = fixture.meta,
                breakIndex = fixture.breakIndex
            )
            assertEquals(BreakMapState.SOFT_SPACE, reopened.stateAt(1L))
            assertEquals("甲 乙", reopened.projectRange(0L, fixture.sourceText.length.toLong()).displayText)
        } finally {
            fixture.close()
        }
    }
}
