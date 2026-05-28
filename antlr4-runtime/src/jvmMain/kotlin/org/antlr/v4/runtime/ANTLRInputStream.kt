/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime

import org.antlr.v4.runtime.misc.Interval
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.util.Arrays

/**
 * Vacuum all input from a [Reader]/[InputStream] and then treat it
 * like a `char[]` buffer. Can also pass in a [String] or
 * `char[]` to use.
 *
 *
 * If you need encoding, pass in stream/reader with correct encoding.
 *
 */
@Deprecated
@Deprecated("as of 4.7 Please use {@link CharStreams} interface.")
class ANTLRInputStream : CharStream {
    /** The data being scanned  */
    protected var data: CharArray

    /** How many characters are actually in the buffer  */
    protected var n: Int = 0

    /** 0..n-1 index into string of next char  */
    protected var p: Int = 0

    /** What is name or source of this char stream?  */
    var name: String? = null

    constructor()

    /** Copy data in string to a local char array  */
    constructor(input: String) {
        this.data = input.toCharArray()
        this.n = input.length()
    }

    /** This is the preferred constructor for strings as no data is copied  */
    constructor(data: CharArray, numberOfActualCharsInArray: Int) {
        this.data = data
        this.n = numberOfActualCharsInArray
    }

    constructor(r: Reader?) : this(
        r,
        org.antlr.v4.runtime.ANTLRInputStream.Companion.INITIAL_BUFFER_SIZE,
        org.antlr.v4.runtime.ANTLRInputStream.Companion.READ_BUFFER_SIZE
    )

    constructor(r: Reader?, initialSize: Int) : this(
        r,
        initialSize,
        org.antlr.v4.runtime.ANTLRInputStream.Companion.READ_BUFFER_SIZE
    )

    constructor(r: Reader?, initialSize: Int, readChunkSize: Int) {
        load(r, initialSize, readChunkSize)
    }

    constructor(input: InputStream?) : this(
        InputStreamReader(input),
        org.antlr.v4.runtime.ANTLRInputStream.Companion.INITIAL_BUFFER_SIZE
    )

    constructor(input: InputStream?, initialSize: Int) : this(InputStreamReader(input), initialSize)

    constructor(input: InputStream?, initialSize: Int, readChunkSize: Int) : this(
        InputStreamReader(input),
        initialSize,
        readChunkSize
    )

    @kotlin.Throws(IOException::class)
    fun load(r: Reader?, size: Int, readChunkSize: Int) {
        var size = size
        var readChunkSize = readChunkSize
        if (r == null) {
            return
        }
        if (size <= 0) {
            size = org.antlr.v4.runtime.ANTLRInputStream.Companion.INITIAL_BUFFER_SIZE
        }
        if (readChunkSize <= 0) {
            readChunkSize = org.antlr.v4.runtime.ANTLRInputStream.Companion.READ_BUFFER_SIZE
        }
        // println("load "+size+" in chunks of "+readChunkSize);
        try {
            // alloc initial buffer size.
            data = CharArray(size)
            // read all the data in chunks of readChunkSize
            var numRead = 0
            var p = 0
            do {
                if (p + readChunkSize > data.size) { // overflow?
                    // println("### overflow p="+p+", data.length="+data.length);
                    data = data.copyOf(data.size * 2)
                }
                numRead = r.read(data, p, readChunkSize)
                // println("read "+numRead+" chars; p was "+p+" is now "+(p+numRead));
                p += numRead
            } while (numRead != -1) // while not EOF
            // set the actual size of the data available;
            // EOF subtracted one above in p+=numRead; add one back
            n = p + 1
            //println("n="+n);
        } finally {
            r.close()
        }
    }

    /** Reset the stream so that it's in the same state it was
     * when the object was created *except* the data array is not
     * touched.
     */
    fun reset() {
        p = 0
    }
    fun consume() {
        if (p >= n) {
            assert(LA(1) == IntStream.EOF)
            throw IllegalStateException("cannot consume EOF")
        }

        //println("prev p="+p+", c="+(char)data[p]);
        if (p < n) {
            p++
            //println("p moves to "+p+" (c='"+(char)data[p]+"')");
        }
    }
    fun LA(i: Int): Int {
        var i = i
        if (i == 0) {
            return 0 // undefined
        }
        if (i < 0) {
            i++ // e.g., translate LA(-1) to use offset i=0; then data[p+0-1]
            if ((p + i - 1) < 0) {
                return IntStream.EOF // invalid; no char before first char
            }
        }

        if ((p + i - 1) >= n) {
            //println("char LA("+i+")=EOF; p="+p);
            return IntStream.EOF
        }
        //println("char LA("+i+")="+(char)data[p+i-1]+"; p="+p);
        //println("LA("+i+"); p="+p+" n="+n+" data.length="+data.length);
        return data[p + i - 1].code
    }

    fun LT(i: Int): Int {
        return LA(i)
    }

    /** Return the current input symbol index 0..n where n indicates the
     * last symbol has been read.  The index is the index of char to
     * be returned from LA(1).
     */
    fun index(): Int {
        return p
    }
    fun size(): Int {
        return n
    }

    /** mark/release do nothing; we have entire buffer  */
    fun mark(): Int {
        return -1
    }
    fun release(marker: Int) {
    }

    /** consume() ahead until p==index; can't just set p=index as we must
     * update line and charPositionInLine. If we seek backwards, just set p
     */
    fun seek(index: Int) {
        var index = index
        if (index <= p) {
            p = index // just jump; don't update stream state (line, ...)
            return
        }
        // seek forward, consume until p hits index or n (whichever comes first)
        index = minOf(index, n)
        while (p < index) {
            consume()
        }
    }
    fun getText(interval: Interval): String? {
        val start: Int = interval.a
        var stop: Int = interval.b
        if (stop >= n) stop = n - 1
        val count = stop - start + 1
        if (start >= n) return ""
        //		println("data: "+data.contentToString()+", n="+n+
//						   ", start="+start+
//						   ", stop="+stop);
        return String(data, start, count)
    }
    val sourceName: String?
        get() {
            if (name == null || name.isEmpty()) {
                return IntStream.UNKNOWN_SOURCE_NAME
            }

            return name
        }
    fun toString(): String? {
        return String(data)
    }

    companion object {
        const val READ_BUFFER_SIZE: Int = 1024
        const val INITIAL_BUFFER_SIZE: Int = 1024
    }
}
