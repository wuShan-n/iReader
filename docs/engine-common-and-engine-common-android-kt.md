# engine-common & engine-common-android Kotlin Sources

> Auto-copied from `engines/engine-common` and `engines/engine-common-android` (excluding tests).

## `engines/engine-common-android\src\main\kotlin\com\ireader\engines\common\android\controller\BaseCoroutineReaderController.kt`

```kotlin
package com.ireader.engines.common.android.controller

import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.api.render.ReaderEvent
import com.ireader.reader.api.render.RenderState
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

abstract class BaseCoroutineReaderController(
    initialState: RenderState,
    dispatcher: CoroutineDispatcher
) : ReaderController {

    protected val mutex: Mutex = Mutex()
    protected val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

    private val closed = AtomicBoolean(false)

    protected val eventsMutable = MutableSharedFlow<ReaderEvent>(extraBufferCapacity = 32)
    override val events: Flow<ReaderEvent> = eventsMutable.asSharedFlow()

    protected val stateMutable = MutableStateFlow(initialState)
    override val state: StateFlow<RenderState> = stateMutable.asStateFlow()

    protected fun launchSafely(
        name: String,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return scope.launch {
            try {
                block()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                onCoroutineError(name, t)
            }
        }
    }

    protected open fun onCoroutineError(name: String, throwable: Throwable) = Unit

    protected open fun onClose() = Unit

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { onClose() }
        scope.cancel()
    }
}
```

## `engines/engine-common-android\src\main\kotlin\com\ireader\engines\common\android\error\ReaderErrorMapping.kt`

```kotlin
package com.ireader.engines.common.android.error

import com.ireader.reader.api.error.ReaderError
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.zip.ZipException

fun Throwable.toReaderError(
    invalidPasswordKeywords: Set<String> = emptySet(),
    preserveInternalMessage: Boolean = true
): ReaderError {
    val lowerMessage = (message ?: "").lowercase(Locale.US)
    return when (this) {
        is ReaderError -> this
        is CancellationException -> ReaderError.Cancelled(cause = this)
        is FileNotFoundException -> ReaderError.NotFound(cause = this)
        is SecurityException -> ReaderError.PermissionDenied(cause = this)
        is ZipException -> ReaderError.CorruptOrInvalid(cause = this)
        is IOException -> ReaderError.Io(cause = this)
        else -> {
            val invalidPassword = invalidPasswordKeywords.any { keyword ->
                keyword.isNotBlank() && lowerMessage.contains(keyword.lowercase(Locale.US))
            }
            if (invalidPassword) {
                ReaderError.InvalidPassword(cause = this)
            } else if (preserveInternalMessage) {
                ReaderError.Internal(message = message, cause = this)
            } else {
                ReaderError.Internal(cause = this)
            }
        }
    }
}
```

## `engines/engine-common-android\src\main\kotlin\com\ireader\engines\common\android\id\SourceDocumentIds.kt`

```kotlin
package com.ireader.engines.common.android.id

import com.ireader.core.files.source.DocumentSource
import com.ireader.engines.common.id.DocumentIds
import com.ireader.reader.model.DocumentId

object SourceDocumentIds {

    fun fromSourceSha1(
        prefix: String,
        source: DocumentSource,
        extraParts: List<String> = emptyList()
    ): DocumentId {
        val raw = buildRaw(source, extraParts)
        return DocumentIds.fromSha1(prefix = prefix, raw = raw)
    }

    fun fromSourceSha256(
        source: DocumentSource,
        length: Int = 64,
        prefix: String? = null,
        extraParts: List<String> = emptyList()
    ): DocumentId {
        val raw = buildRaw(source, extraParts)
        return DocumentIds.fromSha256(raw = raw, length = length, prefix = prefix)
    }

    private fun buildRaw(source: DocumentSource, extraParts: List<String>): String {
        return buildString {
            append(source.uri.toString())
            append('|')
            append(source.displayName.orEmpty())
            append('|')
            append(source.sizeBytes ?: -1L)
            append('|')
            append(source.mimeType.orEmpty())
            for (part in extraParts) {
                append('|')
                append(part)
            }
        }
    }
}
```

## `engines/engine-common-android\src\main\kotlin\com\ireader\engines\common\android\layout\StaticLayoutMeasurer.kt`

```kotlin
@file:Suppress("LongParameterList")

package com.ireader.engines.common.android.layout

import android.graphics.text.LineBreakConfig
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
import com.ireader.core.common.android.typography.resolveAndroidLineBreakConfig
import com.ireader.core.common.android.typography.toAndroidBreakStrategy
import com.ireader.core.common.android.typography.toAndroidHyphenationFrequency
import com.ireader.core.common.android.typography.toAndroidJustificationMode
import com.ireader.core.common.android.typography.toAndroidLayoutAlignment
import com.ireader.reader.api.render.BreakStrategyMode
import com.ireader.reader.api.render.HyphenationMode
import com.ireader.reader.api.render.TextAlignMode

data class MeasureResult(
    val endChar: Int,
    val lineCount: Int,
    val lastVisibleLine: Int
)

object StaticLayoutMeasurer {

    fun measure(
        text: CharSequence,
        paint: TextPaint,
        widthPx: Int,
        heightPx: Int,
        lineHeightMult: Float,
        textAlign: TextAlignMode,
        breakStrategy: BreakStrategyMode,
        hyphenationMode: HyphenationMode,
        includeFontPadding: Boolean,
        preferInterCharacterJustify: Boolean
    ): MeasureResult {
        val builder = StaticLayout.Builder.obtain(
            text,
            0,
            text.length,
            paint,
            widthPx.coerceAtLeast(1)
        )
            .setAlignment(textAlign.toAndroidLayoutAlignment())
            .setIncludePad(includeFontPadding)
            .setLineSpacing(0f, lineHeightMult)
            .setHyphenationFrequency(hyphenationMode.toAndroidHyphenationFrequency())
            .setBreakStrategy(breakStrategy.toAndroidBreakStrategy())

        runCatching {
            StaticLayout.Builder::class.java
                .getMethod("setUseLineSpacingFromFallbacks", Boolean::class.javaPrimitiveType)
                .invoke(builder, true)
        }

        runCatching {
            builder.setJustificationMode(
                textAlign.toAndroidJustificationMode(
                    preferInterCharacter = preferInterCharacterJustify
                )
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            resolveAndroidLineBreakConfig(preferInterCharacterJustify)?.let { config ->
                runCatching {
                    builder.setLineBreakConfig(
                        LineBreakConfig.Builder()
                            .setLineBreakStyle(config.lineBreakStyle)
                            .setLineBreakWordStyle(config.lineBreakWordStyle)
                            .build()
                    )
                }
            }
        }

        val layout = builder.build()

        if (layout.lineCount == 0) {
            return MeasureResult(
                endChar = 0,
                lineCount = 0,
                lastVisibleLine = -1
            )
        }

        val contentHeight = heightPx.coerceAtLeast(1)
        var lastVisibleLine = -1
        var line = 0
        while (line < layout.lineCount) {
            if (layout.getLineBottom(line) <= contentHeight) {
                lastVisibleLine = line
                line++
            } else {
                break
            }
        }
        if (lastVisibleLine < 0) {
            lastVisibleLine = 0
        }
        val end = layout.getLineEnd(lastVisibleLine).coerceAtLeast(0).coerceAtMost(text.length)
        return MeasureResult(
            endChar = end,
            lineCount = lastVisibleLine + 1,
            lastVisibleLine = lastVisibleLine
        )
    }

}
```

## `engines/engine-common-android\src\main\kotlin\com\ireader\engines\common\android\layout\TextPaintFactory.kt`

```kotlin
package com.ireader.engines.common.android.layout

import android.graphics.Color
import android.graphics.Typeface
import android.text.TextPaint
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig

object TextPaintFactory {

    fun create(
        config: RenderConfig.ReflowText,
        constraints: LayoutConstraints
    ): TextPaint {
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)
        val textSizePx = config.fontSizeSp * constraints.density * constraints.fontScale
        paint.textSize = textSizePx
        paint.color = Color.BLACK
        val family = config.fontFamilyName
        if (!family.isNullOrBlank()) {
            paint.typeface = Typeface.create(family, Typeface.NORMAL)
        }
        return paint
    }
}
```

