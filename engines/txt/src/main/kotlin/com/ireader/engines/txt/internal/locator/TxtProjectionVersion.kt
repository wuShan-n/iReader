package com.ireader.engines.txt.internal.locator

import com.ireader.engines.common.android.reflow.SoftBreakRuleConfig
import com.ireader.engines.common.android.reflow.SoftBreakTuningProfile
import com.ireader.engines.txt.internal.open.TxtBookFiles
import com.ireader.engines.txt.internal.open.TxtMeta
import java.io.File
import java.security.MessageDigest

internal object TxtProjectionVersion {
    private const val DIGEST_PREFIX_LENGTH = 16

    fun current(files: TxtBookFiles, meta: TxtMeta): String {
        val rulesVersion = SoftBreakRuleConfig.forProfile(SoftBreakTuningProfile.BALANCED).rulesVersion
        return compute(
            meta = meta,
            rulesVersion = rulesVersion,
            breakPatchHash = breakPatchHash(files.breakPatch)
        )
    }

    fun compute(
        meta: TxtMeta,
        rulesVersion: Int,
        breakPatchHash: String
    ): String {
        val payload = buildString {
            append(meta.contentRevision)
            append(':')
            append(rulesVersion)
            append(':')
            append(breakPatchHash)
        }
        return digestHex(payload.toByteArray(Charsets.UTF_8)).take(DIGEST_PREFIX_LENGTH)
    }

    private fun breakPatchHash(file: File): String {
        if (!file.exists()) {
            return digestHex(ByteArray(0)).take(DIGEST_PREFIX_LENGTH)
        }
        val bytes = runCatching { file.readBytes() }.getOrElse { ByteArray(0) }
        return digestHex(bytes).take(DIGEST_PREFIX_LENGTH)
    }

    private fun digestHex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return buildString(digest.size * 2) {
            digest.forEach { append("%02x".format(it)) }
        }
    }
}
