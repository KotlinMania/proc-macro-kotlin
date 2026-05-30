/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.IntBuffer
import kotlin.math.pow

/**
 * Wrapper for [ByteBuffer] / [CharBuffer] / [IntBuffer].
 *
 * Because Java lacks generics on primitive types, these three types
 * do not share an interface, so we have to write one manually.
 */
class CodePointBuffer private constructor(
    val type: Type,
    private val byteBuffer: ByteBuffer?,
    private val charBuffer: CharBuffer?,
    private val intBuffer: IntBuffer?,
) {
    enum class Type {
        BYTE,
        CHAR,
        INT,
    }

    fun position(): Int =
        when (type) {
            Type.BYTE -> byteBuffer!!.position()
            Type.CHAR -> charBuffer!!.position()
            Type.INT -> intBuffer!!.position()
        }

    fun position(newPosition: Int) {
        when (type) {
            Type.BYTE -> byteBuffer!!.position(newPosition)
            Type.CHAR -> charBuffer!!.position(newPosition)
            Type.INT -> intBuffer!!.position(newPosition)
        }
    }

    fun remaining(): Int =
        when (type) {
            Type.BYTE -> byteBuffer!!.remaining()
            Type.CHAR -> charBuffer!!.remaining()
            Type.INT -> intBuffer!!.remaining()
        }

    fun get(offset: Int): Int =
        when (type) {
            Type.BYTE -> byteBuffer!!.get(offset).toInt() and 0xFF
            Type.CHAR -> charBuffer!!.get(offset).code
            Type.INT -> intBuffer!!.get(offset)
        }

    internal fun getType(): Type = type

    internal fun arrayOffset(): Int =
        when (type) {
            Type.BYTE -> byteBuffer!!.arrayOffset()
            Type.CHAR -> charBuffer!!.arrayOffset()
            Type.INT -> intBuffer!!.arrayOffset()
        }

    internal fun byteArray(): ByteArray {
        assert(type == Type.BYTE)
        return byteBuffer!!.array()
    }

    internal fun charArray(): CharArray {
        assert(type == Type.CHAR)
        return charBuffer!!.array()
    }

    internal fun intArray(): IntArray {
        assert(type == Type.INT)
        return intBuffer!!.array()
    }

    companion object {
        @JvmStatic
        fun withBytes(byteBuffer: ByteBuffer): CodePointBuffer = CodePointBuffer(Type.BYTE, byteBuffer, null, null)

        @JvmStatic
        fun withChars(charBuffer: CharBuffer): CodePointBuffer = CodePointBuffer(Type.CHAR, null, charBuffer, null)

        @JvmStatic
        fun withInts(intBuffer: IntBuffer): CodePointBuffer = CodePointBuffer(Type.INT, null, null, intBuffer)

        @JvmStatic
        fun builder(initialBufferSize: Int): Builder = Builder(initialBufferSize)
    }

    class Builder internal constructor(
        initialBufferSize: Int,
    ) {
        private var type: Type = Type.BYTE
        private var byteBuffer: ByteBuffer = ByteBuffer.allocate(initialBufferSize)
        private var charBuffer: CharBuffer? = null
        private var intBuffer: IntBuffer? = null
        private var prevHighSurrogate: Int = -1

        internal fun getType(): Type = type

        internal fun getByteBuffer(): ByteBuffer = byteBuffer

        internal fun getCharBuffer(): CharBuffer? = charBuffer

        internal fun getIntBuffer(): IntBuffer? = intBuffer

        fun build(): CodePointBuffer {
            when (type) {
                Type.BYTE -> byteBuffer.flip()
                Type.CHAR -> charBuffer!!.flip()
                Type.INT -> intBuffer!!.flip()
            }
            return CodePointBuffer(type, byteBuffer, charBuffer, intBuffer)
        }

        private fun roundUpToNextPowerOfTwo(i: Int): Int {
            val nextPowerOfTwo = 32 - Integer.numberOfLeadingZeros(i - 1)
            return 2.0.pow(nextPowerOfTwo.toDouble()).toInt()
        }

        fun ensureRemaining(remainingNeeded: Int) {
            when (type) {
                Type.BYTE -> {
                    if (byteBuffer.remaining() < remainingNeeded) {
                        val newCapacity = roundUpToNextPowerOfTwo(byteBuffer.capacity() + remainingNeeded)
                        val newBuffer = ByteBuffer.allocate(newCapacity)
                        byteBuffer.flip()
                        newBuffer.put(byteBuffer)
                        byteBuffer = newBuffer
                    }
                }
                Type.CHAR -> {
                    if (charBuffer!!.remaining() < remainingNeeded) {
                        val newCapacity = roundUpToNextPowerOfTwo(charBuffer!!.capacity() + remainingNeeded)
                        val newBuffer = CharBuffer.allocate(newCapacity)
                        charBuffer!!.flip()
                        newBuffer.put(charBuffer!!)
                        charBuffer = newBuffer
                    }
                }
                Type.INT -> {
                    if (intBuffer!!.remaining() < remainingNeeded) {
                        val newCapacity = roundUpToNextPowerOfTwo(intBuffer!!.capacity() + remainingNeeded)
                        val newBuffer = IntBuffer.allocate(newCapacity)
                        intBuffer!!.flip()
                        newBuffer.put(intBuffer!!)
                        intBuffer = newBuffer
                    }
                }
            }
        }

        fun append(utf16In: CharBuffer) {
            if (utf16In.hasArray()) {
                appendArray(utf16In)
            } else {
                throw UnsupportedOperationException("Not implemented for non-array CharBuffers")
            }
        }

        private fun appendArray(utf16In: CharBuffer) {
            when (type) {
                Type.BYTE -> appendArrayByte(utf16In)
                Type.CHAR -> appendArrayChar(utf16In)
                Type.INT -> appendArrayInt(utf16In)
            }
        }

        private fun appendArrayByte(utf16In: CharBuffer) {
            assert(prevHighSurrogate == -1)

            val inn = utf16In.array()
            var inOffset = utf16In.arrayOffset() + utf16In.position()
            val inLimit = utf16In.arrayOffset() + utf16In.limit()

            val outByte = byteBuffer.array()
            var outOffset = byteBuffer.arrayOffset() + byteBuffer.position()

            while (inOffset < inLimit) {
                val c = inn[inOffset]
                if (c.code <= 0xFF) {
                    outByte[outOffset] = (c.code and 0xFF).toByte()
                } else {
                    utf16In.position(inOffset - utf16In.arrayOffset())
                    byteBuffer.position(outOffset - byteBuffer.arrayOffset())
                    if (!Character.isHighSurrogate(c)) {
                        byteToCharBuffer(utf16In.remaining())
                        appendArrayChar(utf16In)
                        return
                    } else {
                        byteToIntBuffer(utf16In.remaining())
                        appendArrayInt(utf16In)
                        return
                    }
                }
                inOffset++
                outOffset++
            }

            utf16In.position(inOffset - utf16In.arrayOffset())
            byteBuffer.position(outOffset - byteBuffer.arrayOffset())
        }

        private fun appendArrayChar(utf16In: CharBuffer) {
            assert(prevHighSurrogate == -1)

            val inn = utf16In.array()
            var inOffset = utf16In.arrayOffset() + utf16In.position()
            val inLimit = utf16In.arrayOffset() + utf16In.limit()

            val outChar = charBuffer!!.array()
            var outOffset = charBuffer!!.arrayOffset() + charBuffer!!.position()

            while (inOffset < inLimit) {
                val c = inn[inOffset]
                if (!Character.isHighSurrogate(c)) {
                    outChar[outOffset] = c
                } else {
                    utf16In.position(inOffset - utf16In.arrayOffset())
                    charBuffer!!.position(outOffset - charBuffer!!.arrayOffset())
                    charToIntBuffer(utf16In.remaining())
                    appendArrayInt(utf16In)
                    return
                }
                inOffset++
                outOffset++
            }

            utf16In.position(inOffset - utf16In.arrayOffset())
            charBuffer!!.position(outOffset - charBuffer!!.arrayOffset())
        }

        private fun appendArrayInt(utf16In: CharBuffer) {
            val inn = utf16In.array()
            var inOffset = utf16In.arrayOffset() + utf16In.position()
            val inLimit = utf16In.arrayOffset() + utf16In.limit()

            val outInt = intBuffer!!.array()
            var outOffset = intBuffer!!.arrayOffset() + intBuffer!!.position()

            while (inOffset < inLimit) {
                val c = inn[inOffset]
                inOffset++
                if (prevHighSurrogate != -1) {
                    if (Character.isLowSurrogate(c)) {
                        outInt[outOffset] = Character.toCodePoint(prevHighSurrogate.toChar(), c)
                        outOffset++
                        prevHighSurrogate = -1
                    } else {
                        outInt[outOffset] = prevHighSurrogate
                        outOffset++
                        if (Character.isHighSurrogate(c)) {
                            prevHighSurrogate = c.code and 0xFFFF
                        } else {
                            outInt[outOffset] = c.code and 0xFFFF
                            outOffset++
                            prevHighSurrogate = -1
                        }
                    }
                } else if (Character.isHighSurrogate(c)) {
                    prevHighSurrogate = c.code and 0xFFFF
                } else {
                    outInt[outOffset] = c.code and 0xFFFF
                    outOffset++
                }
            }

            if (prevHighSurrogate != -1) {
                outInt[outOffset] = prevHighSurrogate and 0xFFFF
                outOffset++
            }

            utf16In.position(inOffset - utf16In.arrayOffset())
            intBuffer!!.position(outOffset - intBuffer!!.arrayOffset())
        }

        private fun byteToCharBuffer(toAppend: Int) {
            byteBuffer.flip()
            val newBuffer =
                CharBuffer.allocate(
                    maxOf(byteBuffer.remaining() + toAppend, byteBuffer.capacity() / 2),
                )
            while (byteBuffer.hasRemaining()) {
                newBuffer.put((byteBuffer.get().toInt() and 0xFF).toChar())
            }
            type = Type.CHAR
            byteBuffer = ByteBuffer.allocate(0) // release
            charBuffer = newBuffer
        }

        private fun byteToIntBuffer(toAppend: Int) {
            byteBuffer.flip()
            val newBuffer =
                IntBuffer.allocate(
                    maxOf(byteBuffer.remaining() + toAppend, byteBuffer.capacity() / 4),
                )
            while (byteBuffer.hasRemaining()) {
                newBuffer.put(byteBuffer.get().toInt() and 0xFF)
            }
            type = Type.INT
            byteBuffer = ByteBuffer.allocate(0) // release
            intBuffer = newBuffer
        }

        private fun charToIntBuffer(toAppend: Int) {
            charBuffer!!.flip()
            val newBuffer =
                IntBuffer.allocate(
                    maxOf(charBuffer!!.remaining() + toAppend, charBuffer!!.capacity() / 2),
                )
            while (charBuffer!!.hasRemaining()) {
                newBuffer.put(charBuffer!!.get().code and 0xFFFF)
            }
            type = Type.INT
            charBuffer = null
            intBuffer = newBuffer
        }
    }
}