## `engines/engine-common-android\src\main\kotlin\com\ireader\engines\common\android\pagination\ReflowPaginationProfile.kt`

```kotlin
package com.ireader.engines.common.android.pagination

import com.ireader.engines.common.hash.Hashing
import com.ireader.engines.common.android.reflow.SOFT_BREAK_PROFILE_EXTRA_KEY
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig

object ReflowPaginationProfile {

    private const val PROFILE_SCHEMA_VERSION = 9

    fun keyFor(
        documentKey: String,
        constraints: LayoutConstraints,
        config: RenderConfig.ReflowText,
        keyLength: Int = 16
    ): String {
        val raw = buildString {
            append("v")
            append(PROFILE_SCHEMA_VERSION)
            append('|')
            append(documentKey)
            append('|')
            append(constraints.viewportWidthPx)
            append('x')
            append(constraints.viewportHeightPx)
            append('|')
            append(constraints.density)
            append('|')
            append(constraints.fontScale)
            append('|')
            append(config.fontSizeSp)
            append('|')
            append(config.lineHeightMult)
            append('|')
            append(config.paragraphSpacingDp)
            append('|')
            append(config.pagePaddingDp)
            append('|')
            append(config.fontFamilyName.orEmpty())
            append('|')
            append(config.breakStrategy)
            append('|')
            append(config.hyphenationMode)
            append('|')
            append(config.includeFontPadding)
            append('|')
            append(config.pageInsetMode)
            append('|')
            append(config.extra[SOFT_BREAK_PROFILE_EXTRA_KEY].orEmpty().trim().lowercase())
        }
        return Hashing.sha256Hex(raw).take(keyLength.coerceAtLeast(1))
    }
}
```

## `engines/engine-common-android\src\main\kotlin\com\ireader\engines\common\android\reflow\ParagraphSpacingSpan.kt`

```kotlin
package com.ireader.engines.common.android.reflow

import android.graphics.Paint
import android.text.style.LineHeightSpan

class ParagraphSpacingSpan(
    private val extraPx: Int
) : LineHeightSpan {
    override fun chooseHeight(
        text: CharSequence,
        start: Int,
        end: Int,
        spanstartv: Int,
        v: Int,
        fm: Paint.FontMetricsInt
    ) {
        if (extraPx <= 0) {
            return
        }
        if (end > 0 && text[end - 1] == '\n') {
            fm.descent += extraPx
            fm.bottom += extraPx
        }
    }
}
```

## `engines/engine-common-android\src\main\kotlin\com\ireader\engines\common\android\reflow\ReflowPageSlice.kt`

```kotlin
package com.ireader.engines.common.android.reflow

data class ReflowPageSlice(
    val startOffset: Long,
    val endOffset: Long,
    val text: CharSequence
)

```

## `engines/engine-common-android\src\main\kotlin\com\ireader\engines\common\android\reflow\ReflowPageSliceCache.kt`

```kotlin
package com.ireader.engines.common.android.reflow

import com.ireader.engines.common.cache.LruCache
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig

class ReflowPageSliceCache(
    private val paginator: ReflowPaginator,
    maxPageCache: Int,
    private val maxOffsetProvider: () -> Long
) {
    private val pageCache = LruCache<Long, ReflowPageSlice>(maxPageCache)

    fun clear() {
        pageCache.clear()
    }

    fun getCached(start: Long): ReflowPageSlice? {
        return pageCache[start.normalized()]
    }

    suspend fun getOrBuild(
        start: Long,
        constraints: LayoutConstraints,
        config: RenderConfig.ReflowText,
        allowCache: Boolean
    ): ReflowPageSlice {
        val normalizedStart = start.normalized()
        if (allowCache) {
            val cached = pageCache[normalizedStart]
            if (cached != null) {
                return cached
            }
        }
        val computed = paginator.pageAt(
            startOffset = normalizedStart,
            config = config,
            constraints = constraints
        )
        pageCache[normalizedStart] = computed
        return computed
    }

    private fun Long.normalized(): Long {
        val maxOffset = maxOffsetProvider()
        return coerceIn(0L, maxOffset)
    }
}

```

## `engines/engine-common-android\src\main\kotlin\com\ireader\engines\common\android\reflow\ReflowPaginationIndexStore.kt`

```kotlin
package com.ireader.engines.common.android.reflow

import com.ireader.engines.common.android.pagination.ReflowPaginationProfile
import com.ireader.engines.common.pagination.PageMap
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.model.LocatorExtraKeys
import java.io.File
import java.util.TreeSet
import kotlin.math.roundToInt

class ReflowPaginationIndexStore(
    private val enabled: Boolean,
    private val documentKey: String,
    private val paginationDir: File
) {
    private var profileKey: String? = null
    private var pageStarts = sortedSetOf<Long>()
    private var dirty = false

    fun hasActiveProfile(): Boolean {
        return enabled && !profileKey.isNullOrBlank()
    }

    fun knownPageCount(): Int = pageStarts.size

    fun lastKnownStart(): Long? = pageStarts.lastOrNull()

    fun startForProgress(percent: Double): Long? {
        if (pageStarts.size < 8) return null
        val index = ((pageStarts.size - 1) * percent.coerceIn(0.0, 1.0))
            .roundToInt()
            .coerceIn(0, pageStarts.size - 1)
        return pageStarts.elementAt(index)
    }

    fun locatorExtras(maxAnchors: Int = 96): Map<String, String> {
        if (!hasActiveProfile()) return emptyMap()
        val profile = profileKey ?: return emptyMap()
        val anchors = sampledAnchors(maxAnchors = maxAnchors)
        if (anchors.isEmpty()) {
            return mapOf(LocatorExtraKeys.REFLOW_PAGE_PROFILE to profile)
        }
        return mapOf(
            LocatorExtraKeys.REFLOW_PAGE_PROFILE to profile,
            LocatorExtraKeys.REFLOW_PAGE_ANCHORS to anchors.joinToString(separator = ",")
        )
    }

    fun mergeLocatorAnchors(locatorExtras: Map<String, String>) {
        if (!hasActiveProfile()) return
        val profile = locatorExtras[LocatorExtraKeys.REFLOW_PAGE_PROFILE] ?: return
        if (profile != profileKey) return
        val serialized = locatorExtras[LocatorExtraKeys.REFLOW_PAGE_ANCHORS] ?: return
        val parsed = serialized
            .split(',')
            .asSequence()
            .map { it.trim() }
            .mapNotNull { it.toLongOrNull() }
            .filter { it >= 0L }
            .toList()
        if (parsed.isEmpty()) return
        val before = pageStarts.size
        pageStarts.addAll(parsed)
        if (pageStarts.size != before) {
            dirty = true
        }
    }

    fun reloadIfNeeded(
        constraints: LayoutConstraints?,
        profileConfig: RenderConfig.ReflowText
    ) {
        if (!enabled) {
            clearState()
            return
        }
        val currentConstraints = constraints ?: return
        val nextProfile = ReflowPaginationProfile.keyFor(
            documentKey = documentKey,
            constraints = currentConstraints,
            config = profileConfig
        )
        if (nextProfile == profileKey) {
            return
        }
        saveIfDirty()
        profileKey = nextProfile
        pageStarts = loadPageStarts(nextProfile)
        dirty = false
    }

    fun record(start: Long) {
        if (!hasActiveProfile()) {
            return
        }
        if (pageStarts.add(start.coerceAtLeast(0L))) {
            dirty = true
            if (pageStarts.size % 16 == 0) {
                saveIfDirty()
            }
        }
    }

    fun saveIfDirty() {
        if (!hasActiveProfile() || !dirty) {
            return
        }
        val key = profileKey ?: return
        val file = profileFile(key)
        file.parentFile?.mkdirs()
        PageMap.save(file, pageStarts)
        dirty = false
    }

    fun invalidateProfile() {
        profileKey = null
        pageStarts.clear()
        dirty = false
    }

    private fun clearState() {
        profileKey = null
        pageStarts.clear()
        dirty = false
    }

    private fun sampledAnchors(maxAnchors: Int): List<Long> {
        if (pageStarts.isEmpty()) return emptyList()
        val target = maxAnchors.coerceAtLeast(1)
        if (pageStarts.size <= target) return pageStarts.toList()
        if (target == 1) return listOf(pageStarts.first())

        val values = pageStarts.toList()
        val step = (values.size - 1).toDouble() / (target - 1).toDouble()
        return buildList(target) {
            for (i in 0 until target) {
                val idx = (i * step).roundToInt().coerceIn(0, values.lastIndex)
                add(values[idx])
            }
        }.distinct()
    }

    private fun loadPageStarts(profile: String): TreeSet<Long> {
        return PageMap.load(
            binaryFile = profileFile(profile),
            legacyTextFile = legacyProfileFile(profile)
        )
    }

    private fun profileFile(profile: String): File {
        return File(paginationDir, "pagemap_v2_$profile.bin")
    }

    private fun legacyProfileFile(profile: String): File {
        return File(paginationDir, "pagemap_v2_$profile.txt")
    }
}
```

