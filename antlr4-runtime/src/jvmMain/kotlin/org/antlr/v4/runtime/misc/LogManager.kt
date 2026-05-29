/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.misc

import java.io.BufferedWriter
import java.io.IOException
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date

class LogManager {
    protected class Record {
        var timestamp: Long = 0
        var location: StackTraceElement? = null
        var component: String? = null
        var msg: String? = null

        init {
            timestamp = System.currentTimeMillis()
            location = Throwable().stackTrace.get(0)
        }

        override fun toString(): String {
            val buf: StringBuilder = StringBuilder()
            buf.append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS").format(Date(timestamp)))
            buf.append(" ")
            buf.append(component)
            buf.append(" ")
            buf.append(location?.fileName)
            buf.append(":")
            buf.append(location?.lineNumber)
            buf.append(" ")
            buf.append(msg)
            return buf.toString()
        }
    }

    protected var records: MutableList<Record>? = null

    fun log(component: String?, msg: String?) {
        val r = Record()
        r.component = component
        r.msg = msg
        if (records == null) {
            records = mutableListOf()
        }
        records!!.add(r)
    }

    fun log(msg: String?) {
        log(null, msg)
    }

    @kotlin.Throws(IOException::class)
    fun save(filename: String?) {
        val fw = FileWriter(filename)
        val bw = BufferedWriter(fw)
        try {
            bw.write(toString())
        } finally {
            bw.close()
        }
    }

    @kotlin.Throws(IOException::class)
    fun save(): String {
        val dir = "."
        val defaultFilename =
            dir.toString() + "/antlr-" +
                SimpleDateFormat("yyyy-MM-dd-HH.mm.ss").format(Date()) + ".log"
        save(defaultFilename)
        return defaultFilename
    }

    override fun toString(): String {
        if (records == null) return ""
        val nl: String = System.getProperty("line.separator")
        val buf: StringBuilder = StringBuilder()
        for (r in records!!) {
            buf.append(r)
            buf.append(nl)
        }
        return buf.toString()
    }

    companion object {
        @kotlin.Throws(IOException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            val mgr = LogManager()
            mgr.log("atn", "test msg")
            mgr.log("dfa", "test msg 2")
            println(mgr)
            mgr.save()
        }
    }
}
