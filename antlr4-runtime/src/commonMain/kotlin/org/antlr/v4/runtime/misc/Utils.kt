package org.antlr.v4.runtime.misc

object Utils {
    fun <T> join(iter: Iterator<T?>, separator: String?): String {
        val buf = StringBuilder()
        while (iter.hasNext()) {
            buf.append(iter.next())
            if (iter.hasNext()) buf.append(separator)
        }
        return buf.toString()
    }

    fun <T> join(array: Array<T?>, separator: String?): String {
        val builder = StringBuilder()
        for (i in array.indices) {
            builder.append(array[i])
            if (i < array.size - 1) builder.append(separator)
        }
        return builder.toString()
    }

    fun numNonnull(data: Array<Any?>?): Int {
        var n = 0
        if (data == null) return n
        for (o in data) if (o != null) n++
        return n
    }

    fun <T> removeAllElements(data: MutableCollection<T?>?, value: T?) {
        if (data == null) return
        while (data.contains(value)) data.remove(value)
    }

    fun escapeWhitespace(s: String, escapeSpaces: Boolean): String {
        val buf = StringBuilder()
        for (c in s) {
            when {
                c == ' ' && escapeSpaces -> buf.append('\u00B7')
                c == '\t' -> buf.append("\\t")
                c == '\n' -> buf.append("\\n")
                c == '\r' -> buf.append("\\r")
                else -> buf.append(c)
            }
        }
        return buf.toString()
    }

    fun toSet(bits: BitSet): IntervalSet {
        val s = IntervalSet()
        var i = bits.nextSetBit(0)
        while (i >= 0) {
            s.add(i)
            i = bits.nextSetBit(i + 1)
        }
        return s
    }

    fun expandTabs(s: String?, tabSize: Int): String? {
        if (s == null) return null
        val buf = StringBuilder()
        var col = 0
        for (c in s) {
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

    fun sequence(n: Int, s: String?): String {
        val buf = StringBuilder()
        repeat(n) { buf.append(s) }
        return buf.toString()
    }

    fun count(s: String, x: Char): Int = s.count { it == x }
}
