/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime
import java.io.InputStream
import java.io.Reader
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CoderResult
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


/** This class represents the primary interface for creating [CharStream]s
 * from a variety of sources as of 4.7.  The motivation was to support
 * Unicode code points > U+FFFF.  [ANTLRInputStream] and
 * [ANTLRFileStream] are now deprecated in favor of the streams created
 * by this interface.
 *
 * DEPRECATED: `new ANTLRFileStream("myinputfile")`
 * NEW:        `CharStreams.fromFileName("myinputfile")`
 *
 * WARNING: If you use both the deprecated and the new streams, you will see
 * a nontrivial performance degradation. This speed hit is because the
 * [Lexer]'s internal code goes from a monomorphic to megamorphic
 * dynamic dispatch to get characters from the input stream. Java's
 * on-the-fly compiler (JIT) is unable to perform the same optimizations
 * so stick with either the old or the new streams, if performance is
 * a primary concern. See the extreme debugging and spelunking
 * needed to identify this issue in our timing rig:
 *
 * https://github.com/antlr/antlr4/pull/1781
 *
 * The ANTLR character streams still buffer all the input when you create
 * the stream, as they have done for ~20 years. If you need unbuffered
 * access, please note that it becomes challenging to create
 * parse trees. The parse tree has to point to tokens which will either
 * point into a stale location in an unbuffered stream or you have to copy
 * the characters out of the buffer into the token. That defeats the purpose
 * of unbuffered input. Per the ANTLR book, unbuffered streams are primarily
 * useful for processing infinite streams *during the parse.*
 *
 * The new streams also use 8-bit buffers when possible so this new
 * interface supports character streams that use half as much memory
 * as the old [ANTLRFileStream], which assumed 16-bit characters.
 *
 * A big shout out to Ben Hamilton (github bhamiltoncx) for his superhuman
 * efforts across all targets to get true Unicode 3.1 support for U+10FFFF.
 *
 * @since 4.7
 */
object CharStreams {
    private const val DEFAULT_BUFFER_SIZE = 4096

    /**
     * Creates a [CharStream] given a path to a UTF-8
     * encoded file on disk.
     *
     * Reads the entire contents of the file into the result before returning.
     */
    @kotlin.Throws(IOException::class)
    fun fromPath(path: Path): CharStream {
        return org.antlr.v4.runtime.CharStreams.fromPath(path, StandardCharsets.UTF_8)
    }

    /**
     * Creates a [CharStream] given a path to a file on disk and the
     * charset of the bytes contained in the file.
     *
     * Reads the entire contents of the file into the result before returning.
     */
    @kotlin.Throws(IOException::class)
    fun fromPath(path: Path, charset: Charset): CharStream {
        val size: Long = Files.size(path)
        Files.newByteChannel(path).use { channel ->
            return org.antlr.v4.runtime.CharStreams.fromChannel(
                channel,
                charset,
                org.antlr.v4.runtime.CharStreams.DEFAULT_BUFFER_SIZE,
                CodingErrorAction.REPLACE,
                path.toString(),
                size
            )
        }
    }

    /**
     * Creates a [CharStream] given a string containing a
     * path to a UTF-8 file on disk.
     *
     * Reads the entire contents of the file into the result before returning.
     */
    @kotlin.Throws(IOException::class)
    fun fromFileName(fileName: String?): CharStream {
        return org.antlr.v4.runtime.CharStreams.fromPath(Paths.get(fileName), StandardCharsets.UTF_8)
    }

    /**
     * Creates a [CharStream] given a string containing a
     * path to a file on disk and the charset of the bytes
     * contained in the file.
     *
     * Reads the entire contents of the file into the result before returning.
     */
    @kotlin.Throws(IOException::class)
    fun fromFileName(fileName: String?, charset: Charset): CharStream {
        return org.antlr.v4.runtime.CharStreams.fromPath(Paths.get(fileName), charset)
    }


