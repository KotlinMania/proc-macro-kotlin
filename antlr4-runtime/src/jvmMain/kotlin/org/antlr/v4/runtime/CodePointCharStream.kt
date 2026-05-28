/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime

import org.antlr.v4.runtime.misc.Interval
import java.nio.charset.StandardCharsets

/**
 * Alternative to [ANTLRInputStream] which treats the input
 * as a series of Unicode code points, instead of a series of UTF-16
 * code units.
 *
 * Use this if you need to parse input which potentially contains
 * Unicode values > U+FFFF.
 */
abstract class CodePointCharStream private constructor(position: Int, remaining: Int, name: String?) : CharStream {
    protected val size: Int
    protected val name: String?

    // To avoid lots of virtual method calls, we directly access
    // the state of the underlying code points in the
    // CodePointBuffer.
    protected var position: Int

    // Use the factory method {@link #fromBuffer(CodePointBuffer)} to
    // construct instances of this type.
    init {
        // TODO
        assert(position == 0)
        this.size = remaining
        this.name = name
        this.position = 0
    }

    // Visible for testing.
    abstract val internalStorage: Any?
    fun consume() {
        if (size - position == 0) {
            assert(LA(1) === IntStream.EOF)
            throw IllegalStateException("cannot consume EOF")
        }
        position = position + 1
    }
    fun index(): Int {
        return position
    }
    fun size(): Int {
        return size
    }

    /** mark/release do nothing; we have entire buffer  */
    fun mark(): Int {
        return -1
    }
    fun release(marker: Int) {
    }
    fun seek(index: Int) {
        position = index
    }
    val sourceName: String?
        get() {
            if (name == null || name.isEmpty()) {
                return IntStream.UNKNOWN_SOURCE_NAME
            }

            return name
        }
    fun toString(): String {
        return getText(Interval.of(0, size - 1))
    }

    // 8-bit storage for code points <= U+00FF.
    private class CodePoint8BitCharStream(
        position: Int,
        remaining: Int,
        name: String?,
        byteArray: ByteArray,
        arrayOffset: Int
    ) : CodePointCharStream(position, remaining, name) {
        private val byteArray: ByteArray

        init {
            // TODO
            assert(arrayOffset == 0)
            this.byteArray = byteArray
        }

        /** Return the UTF-16 encoded string for the given interval  */
        fun getText(interval: Interval): String {
            val startIdx: Int = minOf(interval.a, size)
            val len: Int = minOf(interval.b - interval.a + 1, size - startIdx)

            // We know the maximum code point in byteArray is U+00FF,
            // so we can treat this as if it were ISO-8859-1, aka Latin-1,
            // which shares the same code points up to 0xFF.
            return String(byteArray, startIdx, len, StandardCharsets.ISO_8859_1)
        }
        fun LA(i: Int): Int {
            val offset: Int
            when (Integer.signum(i)) {
                -1 -> {
                    offset = position + i
                    if (offset < 0) {
                        return IntStream.EOF
                    }
                    return byteArray[offset].toInt() and 0xFF
                }

                0 ->                    // Undefined
                    return 0

                1 -> {
                    offset = position + i - 1
                    if (offset >= size) {
                        return IntStream.EOF
                    }
                    return byteArray[offset].toInt() and 0xFF
                }
            }
            throw UnsupportedOperationException("Not reached")
        }
        override fun getInternalStorage(): Any {
            return byteArray
        }
    }