## `engines/engine-common-android\src\main\kotlin\com\ireader\engines\common\android\reflow\ReflowPaginator.kt`

```kotlin
@file:Suppress("LongMethod", "MagicNumber", "ReturnCount")

package com.ireader.engines.common.android.reflow

import android.text.SpannableStringBuilder
import android.util.Log
import com.ireader.core.common.android.typography.prefersInterCharacterJustify
import com.ireader.core.common.android.typography.effectiveForInterCharacterScript
import com.ireader.engines.common.android.layout.StaticLayoutMeasurer
import com.ireader.engines.common.android.layout.TextPaintFactory
import com.ireader.reader.api.render.LayoutConstraints
import com.ireader.reader.api.render.RenderConfig
import com.ireader.reader.api.render.TextAlignMode
import com.ireader.reader.api.render.toTypographySpec
import kotlin.math.min
import kotlin.math.roundToInt

fun interface ReflowPageEndAdjuster {
    fun adjust(
        raw: String,
        measuredEnd: Int,
        rawLength: Int,
        pageStartOffset: Long
    ): Int

    companion object {
        val NONE: ReflowPageEndAdjuster = ReflowPageEndAdjuster { _, measuredEnd, _, _ -> measuredEnd }
    }
}

class ReflowPaginator(
    private val source: ReflowTextSource,
    private val hardWrapLikely: Boolean,
    private var softBreakIndex: ReflowSoftBreakIndex? = null,
    private val pageEndAdjuster: ReflowPageEndAdjuster = ReflowPageEndAdjuster.NONE
) {

    fun setSoftBreakIndex(index: ReflowSoftBreakIndex?) {
        softBreakIndex = index
    }

    suspend fun pageAt(
        startOffset: Long,
        config: RenderConfig.ReflowText,
        constraints: LayoutConstraints
    ): ReflowPageSlice {
        if (source.lengthChars <= 0L) {
            return ReflowPageSlice(0L, 0L, "")
        }

        val start = startOffset.coerceIn(0L, source.lengthChars)
        if (start >= source.lengthChars) {
            return ReflowPageSlice(source.lengthChars, source.lengthChars, "")
        }

        val startsAtParagraphBoundary = startsAtParagraphBoundary(start)
        val typography = config.toTypographySpec()
        val paddingPx = (typography.pagePaddingDp * constraints.density).roundToInt()
        val width = (constraints.viewportWidthPx - paddingPx * 2).coerceAtLeast(1)
        val height = (constraints.viewportHeightPx - paddingPx * 2).coerceAtLeast(1)
        val paragraphSpacingPx = (typography.paragraphSpacingDp * constraints.density).roundToInt()
        val paint = TextPaintFactory.create(config, constraints)
        val paragraphIndentPx = 0
        val softBreakProfile = SoftBreakTuningProfile.fromStorageValue(config.extra[SOFT_BREAK_PROFILE_EXTRA_KEY])
        val softBreakRules = SoftBreakRuleConfig.forProfile(softBreakProfile)
        val softBreakSource = when {
            !hardWrapLikely -> "raw"
            softBreakIndex != null -> "index"
            else -> "runtime"
        }

        var windowChars = initialWindowChars(config, constraints)
        var measuredEnd = 0
        var measuredText: CharSequence = ""
        var rawLength = 0
        var measuredRaw = ""

        while (true) {
            val toRead = min(windowChars.toLong(), source.lengthChars - start).toInt()
            val raw = source.readString(start, toRead)
            rawLength = raw.length
            measuredRaw = raw
            if (rawLength == 0) {
                return ReflowPageSlice(start, start, "")
            }

            val display = when {
                !hardWrapLikely -> SoftBreakProcessor.renderRawPreservingBreaks(
                    rawText = raw,
                    paragraphSpacingPx = paragraphSpacingPx,
                    paragraphIndentPx = paragraphIndentPx,
                    startsAtParagraphBoundary = startsAtParagraphBoundary
                )

                softBreakIndex != null -> applySoftBreakIndex(
                    start = start,
                    raw = raw,
                    paragraphSpacingPx = paragraphSpacingPx,
                    paragraphIndentPx = paragraphIndentPx,
                    startsAtParagraphBoundary = startsAtParagraphBoundary
                )

                else -> SoftBreakProcessor.process(
                    rawText = raw,
                    hardWrapLikely = hardWrapLikely,
                    paragraphSpacingPx = paragraphSpacingPx,
                    paragraphIndentPx = paragraphIndentPx,
                    startsAtParagraphBoundary = startsAtParagraphBoundary,
                    ruleConfig = softBreakRules
                )
            }
            measuredText = display
            val preferInterCharacterJustify = raw.prefersInterCharacterJustify()
            val effectiveBreakStrategy = typography.breakStrategy
                .effectiveForInterCharacterScript(preferInterCharacterJustify)

            val measure = StaticLayoutMeasurer.measure(
                text = display,
                paint = paint,
                widthPx = width,
                heightPx = height,
                lineHeightMult = typography.lineHeightMult,
                textAlign = TextAlignMode.JUSTIFY,
                breakStrategy = effectiveBreakStrategy,
                hyphenationMode = typography.hyphenationMode,
                includeFontPadding = typography.includeFontPadding,
                preferInterCharacterJustify = preferInterCharacterJustify
            )
            measuredEnd = measure.endChar.coerceIn(0, rawLength)

            val consumedAllWindow = measuredEnd >= rawLength
            val reachedDocumentEnd = start + rawLength >= source.lengthChars
            if (!consumedAllWindow || reachedDocumentEnd || windowChars >= MAX_WINDOW_CHARS) {
                break
            }
            windowChars = (windowChars * 2).coerceAtMost(MAX_WINDOW_CHARS)
        }

        val measuredEndBeforeAdjust = measuredEnd
        if (measuredEnd in 1 until rawLength) {
            measuredEnd = pageEndAdjuster.adjust(
                raw = measuredRaw,
                measuredEnd = measuredEnd,
                rawLength = rawLength,
                pageStartOffset = start
            ).coerceIn(1, measuredEnd)
        }
        var end = start + measuredEnd.toLong()
        if (end <= start) {
            end = (start + 1L).coerceAtMost(source.lengthChars)
            measuredEnd = (end - start).toInt()
            measuredText = measuredText.subSequence(0, measuredEnd)
        } else {
            measuredText = measuredText.subSequence(0, measuredEnd)
        }

        if (isDebugLoggingEnabled()) {
            val newlineStats = collectNewlineStats(
                raw = measuredRaw,
                display = measuredText,
                endExclusive = measuredEnd
            )
            logDebug(
                TAG,
                "pageAt start=$start end=$end source=$softBreakSource " +
                    "hardWrapLikely=$hardWrapLikely softBreakProfile=${softBreakProfile.storageValue} " +
                    "newlineSoft=${newlineStats.softBreaks} newlineHard=${newlineStats.hardBreaks} " +
                    "measuredEnd=$measuredEndBeforeAdjust adjustedEnd=$measuredEnd " +
                    "rewind=${measuredEndBeforeAdjust - measuredEnd}"
            )
        }

        return ReflowPageSlice(
            startOffset = start,
            endOffset = end,
            text = measuredText
        )
    }

    private fun applySoftBreakIndex(
        start: Long,
        raw: String,
        paragraphSpacingPx: Int,
        paragraphIndentPx: Int,
        startsAtParagraphBoundary: Boolean
    ): CharSequence {
        val builder = SpannableStringBuilder(raw)
        val hardBreakPositions = ArrayList<Int>()
        val end = start + raw.length.toLong()
        softBreakIndex?.forEachNewlineInRange(start, end) { offset, isSoft ->
            val local = (offset - start).toInt()
            if (local !in 0 until builder.length) {
                return@forEachNewlineInRange
            }
            if (isSoft) {
                builder.replace(local, local + 1, " ")
            } else {
                hardBreakPositions += local
            }
        }
        SoftBreakProcessor.decorateParagraphs(
            builder = builder,
            hardBreakPositions = hardBreakPositions.toIntArray(),
            paragraphSpacingPx = paragraphSpacingPx,
            paragraphIndentPx = paragraphIndentPx,
            startsAtParagraphBoundary = startsAtParagraphBoundary
        )
        return builder
    }

    private suspend fun startsAtParagraphBoundary(offset: Long): Boolean {
        if (offset <= 0L) {
            return true
        }
        val previous = source.readString(offset - 1L, 1)
        return previous.firstOrNull() == '\n'
    }

    private fun initialWindowChars(
        config: RenderConfig.ReflowText,
        constraints: LayoutConstraints
    ): Int {
        val area = constraints.viewportWidthPx.toLong() * constraints.viewportHeightPx.toLong()
        val scale = (config.fontSizeSp * constraints.fontScale).coerceAtLeast(10f)
        val rough = (area / (scale * 22f)).toInt()
        return rough.coerceIn(2_500, 16_000)
    }

    private fun collectNewlineStats(
        raw: String,
        display: CharSequence,
        endExclusive: Int
    ): NewlineStats {
        if (raw.isEmpty() || display.isEmpty() || endExclusive <= 0) {
            return NewlineStats(softBreaks = 0, hardBreaks = 0)
        }
        val limit = minOf(raw.length, display.length, endExclusive)
        var soft = 0
        var hard = 0
        for (i in 0 until limit) {
            if (raw[i] != '\n') {
                continue
            }
            if (display[i] == ' ') {
                soft++
            } else {
                hard++
            }
        }
        return NewlineStats(softBreaks = soft, hardBreaks = hard)
    }

    private data class NewlineStats(
        val softBreaks: Int,
        val hardBreaks: Int
    )

    companion object {
        private const val TAG = "ReflowPaginator"
        private const val MAX_WINDOW_CHARS = 96_000
        private const val MIN_TAIL_CHARS_FOR_REWIND = 14
        private const val MIN_SENTENCE_TAIL_CHARS_FOR_REWIND = 12
        private const val MIN_PARAGRAPH_CHARS_FOR_REWIND = 24
        private const val MAX_REWIND_CHARS = 240
        private val STRONG_SENTENCE_BREAKS = charArrayOf('。', '！', '？', '.', '!', '?', ';', '；', ':', '：')

        fun adjustMeasuredEndForParagraphTail(
            raw: String,
            measuredEnd: Int,
            rawLength: Int
        ): Int {
            val clampedEnd = measuredEnd.coerceIn(0, rawLength)
            if (clampedEnd <= 0 || clampedEnd >= rawLength) {
                return clampedEnd
            }
            val lineBreak = raw.lastIndexOf('\n', startIndex = clampedEnd - 1)
            if (lineBreak <= 0 || lineBreak >= clampedEnd) {
                return adjustMeasuredEndForSentenceTail(
                    raw = raw,
                    clampedEnd = clampedEnd,
                    rawLength = rawLength
                )
            }
            val previousBreak = raw.lastIndexOf('\n', startIndex = lineBreak - 1)
            val paragraphStart = previousBreak + 1
            val tailChars = clampedEnd - (lineBreak + 1)
            if (tailChars <= 0 || tailChars >= MIN_TAIL_CHARS_FOR_REWIND) {
                return adjustMeasuredEndForSentenceTail(
                    raw = raw,
                    clampedEnd = clampedEnd,
                    rawLength = rawLength
                )
            }
            val paragraphChars = clampedEnd - paragraphStart
            if (paragraphChars < MIN_PARAGRAPH_CHARS_FOR_REWIND) {
                return adjustMeasuredEndForSentenceTail(
                    raw = raw,
                    clampedEnd = clampedEnd,
                    rawLength = rawLength
                )
            }
            val candidate = lineBreak + 1
            if (clampedEnd - candidate > MAX_REWIND_CHARS) {
                return adjustMeasuredEndForSentenceTail(
                    raw = raw,
                    clampedEnd = clampedEnd,
                    rawLength = rawLength
                )
            }
            return candidate
        }

        private fun adjustMeasuredEndForSentenceTail(
            raw: String,
            clampedEnd: Int,
            rawLength: Int
        ): Int {
            if (clampedEnd <= 0 || clampedEnd >= rawLength) {
                return clampedEnd
            }
            val sentenceBreak = raw.lastIndexOfAny(
                chars = STRONG_SENTENCE_BREAKS,
                startIndex = clampedEnd - 1
            )
            if (sentenceBreak <= 0 || sentenceBreak >= clampedEnd) {
                return clampedEnd
            }
            val candidate = sentenceBreak + 1
            val tailChars = clampedEnd - candidate
            if (tailChars <= 0 || tailChars >= MIN_SENTENCE_TAIL_CHARS_FOR_REWIND) {
                return clampedEnd
            }
            val previousBreak = raw.lastIndexOf('\n', startIndex = sentenceBreak - 1)
            val blockStart = if (previousBreak >= 0) previousBreak + 1 else 0
            val blockChars = clampedEnd - blockStart
            if (blockChars < MIN_PARAGRAPH_CHARS_FOR_REWIND) {
                return clampedEnd
            }
            if (clampedEnd - candidate > MAX_REWIND_CHARS) {
                return clampedEnd
            }
            return candidate
        }

        private fun isDebugLoggingEnabled(): Boolean {
            return runCatching { Log.isLoggable(TAG, Log.DEBUG) }
                .getOrDefault(false)
        }

        private fun logDebug(tag: String, message: String) {
            runCatching { Log.d(tag, message) }
        }
    }
}
```