    /**
     * Creates a [CharStream] given an opened [InputStream]
     * containing UTF-8 bytes.
     *
     * Reads the entire contents of the `InputStream` into
     * the result before returning, then closes the `InputStream`.
     */
    @kotlin.Throws(IOException::class)
    fun fromStream(`is`: InputStream?): CharStream {
        return org.antlr.v4.runtime.CharStreams.fromStream(`is`, StandardCharsets.UTF_8)
    }

    /**
     * Creates a [CharStream] given an opened [InputStream] and the
     * charset of the bytes contained in the stream.
     *
     * Reads the entire contents of the `InputStream` into
     * the result before returning, then closes the `InputStream`.
     */
    @kotlin.Throws(IOException::class)
    fun fromStream(`is`: InputStream?, charset: Charset): CharStream {
        return org.antlr.v4.runtime.CharStreams.fromStream(`is`, charset, -1)
    }

    @kotlin.Throws(IOException::class)
    fun fromStream(`is`: InputStream?, charset: Charset, inputSize: Long): CharStream {
        Channels.newChannel(`is`).use { channel ->
            return org.antlr.v4.runtime.CharStreams.fromChannel(
                channel,
                charset,
                org.antlr.v4.runtime.CharStreams.DEFAULT_BUFFER_SIZE,
                CodingErrorAction.REPLACE,
                IntStream.UNKNOWN_SOURCE_NAME,
                inputSize
            )
        }
    }

    /**
     * Creates a [CharStream] given an opened [ReadableByteChannel]
     * containing UTF-8 bytes.
     *
     * Reads the entire contents of the `channel` into
     * the result before returning, then closes the `channel`.
     */
    @kotlin.Throws(IOException::class)
    fun fromChannel(channel: ReadableByteChannel): CharStream {
        return org.antlr.v4.runtime.CharStreams.fromChannel(channel, StandardCharsets.UTF_8)
    }

    /**
     * Creates a [CharStream] given an opened [ReadableByteChannel] and the
     * charset of the bytes contained in the channel.
     *
     * Reads the entire contents of the `channel` into
     * the result before returning, then closes the `channel`.
     */
    @kotlin.Throws(IOException::class)
    fun fromChannel(channel: ReadableByteChannel, charset: Charset?): CharStream {
        return org.antlr.v4.runtime.CharStreams.fromChannel(
            channel,
            org.antlr.v4.runtime.CharStreams.DEFAULT_BUFFER_SIZE,
            CodingErrorAction.REPLACE,
            IntStream.UNKNOWN_SOURCE_NAME
        )
    }

    /**
     * Creates a [CharStream] given a [Reader]. Closes
     * the reader before returning.
     */
    @kotlin.Throws(IOException::class)
    fun fromReader(r: Reader): CodePointCharStream {
        return org.antlr.v4.runtime.CharStreams.fromReader(r, IntStream.UNKNOWN_SOURCE_NAME)
    }

    /**
     * Creates a [CharStream] given a [Reader] and its
     * source name. Closes the reader before returning.
     */
    @kotlin.Throws(IOException::class)
    fun fromReader(r: Reader, sourceName: String?): CodePointCharStream {
        try {
            val codePointBufferBuilder: CodePointBuffer.Builder =
                CodePointBuffer.builder(org.antlr.v4.runtime.CharStreams.DEFAULT_BUFFER_SIZE)
            val charBuffer: CharBuffer = CharBuffer.allocate(org.antlr.v4.runtime.CharStreams.DEFAULT_BUFFER_SIZE)
            while ((r.read(charBuffer)) !== -1) {
                charBuffer.flip()
                codePointBufferBuilder.append(charBuffer)
                charBuffer.compact()
            }
            return CodePointCharStream.fromBuffer(codePointBufferBuilder.build(), sourceName)
        } finally {
            r.close()
        }
    }

    /**
     * Creates a [CharStream] given a [String].
     */
    fun fromString(s: String): CodePointCharStream {
        return org.antlr.v4.runtime.CharStreams.fromString(s, IntStream.UNKNOWN_SOURCE_NAME)
    }

