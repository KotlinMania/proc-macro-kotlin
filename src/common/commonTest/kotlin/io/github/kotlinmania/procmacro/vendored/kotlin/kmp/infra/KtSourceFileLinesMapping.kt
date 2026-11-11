/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

// Vendored from JetBrains/kotlin compiler/frontend.common/src/org/jetbrains/kotlin/KtSourceFileLinesMapping.kt
package io.github.kotlinmania.procmacro.vendored.kotlin.kmp.infra

data class SourceLineAndColumn(
    val line: Int,
    val column: Int,
)

interface KtSourceFileLinesMapping {
    fun getLineStartOffset(line: Int): Int
    fun getLineAndColumnByOffset(offset: Int): SourceLineAndColumn
    fun getLineByOffset(offset: Int): Int

    val lastOffset: Int
    val linesCount: Int
}

open class KtSourceFileLinesMappingFromLineStartOffsets(
    val lineStartOffsets: IntArray,
    override val lastOffset: Int,
) : KtSourceFileLinesMapping {
    override fun getLineStartOffset(line: Int): Int = lineStartOffsets[line]

    override fun getLineAndColumnByOffset(offset: Int): SourceLineAndColumn {
        val lineNumber = getLineByOffset(offset)
        if (lineNumber < 0) return SourceLineAndColumn(-1, -1)
        val lineStartOffset = lineStartOffsets[lineNumber]
        return SourceLineAndColumn(lineNumber, offset - lineStartOffset)
    }

    override fun getLineByOffset(offset: Int): Int {
        val index = lineStartOffsets.binarySearchInt(offset)
        return if (index >= 0) index else -index - 2
    }

    override val linesCount: Int
        get() = lineStartOffsets.size
}

private fun IntArray.binarySearchInt(target: Int): Int {
    var low = 0
    var high = size - 1
    while (low <= high) {
        val mid = (low + high).ushr(1)
        val midVal = this[mid]
        if (midVal < target) {
            low = mid + 1
        } else if (midVal > target) {
            high = mid - 1
        } else {
            return mid
        }
    }
    return -(low + 1)
}

fun CharSequence.toSourceLinesMapping(): KtSourceFileLinesMapping {
    val lineOffsets = mutableListOf(0)
    var offset = 0
    for (c in this) {
        offset++
        if (c == '\n') lineOffsets.add(offset)
    }
    return KtSourceFileLinesMappingFromLineStartOffsets(lineOffsets.toIntArray(), offset)
}