## `engines/engine-common-android\src\main\kotlin\com\ireader\engines\common\android\reflow\ReflowSoftBreakIndex.kt`

```kotlin
package com.ireader.engines.common.android.reflow

/**
 * Optional newline index for reflow pagination.
 *
 * Used to distinguish hard paragraph breaks from soft-wrapped line breaks.
 */
fun interface ReflowSoftBreakIndex {
    fun forEachNewlineInRange(
        startChar: Long,
        endChar: Long,
        consumer: (offset: Long, isSoft: Boolean) -> Unit
    )
}

```

## `engines/engine-common-android\src\main\kotlin\com\ireader\engines\common\android\reflow\ReflowTextSource.kt`

```kotlin
package com.ireader.engines.common.android.reflow

interface ReflowTextSource {
    val lengthChars: Long
    fun readString(start: Long, count: Int): String
}

```

## `engines/engine-common-android\src\main\kotlin\com\ireader\engines\common\android\reflow\SoftBreakClassifier.kt`

```kotlin
package com.ireader.engines.common.android.reflow

import kotlin.math.abs

data class SoftBreakLineInfo(
    val length: Int,
    val leadingSpaces: Int,
    val firstNonSpace: Char?,
    val secondNonSpace: Char?,
    val lastNonSpace: Char?,
    val isBoundaryTitle: Boolean,
    val startsWithListMarker: Boolean,
    val startsWithDialogueMarker: Boolean
)

data class SoftBreakClassifierContext(
    val typicalLineLength: Int,
    val hardWrapLikely: Boolean,
    val rules: SoftBreakRuleConfig
)

object SoftBreakClassifier {

    private val strongEndPunctuation = setOf('。', '！', '？', '.', '!', '?', '…', ':', '：', ';', '；')
    private val dialogueMarkers = setOf('“', '"', '「', '『', '—')

    fun classify(
        line0: SoftBreakLineInfo,
        line1: SoftBreakLineInfo,
        context: SoftBreakClassifierContext
    ): SoftBreakDecision {
        val rules = context.rules
        val minTypical = if (context.hardWrapLikely) rules.minTypicalHardWrap else rules.minTypicalNormal
        val typical = context.typicalLineLength.coerceIn(minTypical, rules.maxTypical)
        val threshold = if (context.hardWrapLikely) rules.thresholdHardWrap else rules.thresholdNormal
        var reasons = 0
        if (line0.length == 0 || line1.length == 0) {
            reasons = reasons or SoftBreakDecisionReasons.EMPTY_LINE
            return hardBreakDecision(threshold, reasons)
        }
        if (line0.isBoundaryTitle || line1.isBoundaryTitle) {
            reasons = reasons or SoftBreakDecisionReasons.BOUNDARY_TITLE
            return hardBreakDecision(threshold, reasons)
        }
        if (line1.startsWithListMarker || line1.startsWithDialogueMarker) {
            reasons = reasons or SoftBreakDecisionReasons.LIST_OR_DIALOGUE_START
            return hardBreakDecision(threshold, reasons)
        }

        val indentIncrease = line1.leadingSpaces - line0.leadingSpaces
        if (indentIncrease >= rules.indentIncreaseHardBreakMin) {
            reasons = reasons or SoftBreakDecisionReasons.INDENT_INCREASE
            return hardBreakDecision(threshold, reasons)
        }
        if (
            !context.hardWrapLikely &&
            line1.leadingSpaces >= rules.nonHardWrapIndentChangeHardBreakMin &&
            line1.leadingSpaces != line0.leadingSpaces
        ) {
            reasons = reasons or SoftBreakDecisionReasons.INDENT_SHIFT_IN_NORMAL
            return hardBreakDecision(threshold, reasons)
        }

        var score = if (context.hardWrapLikely) rules.baseScoreHardWrap else rules.baseScoreNormal
        score += line0LengthScore(line0.length, typical, rules)
        score += line1LengthScore(line1.length, typical, rules)

        val lenGap = abs(line0.length - line1.length)
        score += if (lenGap <= (typical * rules.lenGapRatio).toInt()) {
            reasons = reasons or SoftBreakDecisionReasons.LENGTH_STABLE
            1
        } else {
            reasons = reasons or SoftBreakDecisionReasons.LENGTH_GAP
            -1
        }
        if (line0.length < rules.shortLineMinLength || line1.length < rules.shortLineMinLength) {
            reasons = reasons or SoftBreakDecisionReasons.SHORT_LINE
            score -= if (context.hardWrapLikely) {
                rules.shortLinePenaltyHardWrap
            } else {
                rules.shortLinePenaltyNormal
            }
        }

        if (line0.leadingSpaces == line1.leadingSpaces && line1.leadingSpaces > 0) {
            reasons = reasons or SoftBreakDecisionReasons.SAME_INDENT
            score += rules.sameIndentBoost
        }

        val line0StrongEnd = line0.lastNonSpace?.let(strongEndPunctuation::contains) == true
        if (line0StrongEnd) {
            reasons = reasons or SoftBreakDecisionReasons.STRONG_END_PUNCT
            val shortTail = line0.length < (typical * rules.strongEndShortTailRatio).toInt()
            score -= when {
                shortTail && context.hardWrapLikely -> rules.strongEndPenaltyShortTailHardWrap
                shortTail -> rules.strongEndPenaltyShortTailNormal
                context.hardWrapLikely -> rules.strongEndPenaltyHardWrap
                else -> rules.strongEndPenaltyNormal
            }
        }

        if (line0.startsWithDialogueMarker) {
            reasons = reasons or SoftBreakDecisionReasons.LINE0_DIALOGUE_START
            score -= rules.dialogueStartPenalty
        }

        if (
            !context.hardWrapLikely &&
            (
                line0.leadingSpaces >= rules.nonHardWrapIndentChangeHardBreakMin ||
                    line1.leadingSpaces >= rules.nonHardWrapIndentChangeHardBreakMin
                )
        ) {
            reasons = reasons or SoftBreakDecisionReasons.INDENT_PRESENT_IN_NORMAL
            score -= rules.nonHardWrapIndentPenalty
        }

        return SoftBreakDecision(
            isSoft = score >= threshold,
            score = score,
            threshold = threshold,
            reasons = reasons
        )
    }

    fun detectListMarker(firstNonSpace: Char?, secondNonSpace: Char?): Boolean {
        val first = firstNonSpace ?: return false
        if (first == '-' || first == '*' || first == '•' || first == '·') {
            return true
        }
        if (!first.isDigit()) {
            return false
        }
        return secondNonSpace == '.' || secondNonSpace == '、' || secondNonSpace == ')' || secondNonSpace == '）'
    }

    fun detectDialogueMarker(firstNonSpace: Char?): Boolean {
        return firstNonSpace != null && dialogueMarkers.contains(firstNonSpace)
    }

    private fun line0LengthScore(length: Int, typical: Int, rules: SoftBreakRuleConfig): Int {
        return when {
            length >= (typical * rules.line0HighRatio).toInt() -> 3
            length >= (typical * rules.line0MidRatio).toInt() -> 2
            length >= (typical * rules.line0LowRatio).toInt() -> 0
            else -> -3
        }
    }

    private fun line1LengthScore(length: Int, typical: Int, rules: SoftBreakRuleConfig): Int {
        return when {
            length >= (typical * rules.line1HighRatio).toInt() -> 2
            length >= (typical * rules.line1MidRatio).toInt() -> 1
            length >= (typical * rules.line1LowRatio).toInt() -> 0
            else -> -2
        }
    }

    private fun hardBreakDecision(threshold: Int, reasons: Int): SoftBreakDecision {
        return SoftBreakDecision(
            isSoft = false,
            score = threshold - 1,
            threshold = threshold,
            reasons = reasons
        )
    }
}
```

