package com.ireader.engines.txt.internal.open

internal object TxtTextNormalizer {

    const val TAB_SPACES: Int = 4

    fun normalize(input: String): String {
        if (input.isEmpty()) return input
        val state = StreamState()
        val out = StringBuilder(input.length)
        appendNormalized(input, state) { out.append(it) }
        return out.toString()
    }

    fun appendNormalized(
        input: CharSequence,
        state: StreamState,
        emit: (Char) -> Unit
    ) {
        for (i in 0 until input.length) {
            emitNormalized(input[i], state, emit)
        }
    }

    private fun emitNormalized(
        ch: Char,
        state: StreamState,
        emit: (Char) -> Unit
    ) {
        if (ch == '\n' && state.lastWasCr) {
            state.lastWasCr = false
            return
        }

        if (ch == '\r') {
            emit('\n')
            state.lastWasCr = true
            state.emittedAny = true
            return
        }

        state.lastWasCr = false

        if (!state.emittedAny && ch == '\uFEFF') {
            return
        }

        if (ch == '\t') {
            repeat(TAB_SPACES) {
                emit(' ')
            }
            state.emittedAny = true
            return
        }

        if (ch < ' ' && ch != '\n') {
            return
        }

        emit(ch)
        state.emittedAny = true
    }

    data class StreamState(
        var lastWasCr: Boolean = false,
        var emittedAny: Boolean = false
    )
}

