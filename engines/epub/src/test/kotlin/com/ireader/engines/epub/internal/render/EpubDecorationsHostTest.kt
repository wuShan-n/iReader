package com.ireader.engines.epub.internal.render

import java.lang.reflect.Proxy
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.shared.publication.Locator as ReadiumLocator

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EpubDecorationsHostTest {

    @Test
    fun `applyAll should buffer when unbound and replay on bind`() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        val host = EpubDecorationsHost(mainDispatcher = mainDispatcher)
        val calls = mutableListOf<Pair<String, Int>>()
        val navigator = recordingNavigator(calls)

        host.applyAll(
            mapOf(
                "group.alpha" to listOf(decoration("a1"))
            )
        )
        advanceUntilIdle()
        assertTrue(calls.isEmpty())

        host.bind(navigator)
        advanceUntilIdle()

        assertEquals(listOf("group.alpha" to 1), calls)
    }

    @Test
    fun `applyAll should not write to stale navigator after unbind`() = runTest {
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        val host = EpubDecorationsHost(mainDispatcher = mainDispatcher)
        val staleCalls = mutableListOf<Pair<String, Int>>()
        val freshCalls = mutableListOf<Pair<String, Int>>()
        val staleNavigator = recordingNavigator(staleCalls)
        val freshNavigator = recordingNavigator(freshCalls)

        host.bind(staleNavigator)
        advanceUntilIdle()

        val job = launch {
            host.applyAll(
                mapOf(
                    "group.beta" to listOf(decoration("b1"))
                )
            )
        }

        host.unbind()
        advanceUntilIdle()
        job.join()

        assertTrue(staleCalls.isEmpty())

        host.bind(freshNavigator)
        advanceUntilIdle()
        assertEquals(listOf("group.beta" to 1), freshCalls)
    }

    private fun recordingNavigator(calls: MutableList<Pair<String, Int>>): DecorableNavigator {
        val loader = DecorableNavigator::class.java.classLoader
        val interfaces = arrayOf(DecorableNavigator::class.java)
        return Proxy.newProxyInstance(loader, interfaces) { _, method, args ->
            if (method.name == "applyDecorations") {
                @Suppress("UNCHECKED_CAST")
                val decorations = args?.get(0) as? List<Decoration> ?: emptyList()
                val group = args?.get(1) as? String ?: ""
                calls += group to decorations.size
                Unit
            } else {
                defaultValue(method.returnType)
            }
        } as DecorableNavigator
    }

    private fun decoration(id: String): Decoration {
        val locator = ReadiumLocator(
            href = checkNotNull(Url("https://example.com/chapter.xhtml")),
            mediaType = checkNotNull(MediaType("text/html")),
            locations = ReadiumLocator.Locations(
                fragments = listOf(id),
                progression = 0.1,
                totalProgression = 0.1
            )
        )
        return Decoration(
            id = id,
            locator = locator,
            style = Decoration.Style.Highlight(tint = 0x66FFF59D)
        )
    }

    private fun defaultValue(returnType: Class<*>): Any? {
        return when (returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> '\u0000'
            java.lang.Void.TYPE -> Unit
            else -> null
        }
    }
}