## `engines/engine-common-android\src\main\kotlin\com\ireader\engines\common\android\reflow\SoftBreakProcessor.kt`

```kotlin
@file:Suppress("ComplexCondition", "CyclomaticComplexMethod", "ReturnCount")

package com.ireader.engines.common.android.reflow

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import kotlin.math.max

object SoftBreakProcessor {

    fun renderRawPreservingBreaks(
        rawText: String,
        paragraphSpacingPx: Int,
        paragraphIndentPx: Int,
        startsAtParagraphBoundary: Boolean
    ): CharSequence {
        return rawText
    }

    fun process(
        rawText: String,
        hardWrapLikely: Boolean,
        paragraphSpacingPx: Int,
        paragraphIndentPx: Int,
        startsAtParagraphBoundary: Boolean,
        ruleConfig: SoftBreakRuleConfig = SoftBreakRuleConfig.forProfile(SoftBreakTuningProfile.BALANCED)
    ): CharSequence {
        val normalized = normalizeSoftBreaks(
            text = rawText,
            hardWrapLikely = hardWrapLikely,
            ruleConfig = ruleConfig
        )
        if (paragraphSpacingPx <= 0 && paragraphIndentPx <= 0) {
            return normalized.text
        }
        val builder = SpannableStringBuilder(normalized.text)
        decorateParagraphs(
            builder = builder,
            hardBreakPositions = normalized.hardBreakPositions,
            paragraphSpacingPx = paragraphSpacingPx,
            paragraphIndentPx = paragraphIndentPx,
            startsAtParagraphBoundary = startsAtParagraphBoundary
        )
        return builder
    }

    fun decorateParagraphs(
        builder: SpannableStringBuilder,
        hardBreakPositions: IntArray,
        paragraphSpacingPx: Int,
        paragraphIndentPx: Int,
        startsAtParagraphBoundary: Boolean
    ) {
        if (builder.isEmpty() || (paragraphSpacingPx <= 0 && paragraphIndentPx <= 0)) {
            return
        }

        var paragraphStart = if (startsAtParagraphBoundary) 0 else -1
        hardBreakPositions.forEach { index ->
            if (index !in 0 until builder.length) {
                return@forEach
            }
            if (paragraphSpacingPx > 0) {
                applyParagraphSpacing(builder, index, paragraphSpacingPx)
            }
            if (paragraphIndentPx > 0) {
                applyParagraphIndent(builder, paragraphStart, index + 1, paragraphIndentPx)
            }
            val nextStart = index + 1
            paragraphStart = if (nextStart < builder.length && builder[nextStart] != '\n') {
                nextStart
            } else {
                -1
            }
        }
        if (paragraphIndentPx > 0) {
            applyParagraphIndent(builder, paragraphStart, builder.length, paragraphIndentPx)
        }
    }

    private fun normalizeSoftBreaks(
        text: String,
        hardWrapLikely: Boolean,
        ruleConfig: SoftBreakRuleConfig
    ): NormalizedText {
        if (text.isEmpty()) {
            return NormalizedText("", intArrayOf())
        }
        val chars = text.toCharArray()
        val lines = ArrayList<SoftBreakLineInfo>(max(16, text.length / 48))
        val newlineOffsets = ArrayList<Int>(max(16, text.length / 80))

        var start = 0
        while (start <= chars.size) {
            val newline = text.indexOf('\n', start).let { idx ->
                if (idx >= 0) idx else chars.size
            }
            lines += parseLine(chars, start, newline)
            if (newline >= chars.size) {
                break
            }
            newlineOffsets += newline
            start = newline + 1
        }

        val typical = estimateTypicalLineLength(lines, hardWrapLikely, ruleConfig)
        val context = SoftBreakClassifierContext(
            typicalLineLength = typical,
            hardWrapLikely = hardWrapLikely,
            rules = ruleConfig
        )

        val hardBreaks = ArrayList<Int>()
        for (i in newlineOffsets.indices) {
            val offset = newlineOffsets[i]
            val nextLine = lines.getOrNull(i + 1) ?: EMPTY_LINE
            val decision = SoftBreakClassifier.classify(
                line0 = lines[i],
                line1 = nextLine,
                context = context
            )
            if (decision.isSoft) {
                chars[offset] = ' '
            } else {
                hardBreaks += offset
            }
        }
        return NormalizedText(String(chars), hardBreaks.toIntArray())
    }

    private fun applyParagraphSpacing(
        builder: SpannableStringBuilder,
        lineBreakIndex: Int,
        paragraphSpacingPx: Int
    ) {
        val next = if (lineBreakIndex + 1 < builder.length) builder[lineBreakIndex + 1] else null
        if (next == '\n') {
            return
        }
        builder.setSpan(
            ParagraphSpacingSpan(paragraphSpacingPx),
            lineBreakIndex,
            lineBreakIndex + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun applyParagraphIndent(
        builder: SpannableStringBuilder,
        paragraphStart: Int,
        paragraphEndExclusive: Int,
        paragraphIndentPx: Int
    ) {
        if (paragraphStart < 0 || paragraphStart >= paragraphEndExclusive || paragraphIndentPx <= 0) {
            return
        }
        if (!shouldIndentParagraph(builder, paragraphStart, paragraphEndExclusive)) {
            return
        }
        builder.setSpan(
            LeadingMarginSpan.Standard(paragraphIndentPx, 0),
            paragraphStart,
            paragraphEndExclusive,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun shouldIndentParagraph(
        text: CharSequence,
        paragraphStart: Int,
        paragraphEndExclusive: Int
    ): Boolean {
        var index = paragraphStart
        var leadingWhitespaceCount = 0
        var leadingFullWidthSpaces = 0
        while (index < paragraphEndExclusive) {
            val ch = text[index]
            if (ch == '\n') {
                return false
            }
            if (!ch.isWhitespace() && ch != '\u3000') {
                break
            }
            leadingWhitespaceCount++
            if (ch == '\u3000') {
                leadingFullWidthSpaces++
            }
            index++
        }
        if (index >= paragraphEndExclusive) {
            return false
        }
        if (leadingFullWidthSpaces > 0 || leadingWhitespaceCount >= MANUAL_INDENT_WHITESPACE_MIN) {
            return false
        }
        val visibleLength = paragraphEndExclusive - index
        if (visibleLength < MIN_INDENT_PARAGRAPH_CHARS) {
            return false
        }
        val snippet = text.subSequence(index, minOf(paragraphEndExclusive, index + 64)).toString().trim()
        if (isBoundaryTitleLine(snippet)) {
            return false
        }
        return true
    }

    private const val MIN_INDENT_PARAGRAPH_CHARS = 12
    private const val MANUAL_INDENT_WHITESPACE_MIN = 2
    private const val MAX_BOUNDARY_TITLE_CHARS = 48
    private val CHINESE_CHAPTER_REGEX = Regex("^第[零一二三四五六七八九十百千万0-9]{1,9}[章节回卷部篇].{0,30}$")
    private val ENGLISH_CHAPTER_REGEX = Regex("^(Chapter|CHAPTER)\\s+\\d+.*$")
    private val PROLOGUE_REGEX = Regex("^(Prologue|Epilogue|PROLOGUE|EPILOGUE)$")
    private val DIRECTORY_TITLE_REGEX = Regex("^(目录|目\\s*录|contents)$", RegexOption.IGNORE_CASE)
    private val EMPTY_LINE = SoftBreakLineInfo(
        length = 0,
        leadingSpaces = 0,
        firstNonSpace = null,
        secondNonSpace = null,
        lastNonSpace = null,
        isBoundaryTitle = false,
        startsWithListMarker = false,
        startsWithDialogueMarker = false
    )

    private data class NormalizedText(
        val text: String,
        val hardBreakPositions: IntArray
    )

    private fun parseLine(chars: CharArray, start: Int, endExclusive: Int): SoftBreakLineInfo {
        var lineLength = 0
        var leadingSpaces = 0
        var firstNonSpace: Char? = null
        var secondNonSpace: Char? = null
        var lastNonSpace: Char? = null
        var seenNonSpace = false
        var i = start
        while (i < endExclusive) {
            val c = chars[i]
            lineLength++
            if (!seenNonSpace) {
                if (c == ' ' || c == '\t' || c == '\u3000') {
                    leadingSpaces++
                } else {
                    seenNonSpace = true
                    firstNonSpace = c
                }
            } else if (secondNonSpace == null && !c.isWhitespace()) {
                secondNonSpace = c
            }
            if (!c.isWhitespace()) {
                lastNonSpace = c
            }
            i++
        }

        val lineText = if (endExclusive > start) {
            String(chars, start, endExclusive - start).trim()
        } else {
            ""
        }
        val boundary = isBoundaryTitleLine(lineText)
        return SoftBreakLineInfo(
            length = lineLength,
            leadingSpaces = leadingSpaces,
            firstNonSpace = firstNonSpace,
            secondNonSpace = secondNonSpace,
            lastNonSpace = lastNonSpace,
            isBoundaryTitle = boundary,
            startsWithListMarker = SoftBreakClassifier.detectListMarker(firstNonSpace, secondNonSpace),
            startsWithDialogueMarker = SoftBreakClassifier.detectDialogueMarker(firstNonSpace)
        )
    }

    private fun isBoundaryTitleLine(line: String): Boolean {
        if (line.isBlank()) {
            return false
        }
        if (DIRECTORY_TITLE_REGEX.matches(line)) {
            return true
        }
        if (line.length > MAX_BOUNDARY_TITLE_CHARS) {
            return false
        }
        return CHINESE_CHAPTER_REGEX.matches(line) ||
            ENGLISH_CHAPTER_REGEX.matches(line) ||
            PROLOGUE_REGEX.matches(line)
    }

    private fun estimateTypicalLineLength(
        lines: List<SoftBreakLineInfo>,
        hardWrapLikely: Boolean,
        ruleConfig: SoftBreakRuleConfig
    ): Int {
        var count = 0
        var sum = 0L
        lines.forEach { line ->
            if (line.length > 0) {
                count++
                sum += line.length.toLong()
            }
        }
        if (count == 0) {
            return 72
        }
        val minTypical = if (hardWrapLikely) {
            ruleConfig.minTypicalHardWrap
        } else {
            ruleConfig.minTypicalNormal
        }
        return (sum / count).toInt().coerceIn(minTypical, ruleConfig.maxTypical)
    }
}
```

