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
import java.io.IOException

// A class to read plain text interpreter data produced by ANTLR.
object InterpreterDataReader {
    fun parseFile(fileName: String?): InterpreterData {
        val result = InterpreterData()
        result.ruleNames = mutableListOf<String>()

        try {
            BufferedReader(FileReader(fileName)).use { br ->
                var line: String
                val literalNames = mutableListOf<String>()
                val symbolicNames = mutableListOf<String>()

                line = br.readLine() ?: throw RuntimeException("Unexpected data entry")
                if (line != "token literal names:") throw RuntimeException("Unexpected data entry")
                while (br.readLine().also { line = it ?: "" } != null) {
                    if (line.isEmpty()) break
                    literalNames.add(if (line == "null") "" else line)
                }

                line = br.readLine() ?: throw RuntimeException("Unexpected data entry")
                if (line != "token symbolic names:") throw RuntimeException("Unexpected data entry")
                while (br.readLine().also { line = it ?: "" } != null) {
                    if (line.isEmpty()) break
                    symbolicNames.add(if (line == "null") "" else line)
                }

                result.vocabulary =
                    VocabularyImpl(
                        literalNames.toTypedArray(),
                        symbolicNames.toTypedArray(),
                    )

                line = br.readLine() ?: throw RuntimeException("Unexpected data entry")
                if (line != "rule names:") throw RuntimeException("Unexpected data entry")
                while (br.readLine().also { line = it ?: "" } != null) {
                    if (line.isEmpty()) break
                    result.ruleNames!!.add(line)
                }

                line = br.readLine() ?: ""
                if (line == "channel names:") { // Additional lexer data.
                    result.channels = mutableListOf<String>()
                    while (br.readLine().also { line = it ?: "" } != null) {
                        if (line.isEmpty()) break
                        result.channels!!.add(line)
                    }

                    line = br.readLine() ?: throw RuntimeException("Unexpected data entry")
                    if (line != "mode names:") throw RuntimeException("Unexpected data entry")
                    result.modes = mutableListOf<String>()
                    while (br.readLine().also { line = it ?: "" } != null) {
                        if (line.isEmpty()) break
                        result.modes!!.add(line)
                    }
                }

                line = br.readLine() ?: throw RuntimeException("Unexpected data entry")
                if (line != "atn:") throw RuntimeException("Unexpected data entry")
                line = br.readLine() ?: throw RuntimeException("Unexpected data entry")
                val elements = line.substring(1, line.length - 1).split(",")
                val serializedATN = IntArray(elements.size)

                for (i in elements.indices) { // ignore [...] on ends
                    serializedATN[i] = elements[i].trim().toInt()
                }

                val deserializer = ATNDeserializer()
                result.atn = deserializer.deserialize(serializedATN)
            }
        } catch (e: IOException) {
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
