/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.IntBuffer

/**
 * Wrapper for [ByteBuffer] / [CharBuffer] / [IntBuffer].
 *
 * Because Java lacks generics on primitive types, these three types
 * do not share an interface, so we have to write one manually.
 */
class CodePointBuffer private constructor(
    val type: Type,
    byteBuffer: ByteBuffer,
    charBuffer: CharBuffer,
    intBuffer: IntBuffer,
) {
    enum class Type {
        BYTE,
        CHAR,
        INT,
    }

    private val byteBuffer: ByteBuffer
    private val charBuffer: CharBuffer
    private val intBuffer: IntBuffer

    init {
        this.byteBuffer = byteBuffer
        this.charBuffer = charBuffer
        this.intBuffer = intBuffer
    }

    fun position(): Int {
        when (type) {
            org.antlr.v4.runtime.CodePointBuffer.Type.BYTE -> return byteBuffer.position()
            org.antlr.v4.runtime.CodePointBuffer.Type.CHAR -> return charBuffer.position()
            org.antlr.v4.runtime.CodePointBuffer.Type.INT -> return intBuffer.position()
        }
        throw UnsupportedOperationException("Not reached")
    }

    fun position(newPosition: Int) {
        when (type) {
            org.antlr.v4.runtime.CodePointBuffer.Type.BYTE -> byteBuffer.position(newPosition)
            org.antlr.v4.runtime.CodePointBuffer.Type.CHAR -> charBuffer.position(newPosition)
            org.antlr.v4.runtime.CodePointBuffer.Type.INT -> intBuffer.position(newPosition)
        }
    }

    fun remaining(): Int {
        when (type) {
            org.antlr.v4.runtime.CodePointBuffer.Type.BYTE -> return byteBuffer.remaining()
            org.antlr.v4.runtime.CodePointBuffer.Type.CHAR -> return charBuffer.remaining()
            org.antlr.v4.runtime.CodePointBuffer.Type.INT -> return intBuffer.remaining()
        }
        throw UnsupportedOperationException("Not reached")
    }

    fun get(offset: Int): Int {
        when (type) {
            org.antlr.v4.runtime.CodePointBuffer.Type.BYTE -> return byteBuffer.get(offset)
            org.antlr.v4.runtime.CodePointBuffer.Type.CHAR -> return charBuffer.get(offset)
            org.antlr.v4.runtime.CodePointBuffer.Type.INT -> return intBuffer.get(offset)
        }
        throw UnsupportedOperationException("Not reached")
    }

    fun arrayOffset(): Int {
        when (type) {
            org.antlr.v4.runtime.CodePointBuffer.Type.BYTE -> return byteBuffer.arrayOffset()
            org.antlr.v4.runtime.CodePointBuffer.Type.CHAR -> return charBuffer.arrayOffset()
            org.antlr.v4.runtime.CodePointBuffer.Type.INT -> return intBuffer.arrayOffset()
        }
        throw UnsupportedOperationException("Not reached")
    }

    fun byteArray(): ByteArray {
        assert(type == org.antlr.v4.runtime.CodePointBuffer.Type.BYTE)
        return byteBuffer.array()
    }

    fun charArray(): CharArray {
        assert(type == org.antlr.v4.runtime.CodePointBuffer.Type.CHAR)
        return charBuffer.array()
    }

    fun intArray(): IntArray {
        assert(type == org.antlr.v4.runtime.CodePointBuffer.Type.INT)
        return intBuffer.array()
    }

    class Builder private constructor(
        initialBufferSize: Int,
    ) {
        var type: Type
            private set
        private var byteBuffer: ByteBuffer?
        private var charBuffer: CharBuffer? = null
        private var intBuffer: IntBuffer? = null
        private var prevHighSurrogate: Int

        init {
            type = org.antlr.v4.runtime.CodePointBuffer.Type.BYTE
            byteBuffer = ByteBuffer.allocate(initialBufferSize)
            prevHighSurrogate = -1
        }

        fun getByteBuffer(): ByteBuffer? = byteBuffer

        fun getCharBuffer(): CharBuffer? = charBuffer

        fun getIntBuffer(): IntBuffer? = intBuffer

        fun build(): CodePointBuffer {
            when (type) {
                org.antlr.v4.runtime.CodePointBuffer.Type.BYTE -> byteBuffer.flip()
                org.antlr.v4.runtime.CodePointBuffer.Type.CHAR -> charBuffer.flip()
                org.antlr.v4.runtime.CodePointBuffer.Type.INT -> intBuffer.flip()
            }
            return org.antlr.v4.runtime
                .CodePointBuffer(type, byteBuffer, charBuffer, intBuffer)
        }

        fun ensureRemaining(remainingNeeded: Int) {
            when (type) {
                org.antlr.v4.runtime.CodePointBuffer.Type.BYTE ->
                    if (byteBuffer.remaining() < remainingNeeded) {
                        val newCapacity: Int =
                            org.antlr.v4.runtime.CodePointBuffer.Builder.Companion.roundUpToNextPowerOfTwo(
                                byteBuffer.capacity() + remainingNeeded,
                            )
                        val newBuffer: ByteBuffer = ByteBuffer.allocate(newCapacity)
                        byteBuffer.flip()
                        newBuffer.put(byteBuffer)
                        byteBuffer = newBuffer
                    }

                org.antlr.v4.runtime.CodePointBuffer.Type.CHAR ->
                    if (charBuffer.remaining() < remainingNeeded) {
                        val newCapacity: Int =
                            org.antlr.v4.runtime.CodePointBuffer.Builder.Companion.roundUpToNextPowerOfTwo(
                                charBuffer.capacity() + remainingNeeded,
                            )
                        val newBuffer: CharBuffer = CharBuffer.allocate(newCapacity)
                        charBuffer.flip()
                        newBuffer.put(charBuffer)
                        charBuffer = newBuffer
                    }

                org.antlr.v4.runtime.CodePointBuffer.Type.INT ->
                    if (intBuffer.remaining() < remainingNeeded) {
                        val newCapacity: Int =
                            org.antlr.v4.runtime.CodePointBuffer.Builder.Companion.roundUpToNextPowerOfTwo(
                                intBuffer.capacity() + remainingNeeded,
                            )
                        val newBuffer: IntBuffer = IntBuffer.allocate(newCapacity)
                        intBuffer.flip()
                        newBuffer.put(intBuffer)
                        intBuffer = newBuffer
                    }
            }
        }

        fun append(utf16In: CharBuffer) {
            ensureRemaining(utf16In.remaining())
            if (utf16In.hasArray()) {
                appendArray(utf16In)
            } else {
                // TODO
                throw UnsupportedOperationException("TODO")
            }
        }

        private fun appendArray(utf16In: CharBuffer) {
            assert(utf16In.hasArray())

            when (type) {
                org.antlr.v4.runtime.CodePointBuffer.Type.BYTE -> appendArrayByte(utf16In)
                org.antlr.v4.runtime.CodePointBuffer.Type.CHAR -> appendArrayChar(utf16In)
                org.antlr.v4.runtime.CodePointBuffer.Type.INT -> appendArrayInt(utf16In)
            }
        }

        private fun appendArrayByte(utf16In: CharBuffer) {
            assert(prevHighSurrogate == -1)

            val `in`: CharArray = utf16In.array()
            var inOffset: Int = utf16In.arrayOffset() + utf16In.position()
            val inLimit: Int = utf16In.arrayOffset() + utf16In.limit()

            val outByte: ByteArray = byteBuffer.array()
            var outOffset: Int = byteBuffer.arrayOffset() + byteBuffer.position()

            while (inOffset < inLimit) {
                val c = `in`[inOffset]
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

            val `in`: CharArray = utf16In.array()
            var inOffset: Int = utf16In.arrayOffset() + utf16In.position()
            val inLimit: Int = utf16In.arrayOffset() + utf16In.limit()

            val outChar: CharArray = charBuffer.array()
            var outOffset: Int = charBuffer.arrayOffset() + charBuffer.position()

            while (inOffset < inLimit) {
                val c = `in`[inOffset]
                if (!Character.isHighSurrogate(c)) {
                    outChar[outOffset] = c
                } else {
                    utf16In.position(inOffset - utf16In.arrayOffset())
                    charBuffer.position(outOffset - charBuffer.arrayOffset())
                    charToIntBuffer(utf16In.remaining())
                    appendArrayInt(utf16In)
                    return
                }
                inOffset++
                outOffset++
            }

            utf16In.position(inOffset - utf16In.arrayOffset())
            charBuffer.position(outOffset - charBuffer.arrayOffset())
        }

        private fun appendArrayInt(utf16In: CharBuffer) {
            val `in`: CharArray = utf16In.array()
            var inOffset: Int = utf16In.arrayOffset() + utf16In.position()
            val inLimit: Int = utf16In.arrayOffset() + utf16In.limit()

            val outInt: IntArray = intBuffer.array()
            var outOffset: Int = intBuffer.arrayOffset() + intBuffer.position()

            while (inOffset < inLimit) {
                val c = `in`[inOffset]
                inOffset++
                if (prevHighSurrogate != -1) {
                    if (Character.isLowSurrogate(c)) {
                        outInt[outOffset] = Character.toCodePoint(prevHighSurrogate.toChar(), c)
                        outOffset++
                        prevHighSurrogate = -1
                    } else {
                        // Dangling high surrogate
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
                // Dangling high surrogate
                outInt[outOffset] = prevHighSurrogate and 0xFFFF
                outOffset++
            }

            utf16In.position(inOffset - utf16In.arrayOffset())
            intBuffer.position(outOffset - intBuffer.arrayOffset())
        }

        private fun byteToCharBuffer(toAppend: Int) {
            byteBuffer.flip()
            // CharBuffers hold twice as much per unit as ByteBuffers, so start with half the capacity.
            val newBuffer: CharBuffer =
                CharBuffer.allocate(maxOf(byteBuffer.remaining() + toAppend, byteBuffer.capacity() / 2))
            while (byteBuffer.hasRemaining()) {
                newBuffer.put((byteBuffer.get() and 0xFF) as Char)
            }
            type = org.antlr.v4.runtime.CodePointBuffer.Type.CHAR
            byteBuffer = null
            charBuffer = newBuffer
        }

        private fun byteToIntBuffer(toAppend: Int) {
            byteBuffer.flip()
            // IntBuffers hold four times as much per unit as ByteBuffers, so start with one quarter the capacity.
            val newBuffer: IntBuffer =
                IntBuffer.allocate(maxOf(byteBuffer.remaining() + toAppend, byteBuffer.capacity() / 4))
            while (byteBuffer.hasRemaining()) {
                newBuffer.put(byteBuffer.get() and 0xFF)
            }
            type = org.antlr.v4.runtime.CodePointBuffer.Type.INT
            byteBuffer = null
            intBuffer = newBuffer
        }

        private fun charToIntBuffer(toAppend: Int) {
            charBuffer.flip()
            // IntBuffers hold two times as much per unit as ByteBuffers, so start with one half the capacity.
            val newBuffer: IntBuffer =
                IntBuffer.allocate(maxOf(charBuffer.remaining() + toAppend, charBuffer.capacity() / 2))
            while (charBuffer.hasRemaining()) {
                newBuffer.put(charBuffer.get() and 0xFFFF)
            }
            type = org.antlr.v4.runtime.CodePointBuffer.Type.INT
            charBuffer = null
            intBuffer = newBuffer
        }

        companion object {
            private fun roundUpToNextPowerOfTwo(i: Int): Int {
                val nextPowerOfTwo: Int = 32 - Integer.numberOfLeadingZeros(i - 1)
                return Math.pow(2, nextPowerOfTwo) as Int
            }
        }
    }

    companion object {
        fun withBytes(byteBuffer: ByteBuffer): CodePointBuffer =
            org.antlr.v4.runtime.CodePointBuffer(
                org.antlr.v4.runtime.CodePointBuffer.Type.BYTE,
                byteBuffer,
                null,
                null,
            )

        fun withChars(charBuffer: CharBuffer): CodePointBuffer =
            org.antlr.v4.runtime.CodePointBuffer(
                org.antlr.v4.runtime.CodePointBuffer.Type.CHAR,
                null,
                charBuffer,
                null,
            )

        fun withInts(intBuffer: IntBuffer): CodePointBuffer =
            org.antlr.v4.runtime.CodePointBuffer(
                org.antlr.v4.runtime.CodePointBuffer.Type.INT,
                null,
                null,
                intBuffer,
            )

        fun builder(initialBufferSize: Int): Builder =
            org.antlr.v4.runtime.CodePointBuffer
                .Builder(initialBufferSize)
    }
}