    /**
     * Creates a [CharStream] given a [String] and the `sourceName`
     * from which it came.
     */
    fun fromString(s: String, sourceName: String?): CodePointCharStream {
        // Initial guess assumes no code points > U+FFFF: one code
        // point for each code unit in the string
        val codePointBufferBuilder: CodePointBuffer.Builder = CodePointBuffer.builder(s.length())
        // TODO: CharBuffer.wrap(String) rightfully returns a read-only buffer
        // which doesn't expose its array, so we make a copy.
        val cb: CharBuffer = CharBuffer.allocate(s.length())
        cb.put(s)
        cb.flip()
        codePointBufferBuilder.append(cb)
        return CodePointCharStream.fromBuffer(codePointBufferBuilder.build(), sourceName)
    }

    /**
     * Creates a [CharStream] given an opened [ReadableByteChannel]
     * containing UTF-8 bytes.
     *
     * Reads the entire contents of the `channel` into
     * the result before returning, then closes the `channel`.
     */
    @kotlin.Throws(IOException::class)
    fun fromChannel(
        channel: ReadableByteChannel,
        bufferSize: Int,
        decodingErrorAction: CodingErrorAction,
        sourceName: String?
    ): CodePointCharStream {
        return org.antlr.v4.runtime.CharStreams.fromChannel(
            channel,
            StandardCharsets.UTF_8,
            bufferSize,
            decodingErrorAction,
            sourceName,
            -1
        )
    }

    @kotlin.Throws(IOException::class)
    fun fromChannel(
        channel: ReadableByteChannel,
        charset: Charset,
        bufferSize: Int,
        decodingErrorAction: CodingErrorAction,
        sourceName: String?,
        inputSize: Long
    ): CodePointCharStream {
        var inputSize = inputSize
        try {
            val utf8BytesIn: ByteBuffer = ByteBuffer.allocate(bufferSize)
            val utf16CodeUnitsOut: CharBuffer = CharBuffer.allocate(bufferSize)
            if (inputSize == -1L) {
                inputSize = bufferSize.toLong()
            } else if (inputSize > Int.MAX_VALUE) {
                // ByteBuffer et al don't support long sizes
                throw IOException(String.format("inputSize %d larger than max %d", inputSize, Int.MAX_VALUE))
            }
            val codePointBufferBuilder: CodePointBuffer.Builder = CodePointBuffer.builder(inputSize.toInt())
            val decoder: CharsetDecoder = charset
                .newDecoder()
                .onMalformedInput(decodingErrorAction)
                .onUnmappableCharacter(decodingErrorAction)

            var endOfInput = false
            while (!endOfInput) {
                val bytesRead: Int = channel.read(utf8BytesIn)
                endOfInput = (bytesRead == -1)
                utf8BytesIn.flip()
                val result: CoderResult = decoder.decode(
                    utf8BytesIn,
                    utf16CodeUnitsOut,
                    endOfInput
                )
                if (result.isError() && decodingErrorAction.equals(CodingErrorAction.REPORT)) {
                    result.throwException()
                }
                utf16CodeUnitsOut.flip()
                codePointBufferBuilder.append(utf16CodeUnitsOut)
                utf8BytesIn.compact()
                utf16CodeUnitsOut.compact()
            }
            // Handle any bytes at the end of the file which need to
            // be represented as errors or substitution characters.
            val flushResult: CoderResult = decoder.flush(utf16CodeUnitsOut)
            if (flushResult.isError() && decodingErrorAction.equals(CodingErrorAction.REPORT)) {
                flushResult.throwException()
            }
            utf16CodeUnitsOut.flip()
            codePointBufferBuilder.append(utf16CodeUnitsOut)

            val codePointBuffer: CodePointBuffer? = codePointBufferBuilder.build()
            return CodePointCharStream.fromBuffer(codePointBuffer, sourceName)
        } finally {
            channel.close()
        }
    }
}
