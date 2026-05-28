/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.misc

import org.antlr.v4.runtime.misc.BitSet
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Arrays
import java.util.BitSet

object Utils {
    // Seriously: why isn't this built in to java? ugh!
    fun <T> join(iter: Iterator<T?>, separator: String?): String {
        val buf: StringBuilder = StringBuilder()
        while (iter.hasNext()) {
            buf.append(iter.next())
            if (iter.hasNext()) {
                buf.append(separator)
            }
        }
        return buf.toString()
    }

    fun <T> join(array: Array<T?>, separator: String?): String {
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
        var n = 0
        if (data == null) return n
        for (o in data) {
            if (o != null) n++
        }
        return n
    }

    fun <T> removeAllElements(data: Collection<T?>?, value: T?) {
        if (data == null) return
        while (data.contains(value)) data.remove(value)
    }

    fun escapeWhitespace(s: String, escapeSpaces: Boolean): String {
        val buf: StringBuilder = StringBuilder()
        for (c in s.toCharArray()) {
            if (c == ' ' && escapeSpaces) buf.append('\u00B7')
            else if (c == '\t') buf.append("\\t")
            else if (c == '\n') buf.append("\\n")
            else if (c == '\r') buf.append("\\r")
            else buf.append(c)
        }
        return buf.toString()
    }

    @kotlin.Throws(IOException::class)
    fun writeFile(fileName: String?, content: String?) {
        org.antlr.v4.runtime.misc.Utils.writeFile(fileName, content, null)
    }

    @kotlin.Throws(IOException::class)
    fun writeFile(fileName: String?, content: String?, encoding: String?) {
        val f: File = File(fileName)
        val fos: FileOutputStream = FileOutputStream(f)
        val osw: OutputStreamWriter?
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


    @kotlin.Throws(IOException::class)
    fun readFile(fileName: String?): CharArray? {
        return org.antlr.v4.runtime.misc.Utils.readFile(fileName, null)
    }


    @kotlin.Throws(IOException::class)
    fun readFile(fileName: String?, encoding: String?): CharArray? {
        val f: File = File(fileName)
        val size = f.length() as Int
        val isr: InputStreamReader?
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

    /** Convert array of strings to stringindex map. Useful for
     * converting rulenames to nameruleindex map.
     */
    fun toMap(keys: Array<String?>): Map<String?, Integer?> {
        val m: Map<String?, Integer?> = HashMap<String?, Integer?>()
        for (i in keys.indices) {
            m.put(keys[i], i)
        }
        return m
    }

    fun toCharArray(data: IntegerList?): CharArray? {
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

    /** @since 4.6
     */
    fun expandTabs(s: String?, tabSize: Int): String? {
        if (s == null) return null
        val buf: StringBuilder = StringBuilder()
        var col = 0
        for (i in 0..<s.length()) {
            val c: Char = s.charAt(i)
            when (c) {
                '\n' -> {
                    col = 0
                    buf.append(c)
                }

                '\t' -> {
                    val n = tabSize - col % tabSize
                    col += n
                    buf.append(org.antlr.v4.runtime.misc.Utils.spaces(n))
                }

                else -> {
                    col++
                    buf.append(c)
                }
            }
        }
        return buf.toString()
    }

    /** @since 4.6
     */
    fun spaces(n: Int): String {
        return org.antlr.v4.runtime.misc.Utils.sequence(n, " ")
    }

    /** @since 4.6
     */
    fun newlines(n: Int): String {
        return org.antlr.v4.runtime.misc.Utils.sequence(n, "\n")
    }

    /** @since 4.6
     */
    fun sequence(n: Int, s: String?): String {
        val buf: StringBuilder = StringBuilder()
        for (sp in 1..n) buf.append(s)
        return buf.toString()
    }

    /** @since 4.6
     */
    fun count(s: String, x: Char): Int {
        var n = 0
        for (i in 0..<s.length()) {
            if (s.charAt(i) === x) {
                n++
            }
        }
        return n
    }
}
