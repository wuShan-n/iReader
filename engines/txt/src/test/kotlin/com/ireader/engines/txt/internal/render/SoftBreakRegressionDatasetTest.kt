package com.ireader.engines.txt.internal.render

import com.ireader.engines.common.android.reflow.SoftBreakProcessor
import com.ireader.engines.common.android.reflow.SoftBreakRuleConfig
import com.ireader.engines.common.android.reflow.SoftBreakTuningProfile
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftBreakRegressionDatasetTest {

    @Test
    fun `balanced profile should satisfy regression baseline`() {
        regressionSamples().forEach { sample ->
            val text = sample.lines.joinToString(separator = "\n")
            val expectedSoftOffsets = softOffsetsByBoundary(text, sample.lines, sample.expectedSoftBoundaries)

            val output = SoftBreakProcessor.process(
                rawText = text,
                hardWrapLikely = sample.hardWrapLikely,
                paragraphSpacingPx = 0,
                paragraphIndentPx = 0,
                startsAtParagraphBoundary = true,
                ruleConfig = SoftBreakRuleConfig.forProfile(sample.profile)
            ).toString()

            val predictedSoftOffsets = softOffsetsFromOutput(text, output)
            val f1 = f1Score(
                predicted = predictedSoftOffsets,
                expected = expectedSoftOffsets
            )

            assertTrue(
                "sample=${sample.name} profile=${sample.profile} f1=$f1 expected>=${sample.minF1} predicted=$predictedSoftOffsets expected=$expectedSoftOffsets",
                f1 >= sample.minF1
            )
        }
    }

    private fun regressionSamples(): List<RegressionSample> {
        return listOf(
            RegressionSample(
                name = "hard-wrap-prose",
                hardWrapLikely = true,
                profile = SoftBreakTuningProfile.BALANCED,
                lines = listOf(
                    "这是固定宽度硬换行样本第一行用于回归测试并保持长度稳定",
                    "这是固定宽度硬换行样本第二行用于回归测试并保持长度稳定",
                    "这是固定宽度硬换行样本第三行用于回归测试并保持长度稳定",
                    "这是固定宽度硬换行样本第四行用于回归测试并保持长度稳定"
                ),
                expectedSoftBoundaries = setOf(0, 1, 2),
                minF1 = 1.0
            ),
            RegressionSample(
                name = "normal-paragraphs",
                hardWrapLikely = false,
                profile = SoftBreakTuningProfile.BALANCED,
                lines = listOf(
                    "第一段在句号处结束。",
                    "第二段另起一行并以句号结束。",
                    "",
                    "第三段在空行后继续。"
                ),
                expectedSoftBoundaries = emptySet(),
                minF1 = 1.0
            ),
            RegressionSample(
                name = "indent-increase-boundary",
                hardWrapLikely = true,
                profile = SoftBreakTuningProfile.BALANCED,
                lines = listOf(
                    "这是普通叙述文本用于验证缩进突增时应保留段落边界并继续描述",
                    "    下一行缩进明显增加用于模拟新段落起始位置",
                    "    同级缩进继续叙述可按软换行合并到同一段"
                ),
                expectedSoftBoundaries = setOf(1),
                minF1 = 1.0
            ),
            RegressionSample(
                name = "list-text-keep-hard-breaks",
                hardWrapLikely = false,
                profile = SoftBreakTuningProfile.BALANCED,
                lines = listOf(
                    "- 清单第一项需要逐行展示",
                    "- 清单第二项需要逐行展示",
                    "- 清单第三项需要逐行展示"
                ),
                expectedSoftBoundaries = emptySet(),
                minF1 = 1.0
            )
        )
    }

    private fun softOffsetsByBoundary(
        text: String,
        lines: List<String>,
        softBoundaries: Set<Int>
    ): Set<Int> {
        if (lines.isEmpty() || softBoundaries.isEmpty()) {
            return emptySet()
        }
        val offsets = newlineOffsets(text)
        return softBoundaries.mapNotNull { boundary ->
            offsets.getOrNull(boundary)
        }.toSet()
    }

    private fun softOffsetsFromOutput(raw: String, processed: String): Set<Int> {
        val newlineOffsets = newlineOffsets(raw)
        return newlineOffsets.filter { offset ->
            offset in processed.indices && processed[offset] == ' '
        }.toSet()
    }

    private fun newlineOffsets(text: String): List<Int> {
        val offsets = ArrayList<Int>(text.length / 60)
        text.forEachIndexed { index, ch ->
            if (ch == '\n') {
                offsets += index
            }
        }
        return offsets
    }

    private fun f1Score(predicted: Set<Int>, expected: Set<Int>): Double {
        if (predicted.isEmpty() && expected.isEmpty()) {
            return 1.0
        }
        val tp = predicted.count(expected::contains).toDouble()
        val precision = if (predicted.isEmpty()) 0.0 else tp / predicted.size.toDouble()
        val recall = if (expected.isEmpty()) 0.0 else tp / expected.size.toDouble()
        if (precision + recall == 0.0) {
            return 0.0
        }
        return 2.0 * precision * recall / (precision + recall)
    }

    private data class RegressionSample(
        val name: String,
        val hardWrapLikely: Boolean,
        val profile: SoftBreakTuningProfile,
        val lines: List<String>,
        val expectedSoftBoundaries: Set<Int>,
        val minF1: Double
    )
}