    // 16-bit internal storage for code points between U+0100 and U+FFFF.
    private class CodePoint16BitCharStream(
        position: Int,
        remaining: Int,
        name: String?,
        private val charArray: CharArray,
        arrayOffset: Int
    ) : CodePointCharStream(position, remaining, name) {
        init {
            // TODO
            assert(arrayOffset == 0)
        }

        /** Return the UTF-16 encoded string for the given interval  */
        fun getText(interval: Interval): String {
            val startIdx: Int = minOf(interval.a, size)
            val len: Int = minOf(interval.b - interval.a + 1, size - startIdx)

            // We know there are no surrogates in this
            // array, since otherwise we would be given a
            // 32-bit int[] array.
            //
            // So, it's safe to treat this as if it were
            // UTF-16.
            return String(charArray, startIdx, len)
        }
        fun LA(i: Int): Int {
            val offset: Int
            when (Integer.signum(i)) {
                -1 -> {
                    offset = position + i
                    if (offset < 0) {
                        return IntStream.EOF
                    }
                    return charArray[offset].code and 0xFFFF
                }

                0 ->                    // Undefined
                    return 0

                1 -> {
                    offset = position + i - 1
                    if (offset >= size) {
                        return IntStream.EOF
                    }
                    return charArray[offset].code and 0xFFFF
                }
            }
            throw UnsupportedOperationException("Not reached")
        }
        override fun getInternalStorage(): Any {
            return charArray
        }
    }

    // 32-bit internal storage for code points between U+10000 and U+10FFFF.
    private class CodePoint32BitCharStream(
        position: Int,
        remaining: Int,
        name: String?,
        private val intArray: IntArray,
        arrayOffset: Int
    ) : CodePointCharStream(position, remaining, name) {
        init {
            // TODO
            assert(arrayOffset == 0)
        }

        /** Return the UTF-16 encoded string for the given interval  */
        fun getText(interval: Interval): String {
            val startIdx: Int = minOf(interval.a, size)
            val len: Int = minOf(interval.b - interval.a + 1, size - startIdx)

            // Note that we pass the int[] code points to the String constructor --
            // this is supported, and the constructor will convert to UTF-16 internally.
            return String(intArray, startIdx, len)
        }
        fun LA(i: Int): Int {
            val offset: Int
            when (Integer.signum(i)) {
                -1 -> {
                    offset = position + i
                    if (offset < 0) {
                        return IntStream.EOF
                    }
                    return intArray[offset]
                }

                0 ->                    // Undefined
                    return 0

                1 -> {
                    offset = position + i - 1
                    if (offset >= size) {
                        return IntStream.EOF
                    }
                    return intArray[offset]
                }
            }
            throw UnsupportedOperationException("Not reached")
        }
        override fun getInternalStorage(): Any {
            return intArray
        }
    }

    companion object {
        /**
         * Constructs a [CodePointCharStream] which provides access
         * to the Unicode code points stored in `codePointBuffer`.
         */
        fun fromBuffer(codePointBuffer: CodePointBuffer): CodePointCharStream {
            return org.antlr.v4.runtime.CodePointCharStream.Companion.fromBuffer(codePointBuffer, IntStream.UNKNOWN_SOURCE_NAME)
        }

        /**
         * Constructs a named [CodePointCharStream] which provides access
         * to the Unicode code points stored in `codePointBuffer`.
         */
        fun fromBuffer(codePointBuffer: CodePointBuffer, name: String?): CodePointCharStream {
            // Java lacks generics on primitive types.
            //
            // To avoid lots of calls to virtual methods in the
            // very hot codepath of LA() below, we construct one
            // of three concrete subclasses.
            //
            // The concrete subclasses directly access the code
            // points stored in the underlying array (byte[],
            // char[], or int[]), so we can avoid lots of virtual
            // method calls to ByteBuffer.get(offset).
            when (codePointBuffer.type) {
                BYTE -> return org.antlr.v4.runtime.CodePointCharStream.CodePoint8BitCharStream(
                    codePointBuffer.position(),
                    codePointBuffer.remaining(),
                    name,
                    codePointBuffer.byteArray(),
                    codePointBuffer.arrayOffset()
                )

                CHAR -> return org.antlr.v4.runtime.CodePointCharStream.CodePoint16BitCharStream(
                    codePointBuffer.position(),
                    codePointBuffer.remaining(),
                    name,
                    codePointBuffer.charArray(),
                    codePointBuffer.arrayOffset()
                )

                INT -> return org.antlr.v4.runtime.CodePointCharStream.CodePoint32BitCharStream(
                    codePointBuffer.position(),
                    codePointBuffer.remaining(),
                    name,
                    codePointBuffer.intArray(),
                    codePointBuffer.arrayOffset()
                )
            }
            throw UnsupportedOperationException("Not reached")
        }
    }
}
