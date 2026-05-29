@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package org.antlr.v4.runtime

import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.assert

@Deprecated("as of 4.7 Please use CharStreams interface.")
open class ANTLRInputStream : CharStream {
    protected var data: CharArray
    protected var n: Int = 0
    protected var p: Int = 0
    var name: String? = null

    constructor() {
        this.data = CharArray(0)
    }

    constructor(input: String) {
        this.data = input.toCharArray()
        this.n = input.length
    }

    constructor(data: CharArray, numberOfActualCharsInArray: Int) {
        this.data = data
        this.n = numberOfActualCharsInArray
    }

    fun reset() {
        p = 0
    }

    override fun consume() {
        if (p >= n) {
            assert(LA(1) == IntStream.EOF)
            throw IllegalStateException("cannot consume EOF")
        }
        if (p < n) {
            p++
        }
    }

    override fun LA(i: Int): Int {
        var i = i
        if (i == 0) return 0
        if (i < 0) {
            i++
            if ((p + i - 1) < 0) return IntStream.EOF
        }
        if ((p + i - 1) >= n) return IntStream.EOF
        return data[p + i - 1].code
    }

    fun LT(i: Int): Int = LA(i)

    override fun index(): Int = p

    override fun size(): Int = n

    override fun mark(): Int = -1

    override fun release(marker: Int) {}

    override fun seek(index: Int) {
        var index = index
        if (index <= p) {
            p = index
            return
        }
        index = minOf(index, n)
        while (p < index) {
            consume()
        }
    }

    override fun getText(interval: Interval?): String? {
        val start = interval!!.a
        var stop = interval!!.b
        if (stop >= n) stop = n - 1
        val count = stop - start + 1
        if (start >= n) return ""
        return data.concatToString(start, start + count)
    }

    override val sourceName: String?
        get() {
            val n = name
        if (n == null || n.isEmpty()) {
                return IntStream.UNKNOWN_SOURCE_NAME
            }
            return name
        }

    override fun toString(): String = data.concatToString()

    companion object {
        const val READ_BUFFER_SIZE: Int = 1024
        const val INITIAL_BUFFER_SIZE: Int = 1024
    }
}
