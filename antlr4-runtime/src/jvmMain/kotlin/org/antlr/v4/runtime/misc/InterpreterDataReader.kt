/*
 * Copyright (c) 2012-2017 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.misc

import org.antlr.v4.runtime.Vocabulary
import org.antlr.v4.runtime.VocabularyImpl
import org.antlr.v4.runtime.atn.ATN
import org.antlr.v4.runtime.atn.ATNDeserializer
import java.io.BufferedReader
import java.io.FileReader

// A class to read plain text interpreter data produced by ANTLR.
object InterpreterDataReader {
    /**
     * The structure of the data file is very simple. Everything is line based with empty lines
     * separating the different parts. For lexers the layout is:
     * token literal names:
     * ...
     *
     * token symbolic names:
     * ...
     *
     * rule names:
     * ...
     *
     * channel names:
     * ...
     *
     * mode names:
     * ...
     *
     * atn:
     * <a single line with comma separated int values> enclosed in a pair of squared brackets.
     *
     * Data for a parser does not contain channel and mode names.
    </a> */
    fun parseFile(fileName: String?): InterpreterData {
        val result: InterpreterData = org.antlr.v4.runtime.misc.InterpreterDataReader.InterpreterData()
        result.ruleNames = ArrayList<String?>()

        try {
            BufferedReader(FileReader(fileName)).use { br ->
                var line: String
                val literalNames: MutableList<String> = ArrayList()()
                val symbolicNames: MutableList<String> = ArrayList()()

                line = br.readLine()
                if (!line.equals("token literal names:")) throw RuntimeException("Unexpected data entry")
                while ((br.readLine().also { line = it }) != null) {
                    if (line.isEmpty()) break
                    literalNames.add(if (line.equals("null")) "" else line)
                }

                line = br.readLine()
                if (!line.equals("token symbolic names:")) throw RuntimeException("Unexpected data entry")
                while ((br.readLine().also { line = it }) != null) {
                    if (line.isEmpty()) break
                    symbolicNames.add(if (line.equals("null")) "" else line)
                }

                result.vocabulary = VocabularyImpl(
                    literalNames.toArray(arrayOfNulls<String>(0)), symbolicNames.toArray(
                        arrayOfNulls<String>(0)
                    )
                )

                line = br.readLine()
                if (!line.equals("rule names:")) throw RuntimeException("Unexpected data entry")
                while ((br.readLine().also { line = it }) != null) {
                    if (line.isEmpty()) break
                    result.ruleNames.add(line)
                }

                line = br.readLine()
                if (line.equals("channel names:")) { // Additional lexer data.
                    result.channels = ArrayList<String?>()
                    while ((br.readLine().also { line = it }) != null) {
                        if (line.isEmpty()) break
                        result.channels.add(line)
                    }

                    line = br.readLine()
                    if (!line.equals("mode names:")) throw RuntimeException("Unexpected data entry")
                    result.modes = ArrayList<String?>()
                    while ((br.readLine().also { line = it }) != null) {
                        if (line.isEmpty()) break
                        result.modes.add(line)
                    }
                }

                line = br.readLine()
                if (!line.equals("atn:")) throw RuntimeException("Unexpected data entry")
                line = br.readLine()
                val elements: Array<String?> = line.substring(1, line.length() - 1).split(",")
                val serializedATN = IntArray(elements.size)

                for (i in elements.indices) { // ignore [...] on ends
                    serializedATN[i] = String.toInt(elements[i].trim())
                }

                val deserializer: ATNDeserializer = ATNDeserializer()
                result.atn = deserializer.deserialize(serializedATN)
            }
        } catch (e: java.io.IOException) {
            // We just swallow the error and return empty objects instead.
        }

        return result
    }

    class InterpreterData {
        var atn: ATN? = null
        var vocabulary: Vocabulary? = null
        var ruleNames: MutableList<String>? = null
        var channels: MutableList<String>? = null // Only valid for lexer grammars.
        var modes: MutableList<String>? = null // ditto
    }
}
