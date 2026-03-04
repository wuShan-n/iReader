package com.ireader.core.common.android.typography

private const val MAX_SCRIPT_SAMPLE_CHARS = 1024
private const val MIN_SCRIPT_SIGNAL = 16
private const val MIN_CJK_FOR_DOMINANT = 10
private const val MIN_CJK_FOR_SHORT_SIGNAL = 6
private const val CJK_DOMINANCE_RATIO = 0.38

private enum class ScriptClass {
    CJK,
    LATIN,
    OTHER
}

fun CharSequence.prefersInterCharacterJustify(
    sampleChars: Int = MAX_SCRIPT_SAMPLE_CHARS
): Boolean {
    if (isEmpty()) {
        return false
    }

    val limit = sampleChars.coerceAtLeast(1)
    var cjk = 0
    var latin = 0
    var sampled = 0
    var index = 0

    while (index < length && sampled < limit) {
        when (classifyScript(this[index])) {
            ScriptClass.CJK -> {
                cjk++
                sampled++
            }

            ScriptClass.LATIN -> {
                latin++
                sampled++
            }

            ScriptClass.OTHER -> Unit
        }
        index++
    }

    val scriptTotal = cjk + latin
    if (scriptTotal == 0) {
        return false
    }
    if (scriptTotal < MIN_SCRIPT_SIGNAL) {
        return cjk >= MIN_CJK_FOR_SHORT_SIGNAL && cjk > latin
    }
    if (cjk < MIN_CJK_FOR_DOMINANT) {
        return false
    }

    val cjkRatio = cjk.toDouble() / scriptTotal.toDouble()
    return cjkRatio >= CJK_DOMINANCE_RATIO
}

private fun classifyScript(ch: Char): ScriptClass {
    if (ch.isWhitespace() || ch.isDigit()) {
        return ScriptClass.OTHER
    }

    return when (Character.UnicodeScript.of(ch.code)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL -> ScriptClass.CJK

        Character.UnicodeScript.LATIN -> ScriptClass.LATIN
        else -> ScriptClass.OTHER
    }
}
