package com.ireader.engines.txt.internal.paging

import com.ireader.engines.txt.TxtEngineConfig
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.Locator
import com.ireader.reader.model.LocatorSchemes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.io.path.createTempDirectory

class TxtLastPositionStoreTest {

    @Test
    fun save_and_load_locator_round_trip() = runBlocking {
        val dir = createTempDirectory(prefix = "txt-lastpos-").toFile()
        try {
            val store = TxtLastPositionStore(
                config = TxtEngineConfig(
                    cacheDir = dir,
                    persistLastPosition = true
                ),
                docNamespace = "doc-1",
                charsetName = "UTF-8"
            )
            val key = RenderKey.of(
                docId = "doc-1",
                charset = "UTF-8",
                constraints = LayoutConstraints(
                    viewportWidthPx = 1080,
                    viewportHeightPx = 1920,
                    density = 3f,
                    fontScale = 1f
                ),
                config = RenderConfig.ReflowText()
            )
            val locator = Locator(
                scheme = LocatorSchemes.TXT_OFFSET,
                value = "120",
                extras = mapOf(
                    "progression" to "0.100000",
                    "snippet" to "hello world"
                )
            )

            store.save(key, locator)
            val restored = store.load(key)

            assertEquals(locator.scheme, restored?.scheme)
            assertEquals(locator.value, restored?.value)
            assertEquals(locator.extras, restored?.extras)
        } finally {
            dir.deleteRecursively()
        }
    }
}
