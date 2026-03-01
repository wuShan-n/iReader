package com.ireader.engines.txt.internal.paging

import java.io.ByteArrayOutputStream

internal object PageStartsCodec {

    fun encode(sortedUnique: IntArray): ByteArray {
        val out = ByteArrayOutputStream(sortedUnique.size * 2)
        var prev = 0
        sortedUnique.forEach { value ->
            val delta = (value - prev).coerceAtLeast(0)
            writeVarInt(out, delta)
            prev = value
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): IntArray {
        val values = ArrayList<Int>(256)
        var cursor = 0
        var prev = 0
        while (cursor < bytes.size) {
            val (delta, next) = readVarInt(bytes, cursor)
            cursor = next
            prev += delta
            values.add(prev)
        }
        return values.toIntArray()
    }

    private fun writeVarInt(out: ByteArrayOutputStream, value: Int) {
        var v = value
        while (true) {
            val b = v and 0x7F
            v = v ushr 7
            if (v == 0) {
                out.write(b)
                return
            }
            out.write(b or 0x80)
        }
    }

    private fun readVarInt(bytes: ByteArray, start: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var i = start
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            i += 1
            if ((b and 0x80) == 0) break
            shift += 7
        }
        return result to i
    }
}
