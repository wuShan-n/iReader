package com.ireader.engines.txt.internal.controller

import com.ireader.engines.txt.TxtEngineConfig

internal data class LocatorConfig(
    val snippetLength: Int = 48,
    val sampleStrideChars: Int = 32 * 1024,
    val sampleWindowChars: Int = 512,
    val maxSamples: Int = 512,
    val smallDocumentFullScanThresholdChars: Int = 600_000,
    val snippetWindowMinChars: Int = 4_096,
    val snippetWindowMaxChars: Int = 256_000,
    val snippetWindowCapChars: Int = 1_000_000
)

internal fun TxtEngineConfig.toLocatorConfig(): LocatorConfig {
    return LocatorConfig(
        snippetLength = snippetLength,
        sampleStrideChars = locatorSampleStrideChars,
        sampleWindowChars = locatorSampleWindowChars,
        maxSamples = locatorMaxSamples,
        smallDocumentFullScanThresholdChars = locatorSmallDocumentFullScanThresholdChars,
        snippetWindowMinChars = locatorSnippetWindowMinChars,
        snippetWindowMaxChars = locatorSnippetWindowMaxChars,
        snippetWindowCapChars = locatorSnippetWindowCapChars
    )
}
