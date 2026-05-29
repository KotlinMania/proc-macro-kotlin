/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime

import java.io.IOException
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

object CharStreams {
    private const val DEFAULT_BUFFER_SIZE = 4096

    @kotlin.Throws(IOException::class)
    fun fromPath(path: Path): CharStream = fromPath(path, StandardCharsets.UTF_8)

    @kotlin.Throws(IOException::class)
    fun fromPath(
        path: Path,
        charset: Charset,
    ): CharStream {
        val size: Long = Files.size(path)
        Files.newByteChannel(path).use { channel ->
            return fromChannel(
                channel,
                charset,
                DEFAULT_BUFFER_SIZE,
                CodingErrorAction.REPLACE,
                path.toString(),
                size,
            )
        }
    }

    @kotlin.Throws(IOException::class)
    fun fromFileName(fileName: String): CharStream = fromPath(Paths.get(fileName), StandardCharsets.UTF_8)

    @kotlin.Throws(IOException::class)
    fun fromFileName(
        fileName: String,
        charset: Charset,
    ): CharStream = fromPath(Paths.get(fileName), charset)

    @kotlin.Throws(IOException::class)
    fun fromStream(`is`: InputStream): CodePointCharStream = fromStream(`is`, IntStream.UNKNOWN_SOURCE_NAME)

    @kotlin.Throws(IOException::class)
    fun fromStream(
        `is`: InputStream,
        sourceName: String,
    ): CodePointCharStream =
        fromChannel(
            Channels.newChannel(`is`),
            StandardCharsets.UTF_8,
            DEFAULT_BUFFER_SIZE,
            CodingErrorAction.REPLACE,
            sourceName,
            -1,
        )

    @kotlin.Throws(IOException::class)
    fun fromReader(r: Reader): CodePointCharStream = fromReader(r, IntStream.UNKNOWN_SOURCE_NAME)

    @kotlin.Throws(IOException::class)
    fun fromReader(
        r: Reader,
        sourceName: String,
    ): CodePointCharStream {
        try {
            val codePointBufferBuilder: CodePointBuffer.Builder =
                CodePointBuffer.builder(DEFAULT_BUFFER_SIZE)
            val charBuffer: CharBuffer = CharBuffer.allocate(DEFAULT_BUFFER_SIZE)
            while ((r.read(charBuffer)) != -1) {
                charBuffer.flip()
                codePointBufferBuilder.append(charBuffer)
                charBuffer.compact()
            }
            return CodePointCharStream.fromBuffer(codePointBufferBuilder.build(), sourceName)
        } finally {
            r.close()
        }
    }

    fun fromString(s: String): CodePointCharStream = fromString(s, IntStream.UNKNOWN_SOURCE_NAME)

    fun fromString(
        s: String,
        sourceName: String,
    ): CodePointCharStream {
        val codePointBufferBuilder: CodePointBuffer.Builder = CodePointBuffer.builder(s.length)
        val cb: CharBuffer = CharBuffer.allocate(s.length)
        cb.put(s)
        cb.flip()
        codePointBufferBuilder.append(cb)
        return CodePointCharStream.fromBuffer(codePointBufferBuilder.build(), sourceName)
    }

    @kotlin.Throws(IOException::class)
    fun fromChannel(
        channel: ReadableByteChannel,
        bufferSize: Int,
        decodingErrorAction: CodingErrorAction,
        sourceName: String,
    ): CodePointCharStream = fromChannel(channel, StandardCharsets.UTF_8, bufferSize, decodingErrorAction, sourceName, -1)

    @kotlin.Throws(IOException::class)
    fun fromChannel(
        channel: ReadableByteChannel,
        charset: Charset,
        bufferSize: Int,
        decodingErrorAction: CodingErrorAction,
        sourceName: String,
        inputSize: Long,
    ): CodePointCharStream {
        try {
            val utf8BytesIn: ByteBuffer = ByteBuffer.allocate(bufferSize)
            val utf16CodeUnitsOut: CharBuffer = CharBuffer.allocate(bufferSize)
            var inputSize = inputSize
            if (inputSize == -1L) {
                inputSize = bufferSize.toLong()
            } else if (inputSize > Int.MAX_VALUE) {
                throw IOException(String.format("inputSize %d larger than max %d", inputSize, Int.MAX_VALUE))
            }
            val codePointBufferBuilder: CodePointBuffer.Builder = CodePointBuffer.builder(inputSize.toInt())
            val decoder: CharsetDecoder =
                charset
                    .newDecoder()
                    .onMalformedInput(decodingErrorAction)
                    .onUnmappableCharacter(decodingErrorAction)
            var endOfInput = false
            while (!endOfInput) {
                val bytesRead: Int = channel.read(utf8BytesIn)
                endOfInput = (bytesRead == -1)
                utf8BytesIn.flip()
                val result: CoderResult =
                    decoder.decode(
                        utf8BytesIn,
                        utf16CodeUnitsOut,
                        endOfInput,
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
            val codePointBuffer: CodePointBuffer = codePointBufferBuilder.build()
            return CodePointCharStream.fromBuffer(codePointBuffer, sourceName)
        } finally {
            channel.close()
        }
    }
}