## `engines/engine-common-android\src\main\kotlin\com\ireader\engines\common\android\reflow\SoftBreakRules.kt`

```kotlin
package com.ireader.engines.common.android.reflow

const val SOFT_BREAK_PROFILE_EXTRA_KEY: String = "txt.soft_break_profile"

enum class SoftBreakTuningProfile(val storageValue: String) {
    STRICT("strict"),
    BALANCED("balanced"),
    AGGRESSIVE("aggressive");

    companion object {
        private val BY_STORAGE = entries.associateBy { it.storageValue }

        fun fromStorageValue(raw: String?): SoftBreakTuningProfile {
            val key = raw?.trim()?.lowercase()
            return if (key.isNullOrEmpty()) {
                BALANCED
            } else {
                BY_STORAGE[key] ?: BALANCED
            }
        }
    }
}

data class SoftBreakRuleConfig(
    val rulesVersion: Int,
    val minTypicalHardWrap: Int,
    val minTypicalNormal: Int,
    val maxTypical: Int,
    val baseScoreHardWrap: Int,
    val baseScoreNormal: Int,
    val thresholdHardWrap: Int,
    val thresholdNormal: Int,
    val lenGapRatio: Float,
    val shortLineMinLength: Int,
    val shortLinePenaltyHardWrap: Int,
    val shortLinePenaltyNormal: Int,
    val sameIndentBoost: Int,
    val strongEndShortTailRatio: Float,
    val strongEndPenaltyShortTailHardWrap: Int,
    val strongEndPenaltyShortTailNormal: Int,
    val strongEndPenaltyHardWrap: Int,
    val strongEndPenaltyNormal: Int,
    val dialogueStartPenalty: Int,
    val nonHardWrapIndentPenalty: Int,
    val indentIncreaseHardBreakMin: Int,
    val nonHardWrapIndentChangeHardBreakMin: Int,
    val line0HighRatio: Float,
    val line0MidRatio: Float,
    val line0LowRatio: Float,
    val line1HighRatio: Float,
    val line1MidRatio: Float,
    val line1LowRatio: Float
) {
    companion object {
        private val BALANCED_BASE = SoftBreakRuleConfig(
            rulesVersion = 1,
            minTypicalHardWrap = 8,
            minTypicalNormal = 18,
            maxTypical = 200,
            baseScoreHardWrap = 2,
            baseScoreNormal = 0,
            thresholdHardWrap = 1,
            thresholdNormal = 7,
            lenGapRatio = 0.28f,
            shortLineMinLength = 8,
            shortLinePenaltyHardWrap = 1,
            shortLinePenaltyNormal = 3,
            sameIndentBoost = 1,
            strongEndShortTailRatio = 0.88f,
            strongEndPenaltyShortTailHardWrap = 1,
            strongEndPenaltyShortTailNormal = 4,
            strongEndPenaltyHardWrap = 1,
            strongEndPenaltyNormal = 2,
            dialogueStartPenalty = 1,
            nonHardWrapIndentPenalty = 1,
            indentIncreaseHardBreakMin = 2,
            nonHardWrapIndentChangeHardBreakMin = 2,
            line0HighRatio = 0.95f,
            line0MidRatio = 0.78f,
            line0LowRatio = 0.62f,
            line1HighRatio = 0.72f,
            line1MidRatio = 0.50f,
            line1LowRatio = 0.32f
        )

        fun forProfile(profile: SoftBreakTuningProfile): SoftBreakRuleConfig {
            return when (profile) {
                SoftBreakTuningProfile.STRICT -> BALANCED_BASE.copy(
                    thresholdHardWrap = 2,
                    thresholdNormal = 9,
                    lenGapRatio = 0.22f,
                    shortLinePenaltyHardWrap = 2,
                    shortLinePenaltyNormal = 4,
                    strongEndPenaltyHardWrap = 2,
                    strongEndPenaltyNormal = 3,
                    strongEndPenaltyShortTailHardWrap = 2,
                    strongEndPenaltyShortTailNormal = 5
                )

                SoftBreakTuningProfile.BALANCED -> BALANCED_BASE

                SoftBreakTuningProfile.AGGRESSIVE -> BALANCED_BASE.copy(
                    baseScoreHardWrap = 3,
                    thresholdHardWrap = 0,
                    thresholdNormal = 5,
                    lenGapRatio = 0.35f,
                    shortLinePenaltyHardWrap = 1,
                    shortLinePenaltyNormal = 2,
                    strongEndPenaltyHardWrap = 1,
                    strongEndPenaltyNormal = 1,
                    strongEndPenaltyShortTailHardWrap = 1,
                    strongEndPenaltyShortTailNormal = 2
                )
            }
        }
    }
}

data class SoftBreakDecision(
    val isSoft: Boolean,
    val score: Int,
    val threshold: Int,
    val reasons: Int
)

object SoftBreakDecisionReasons {
    const val EMPTY_LINE = 1 shl 0
    const val BOUNDARY_TITLE = 1 shl 1
    const val LIST_OR_DIALOGUE_START = 1 shl 2
    const val INDENT_INCREASE = 1 shl 3
    const val INDENT_SHIFT_IN_NORMAL = 1 shl 4
    const val LENGTH_STABLE = 1 shl 5
    const val LENGTH_GAP = 1 shl 6
    const val SHORT_LINE = 1 shl 7
    const val SAME_INDENT = 1 shl 8
    const val STRONG_END_PUNCT = 1 shl 9
    const val LINE0_DIALOGUE_START = 1 shl 10
    const val INDENT_PRESENT_IN_NORMAL = 1 shl 11

    fun describe(flags: Int): List<String> {
        val items = ArrayList<String>(8)
        if ((flags and EMPTY_LINE) != 0) items += "empty_line"
        if ((flags and BOUNDARY_TITLE) != 0) items += "boundary_title"
        if ((flags and LIST_OR_DIALOGUE_START) != 0) items += "list_or_dialogue_start"
        if ((flags and INDENT_INCREASE) != 0) items += "indent_increase"
        if ((flags and INDENT_SHIFT_IN_NORMAL) != 0) items += "indent_shift_in_normal"
        if ((flags and LENGTH_STABLE) != 0) items += "length_stable"
        if ((flags and LENGTH_GAP) != 0) items += "length_gap"
        if ((flags and SHORT_LINE) != 0) items += "short_line"
        if ((flags and SAME_INDENT) != 0) items += "same_indent"
        if ((flags and STRONG_END_PUNCT) != 0) items += "strong_end_punct"
        if ((flags and LINE0_DIALOGUE_START) != 0) items += "line0_dialogue_start"
        if ((flags and INDENT_PRESENT_IN_NORMAL) != 0) items += "indent_present_in_normal"
        return items
    }
}
```

