package com.ireader.engines.epub.internal.pagination

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpubPageMetricsStoreTest {

    @Test
    fun `store persists values after flush`() {
        val dir = Files.createTempDirectory("epub-metrics-store").toFile()
        val file = dir.resolve("metrics.properties")

        val store = EpubPageMetricsStore(file)
        assertNull(store.getPages(spine = 0, sig = 100))

        store.putPages(spine = 0, sig = 100, pages = 23)
        store.flush()

        val restored = EpubPageMetricsStore(file)
        assertEquals(23, restored.getPages(spine = 0, sig = 100))
    }

    @Test
    fun `store clamps pages into safe range`() {
        val dir = Files.createTempDirectory("epub-metrics-clamp").toFile()
        val file = dir.resolve("metrics.properties")

        val store = EpubPageMetricsStore(file)
        store.putPages(spine = 1, sig = 200, pages = 99999)
        store.flush()

        val restored = EpubPageMetricsStore(file)
        assertEquals(5000, restored.getPages(spine = 1, sig = 200))
    }
}
