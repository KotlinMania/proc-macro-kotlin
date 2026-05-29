/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.misc

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object Utils {
    fun <T> join(
        iter: Iterator<T?>,
        separator: String?,
    ): String {
        val buf: StringBuilder = StringBuilder()
        while (iter.hasNext()) {
            buf.append(iter.next())
            if (iter.hasNext()) {
                buf.append(separator)
            }
        }
        return buf.toString()
    }

    fun <T> join(
        array: Array<T?>,
        separator: String?,
    ): String {
        val builder: StringBuilder = StringBuilder()
        for (i in array.indices) {
            builder.append(array[i])
            if (i < array.size - 1) {
                builder.append(separator)
            }
        }
        return builder.toString()
    }

    fun numNonnull(data: Array<Any?>?): Int {
        if (data == null) return 0
        var n = 0
        for (o in data) {
            if (o != null) n++
        }
        return n
    }

    fun <T> removeAllElements(
        data: MutableCollection<T>,
        value: T,
    ) {
        while (data.contains(value)) data.remove(value)
    }

    fun escapeWhitespace(
        s: String,
        escapeSpaces: Boolean,
    ): String {
        val buf: StringBuilder = StringBuilder()
        for (c in s.toCharArray()) {
            if (c == ' ' && escapeSpaces) {
                buf.append('\u00B7')
            } else if (c == '\t') {
                buf.append("\\t")
            } else if (c == '\n') {
                buf.append("\\n")
            } else if (c == '\r') {
                buf.append("\\r")
            } else {
                buf.append(c)
            }
        }
        return buf.toString()
    }

    @Throws(IOException::class)
    fun writeFile(
        fileName: String?,
        content: String?,
    ) {
        writeFile(fileName, content, null)
    }

    @Throws(IOException::class)
    fun writeFile(
        fileName: String?,
        content: String?,
        encoding: String?,
    ) {
        val f: File = File(fileName)
        val fos: FileOutputStream = FileOutputStream(f)
        val osw: OutputStreamWriter
        if (encoding != null) {
            osw = OutputStreamWriter(fos, encoding)
        } else {
            osw = OutputStreamWriter(fos)
        }

        try {
            osw.write(content)
        } finally {
            osw.close()
        }
    }

    @Throws(IOException::class)
    fun readFile(fileName: String?): CharArray? = readFile(fileName, null)

    @Throws(IOException::class)
    fun readFile(
        fileName: String?,
        encoding: String?,
    ): CharArray? {
        val f: File = File(fileName!!)
        val size = f.length().toInt()
        val isr: InputStreamReader
        val fis: FileInputStream = FileInputStream(fileName)
        if (encoding != null) {
            isr = InputStreamReader(fis, encoding)
        } else {
            isr = InputStreamReader(fis)
        }
        var data: CharArray? = null
        try {
            data = CharArray(size)
            val n: Int = isr.read(data)
            if (n < data.size) {
                data = data.copyOf(n)
            }
        } finally {
            isr.close()
        }
        return data
    }

    fun toMap(keys: Array<String>): Map<String, Int> {
        val m: MutableMap<String, Int> = HashMap()
        for (i in keys.indices) {
            m[keys[i]] = i
        }
        return m
    }

    fun toCharArray(data: IntList?): CharArray? {
        if (data == null) return null
        return data.toCharArray()
    }

    fun toSet(bits: BitSet): IntervalSet {
        val s: IntervalSet = IntervalSet()
        var i: Int = bits.nextSetBit(0)
        while (i >= 0) {
            s.add(i)
            i = bits.nextSetBit(i + 1)
        }
        return s
    }

    fun expandTabs(
        s: String?,
        tabSize: Int,
    ): String? {
        if (s == null) return null
        val buf: StringBuilder = StringBuilder()
        var col = 0
        for (i in 0 until s.length) {
            val c: Char = s[i]
            when (c) {
                '\n' -> {
                    col = 0
                    buf.append(c)
                }

                '\t' -> {
                    val n = tabSize - col % tabSize
                    col += n
                    buf.append(spaces(n))
                }

                else -> {
                    col++
                    buf.append(c)
                }
            }
        }
        return buf.toString()
    }

    fun spaces(n: Int): String = sequence(n, " ")

    fun newlines(n: Int): String = sequence(n, "\n")

    fun sequence(
        n: Int,
        s: String?,
    ): String {
        val buf: StringBuilder = StringBuilder()
        for (i in 1..n) buf.append(s)
        return buf.toString()
    }

    fun count(
        s: String,
        x: Char,
    ): Int {
        var n = 0
        for (i in 0 until s.length) {
            if (s[i] == x) {
                n++
            }
        }
        return n
    }
}