## `engines/engine-common-android\src\main\kotlin\com\ireader\engines\common\android\session\BaseReaderSession.kt`

```kotlin
package com.ireader.engines.common.android.session

import com.ireader.reader.api.engine.ReaderSession
import com.ireader.reader.api.provider.AnnotationProvider
import com.ireader.reader.api.provider.OutlineProvider
import com.ireader.reader.api.provider.ResourceProvider
import com.ireader.reader.api.provider.SelectionController
import com.ireader.reader.api.provider.SearchProvider
import com.ireader.reader.api.provider.SelectionProvider
import com.ireader.reader.api.provider.TextProvider
import com.ireader.reader.api.render.ReaderController
import com.ireader.reader.model.SessionId
import java.util.concurrent.atomic.AtomicBoolean

open class BaseReaderSession(
    final override val id: SessionId,
    final override val controller: ReaderController,
    final override val outline: OutlineProvider? = null,
    final override val search: SearchProvider? = null,
    final override val text: TextProvider? = null,
    final override val annotations: AnnotationProvider? = null,
    final override val resources: ResourceProvider? = null,
    final override val selection: SelectionProvider? = null,
    final override val selectionController: SelectionController? = null
) : ReaderSession {

    private val closed = AtomicBoolean(false)

    protected open fun closeExtras() = Unit

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { closeExtras() }
        runCatching { controller.close() }
    }
}
```

