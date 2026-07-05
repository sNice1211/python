package com.nexradwx.core.decode

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Cursor over a big-endian byte buffer, matching the wire format used throughout the
 * NEXRAD Level II Interface Control Document (ICD 2620002).
 */
internal class RadarByteReader(private val data: ByteArray, start: Int = 0) {
    private val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

    init {
        buffer.position(start)
    }

    val size: Int get() = data.size

    var position: Int
        get() = buffer.position()
        set(value) {
            buffer.position(value)
        }

    fun remaining(): Int = buffer.remaining()

    fun skip(n: Int) {
        buffer.position(buffer.position() + n)
    }

    fun readU8(): Int = buffer.get().toInt() and 0xFF

    fun readI8(): Int = buffer.get().toInt()

    fun readU16(): Int = buffer.short.toInt() and 0xFFFF

    fun readI16(): Int = buffer.short.toInt()

    fun readU32(): Long = buffer.int.toLong() and 0xFFFFFFFFL

    fun readI32(): Int = buffer.int

    fun readF32(): Float = buffer.float

    fun readBytes(n: Int): ByteArray {
        val out = ByteArray(n)
        buffer.get(out)
        return out
    }

    /** Reads a fixed-width ASCII field, trimming trailing NUL/space padding. */
    fun readAscii(n: Int): String {
        val text = String(readBytes(n), Charsets.US_ASCII)
        return text.trim { it == ' ' || it.code == 0 }
    }
}
