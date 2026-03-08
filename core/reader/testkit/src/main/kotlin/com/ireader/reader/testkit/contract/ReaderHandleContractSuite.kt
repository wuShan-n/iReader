package com.ireader.reader.testkit.contract

import com.ireader.reader.api.engine.TextBreakPatchDirection
import com.ireader.reader.api.engine.TextBreakPatchState
import com.ireader.reader.api.error.ReaderResult
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.api.render.RenderPolicy
import com.ireader.reader.model.annotation.AnnotationAnchor
import com.ireader.reader.model.annotation.AnnotationDraft
import com.ireader.reader.model.annotation.AnnotationType
import com.ireader.reader.model.LocatorRange
import com.ireader.reader.testkit.requireOk
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

abstract class ReaderHandleContractSuite {
    protected abstract fun createHarness(): ReaderContractHarness

    @Test
    fun `reflow sessions should require viewport binding before render`() {
        runBlocking {
            val harness = createHarness()
            try {
                val handle = harness.openSession()
                try {
                    if (handle.capabilities.reflowable) {
                        assertTrue(handle.render(RenderPolicy.Default) is ReaderResult.Err)
                    }

                    handle.bindViewport(harness.defaultLayout, harness.defaultLayouterFactory).requireOk()
                    handle.render(RenderPolicy.Default).requireOk()
                } finally {
                    handle.close()
                }
            } finally {
                harness.close()
            }
        }
    }

    @Test
    fun `navigation should succeed after viewport binding`() {
        runBlocking {
            val harness = createHarness()
            try {
                val handle = harness.openSession()
                try {
                    handle.bindViewport(harness.defaultLayout, harness.defaultLayouterFactory).requireOk()

                    val first = handle.render().requireOk()
                    val next = handle.next().requireOk()
                    val prev = handle.prev().requireOk()
                    val target = harness.locatorAt(harness.selectionEndOffset)
                    val goTo = handle.goTo(target).requireOk()
                    val byProgress = handle.goToProgress(0.5).requireOk()

                    assertFalse(first.id.value.isBlank())
                    assertFalse(next.id.value.isBlank())
                    assertFalse(prev.id.value.isBlank())
                    assertTrue(goTo.locator.value.isNotBlank())
                    assertTrue(byProgress.locator.value.isNotBlank())
                } finally {
                    handle.close()
                }
            } finally {
                harness.close()
            }
        }
    }

    @Test
    fun `outline and search should follow declared capabilities`() {
        runBlocking {
            val harness = createHarness()
            try {
                val handle = harness.openSession()
                try {
                    handle.bindViewport(harness.defaultLayout, harness.defaultLayouterFactory).requireOk()
                    handle.render().requireOk()
                    delay(400L)

                    if (handle.capabilities.outline) {
                        val outline = handle.getOutline().requireOk()
                        val expectedTitle = harness.expectedOutlineTitle
                        if (expectedTitle != null) {
                            assertTrue(outlineContains(outline, expectedTitle))
                        }
                    } else {
                        assertTrue(handle.getOutline() is ReaderResult.Err)
                    }

                    val searchResult = withTimeout(10.seconds) {
                        handle.search(
                            query = harness.searchQuery,
                            options = com.ireader.reader.api.provider.SearchOptions(maxHits = 1)
                        ).first()
                    }
                    if (handle.capabilities.search) {
                        assertTrue(searchResult is ReaderResult.Ok)
                        val hit = (searchResult as ReaderResult.Ok).value
                        val expectedExcerpt = harness.expectedSearchExcerpt
                        if (expectedExcerpt != null) {
                            assertTrue(hit.excerpt.contains(expectedExcerpt))
                        }
                    } else {
                        assertTrue(searchResult is ReaderResult.Err)
                    }
                } finally {
                    handle.close()
                }
            } finally {
                harness.close()
            }
        }
    }

    @Test
    fun `selection annotation and break patch should respect supported capabilities`() {
        runBlocking {
            val harness = createHarness()
            try {
                val handle = harness.openSession()
                try {
                    handle.bindViewport(harness.defaultLayout, harness.defaultLayouterFactory).requireOk()
                    handle.render().requireOk()

                    if (handle.capabilities.selection) {
                        val start = harness.locatorAt(harness.selectionStartOffset)
                        val end = harness.locatorAt(harness.selectionEndOffset)
                        handle.startSelection(start).requireOk()
                        handle.updateSelection(end).requireOk()
                        handle.finishSelection().requireOk()
                        val selection = handle.currentSelection().requireOk()
                        assertNotNull(selection)
                        if (handle.capabilities.annotations) {
                            handle.createAnnotation(selection.toDraft()).requireOk()
                        }
                        handle.clearSelection().requireOk()
                    }

                    if (handle.supportsTextBreakPatches) {
                        val currentLocator = handle.state.value.locator
                        val patched = handle.applyBreakPatch(
                            locator = currentLocator,
                            direction = TextBreakPatchDirection.NEXT,
                            state = TextBreakPatchState.SOFT_SPACE
                        ).requireOk()
                        assertFalse(patched.id.value.isBlank())
                        val cleared = handle.clearBreakPatches().requireOk()
                        assertFalse(cleared.id.value.isBlank())
                    }
                } finally {
                    handle.close()
                }
            } finally {
                harness.close()
            }
        }
    }

    private fun SelectionProvider.Selection?.toDraft(): AnnotationDraft {
        requireNotNull(this) { "Selection must be available before creating an annotation" }
        val start = start ?: locator
        val end = end ?: locator
        return AnnotationDraft(
            type = AnnotationType.HIGHLIGHT,
            anchor = AnnotationAnchor.ReflowRange(
                range = LocatorRange(
                    start = start,
                    end = end
                )
            )
        )
    }

    private fun outlineContains(
        nodes: List<com.ireader.reader.model.OutlineNode>,
        title: String
    ): Boolean {
        return nodes.any { node ->
            node.title.contains(title) || outlineContains(node.children, title)
        }
    }
}