## `engines/engine-common\src\main\kotlin\com\ireader\engines\common\cache\LruCache.kt`

```kotlin
package com.ireader.engines.common.cache

class LruCache<K, V>(
    maxEntries: Int
) {
    private val maxSize = maxEntries.coerceAtLeast(1)
    private val map = object : LinkedHashMap<K, V>(maxSize + 1, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<K, V>
        ): Boolean = size > maxSize
    }

    operator fun get(key: K): V? = map[key]

    operator fun set(key: K, value: V) {
        map[key] = value
    }

    fun clear() {
        map.clear()
    }
}
```

## `engines/engine-common\src\main\kotlin\com\ireader\engines\common\EngineCommonMarker.kt`

```kotlin
package com.ireader.engines.common

object EngineCommonMarker
```

## `engines/engine-common\src\main\kotlin\com\ireader\engines\common\hash\Hashing.kt`

```kotlin
package com.ireader.engines.common.hash

import java.security.MessageDigest

object Hashing {

    fun sha1Hex(input: String): String = digestHex("SHA-1", input.toByteArray(Charsets.UTF_8))

    fun sha256Hex(input: String): String = digestHex("SHA-256", input.toByteArray(Charsets.UTF_8))

    fun sha256Hex(bytes: ByteArray): String = digestHex("SHA-256", bytes)

    fun toHexLower(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        var i = 0
        for (b in bytes) {
            val value = b.toInt() and 0xFF
            out[i++] = HEX[value ushr 4]
            out[i++] = HEX[value and 0x0F]
        }
        return String(out)
    }

    private fun digestHex(algorithm: String, bytes: ByteArray): String {
        val digest = MessageDigest.getInstance(algorithm)
        digest.update(bytes)
        return toHexLower(digest.digest())
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
```

## `engines/engine-common\src\main\kotlin\com\ireader\engines\common\id\DocumentIds.kt`

```kotlin
package com.ireader.engines.common.id

import com.ireader.engines.common.hash.Hashing
import com.ireader.reader.model.DocumentId

object DocumentIds {

    fun fromSha1(prefix: String, raw: String): DocumentId {
        return DocumentId("$prefix:${Hashing.sha1Hex(raw)}")
    }

    fun fromSha256(
        raw: String,
        length: Int = 64,
        prefix: String? = null
    ): DocumentId {
        val value = Hashing.sha256Hex(raw).take(length.coerceAtLeast(1))
        return if (prefix.isNullOrBlank()) {
            DocumentId(value)
        } else {
            DocumentId("$prefix:$value")
        }
    }
}
```

## `engines/engine-common\src\main\kotlin\com\ireader\engines\common\io\AtomicFiles.kt`

```kotlin
package com.ireader.engines.common.io

import java.io.File
import java.io.IOException

fun prepareTempFile(file: File) {
    file.parentFile?.mkdirs()
    if (file.exists() && !file.delete()) {
        throw IOException("Failed to delete stale temp file: ${file.absolutePath}")
    }
}

fun replaceFileAtomically(
    tempFile: File,
    targetFile: File,
    rename: (File, File) -> Boolean = { src, dst -> src.renameTo(dst) }
) {
    targetFile.parentFile?.mkdirs()
    if (targetFile.exists() && !targetFile.delete()) {
        throw IOException("Failed to delete target file: ${targetFile.absolutePath}")
    }
    if (rename(tempFile, targetFile)) {
        return
    }
    tempFile.copyTo(targetFile, overwrite = true)
    if (tempFile.exists() && !tempFile.delete()) {
        tempFile.deleteOnExit()
    }
}

```

## `engines/engine-common\src\main\kotlin\com\ireader\engines\common\io\CloseableExt.kt`

```kotlin
package com.ireader.engines.common.io

import java.io.Closeable

fun Closeable?.closeQuietly() {
    runCatching { this?.close() }
}

fun closeAllQuietly(vararg closeables: Closeable?) {
    for (closeable in closeables) {
        closeable.closeQuietly()
    }
}
```

## `engines/engine-common\src\main\kotlin\com\ireader\engines\common\io\Varint.kt`

```kotlin
package com.ireader.engines.common.io

import java.io.EOFException
import java.io.IOException
import java.io.RandomAccessFile

fun RandomAccessFile.writeVarLong(value: Long) {
    require(value >= 0L) { "VarLong only supports non-negative values: $value" }
    var v = value
    while ((v and -128L) != 0L) {
        writeByte(((v and 0x7FL) or 0x80L).toInt())
        v = v ushr 7
    }
    writeByte(v.toInt())
}

fun RandomAccessFile.readVarLongOrNull(): Long? {
    var shift = 0
    var result = 0L
    try {
        while (shift < 64) {
            val b = readUnsignedByte()
            result = result or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) {
                return result
            }
            shift += 7
        }
        throw IOException("VarLong too long")
    } catch (_: EOFException) {
        return null
    }
}

fun RandomAccessFile.writeStringUtf8(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    require(bytes.size <= 0xFFFF) { "String too long for u16 length: ${bytes.size}" }
    writeShort(bytes.size)
    write(bytes)
}

fun RandomAccessFile.readStringUtf8(): String {
    val len = readShort().toInt() and 0xFFFF
    val bytes = ByteArray(len)
    readFully(bytes)
    return String(bytes, Charsets.UTF_8)
}

```

## `engines/engine-common\src\main\kotlin\com\ireader\engines\common\pagination\PageMap.kt`

```kotlin
package com.ireader.engines.common.pagination

import com.ireader.engines.common.io.prepareTempFile
import com.ireader.engines.common.io.readVarLongOrNull
import com.ireader.engines.common.io.replaceFileAtomically
import com.ireader.engines.common.io.writeVarLong
import java.io.File
import java.io.RandomAccessFile
import java.util.TreeSet

object PageMap {

    private const val MAGIC = 0x504D4150 // PMAP
    private const val VERSION = 1

    fun load(binaryFile: File, legacyTextFile: File? = null): TreeSet<Long> {
        if (binaryFile.exists()) {
            readBinary(binaryFile)?.also { return it }
        }
        if (legacyTextFile != null && legacyTextFile.exists()) {
            return readLegacy(legacyTextFile)
        }
        return TreeSet()
    }

    fun save(binaryFile: File, starts: Collection<Long>) {
        val normalized = TreeSet<Long>()
        for (value in starts) {
            if (value >= 0L) {
                normalized.add(value)
            }
        }
        val tmp = File(binaryFile.parentFile, "${binaryFile.name}.tmp")
        prepareTempFile(tmp)

        RandomAccessFile(tmp, "rw").use { raf ->
            raf.setLength(0L)
            raf.writeInt(MAGIC)
            raf.writeInt(VERSION)
            raf.writeInt(normalized.size)
            var prev = 0L
            for (value in normalized) {
                val delta = (value - prev).coerceAtLeast(0L)
                raf.writeVarLong(delta)
                prev = value
            }
        }

        replaceFileAtomically(tempFile = tmp, targetFile = binaryFile)
    }

    private fun readBinary(file: File): TreeSet<Long>? {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.readInt() != MAGIC) {
                    return null
                }
                if (raf.readInt() != VERSION) {
                    return null
                }
                val count = raf.readInt()
                val out = TreeSet<Long>()
                var value = 0L
                for (i in 0 until count) {
                    val delta = raf.readVarLongOrNull() ?: break
                    value += delta
                    out.add(value)
                }
                out
            }
        }.getOrNull()
    }

    private fun readLegacy(file: File): TreeSet<Long> {
        val out = TreeSet<Long>()
        file.forEachLine { line ->
            line.trim().toLongOrNull()?.takeIf { it >= 0L }?.also { out.add(it) }
        }
        return out
    }
}

```


