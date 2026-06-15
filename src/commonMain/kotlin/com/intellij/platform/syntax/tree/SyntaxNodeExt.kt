// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.tree

import com.intellij.platform.syntax.SyntaxElementType

internal val SyntaxNode.length: Int get() = endOffset - startOffset

internal fun SyntaxNode.children(): Sequence<SyntaxNode> =
    generateSequence({ firstChild() }) { child -> child.nextSibling() }

internal fun SyntaxNode.childrenBackward(): Sequence<SyntaxNode> =
    generateSequence({ lastChild() }) { child -> child.prevSibling() }

internal tailrec fun SyntaxNode.skip(): SyntaxNode? = nextSibling() ?: parent()?.skip()

internal fun SyntaxNode.next(): SyntaxNode? = firstChild() ?: skip()

private tailrec fun SyntaxNode.findFirstSiblingForward(cond: (SyntaxNode) -> Boolean): SyntaxNode? =
    when {
        cond(this) -> this
        else -> nextSibling()?.findFirstSiblingForward(cond)
    }

private tailrec fun SyntaxNode.findFirstSiblingBackward(cond: (SyntaxNode) -> Boolean): SyntaxNode? =
    when {
        cond(this) -> this
        else -> prevSibling()?.findFirstSiblingBackward(cond)
    }

internal fun SyntaxNode.findFirstChild(cond: (SyntaxNode) -> Boolean): SyntaxNode? =
    firstChild()?.findFirstSiblingForward(cond)

internal fun SyntaxNode.findFirstChild(type: SyntaxElementType): SyntaxNode? = findFirstChild { it.type == type }

internal fun SyntaxNode.findLastChild(cond: (SyntaxNode) -> Boolean): SyntaxNode? =
    lastChild()?.findFirstSiblingBackward(cond)

internal fun SyntaxNode.findLastChild(type: SyntaxElementType): SyntaxNode? = findLastChild { it.type == type }

internal fun SyntaxNode.pp(indent: String = ""): String =
    indent + type + (if (firstChild() == null) ": (${text.toString().replace("\n", "\\n")})" else "") + "\n" +
        generateSequence(
            firstChild(),
        ) { it.nextSibling() }.map { it.pp("$indent  ") }.joinToString("")

internal fun SyntaxNode.descendants(filtering: (SyntaxNode) -> Boolean = { true }): Sequence<SyntaxNode> =
    sequenceOf(this) +
        if (filtering(this)) children().flatMap { treeWalker -> treeWalker.descendants(filtering) } else emptySequence()

internal fun SyntaxNode.descendantsReversed(filtering: (SyntaxNode) -> Boolean = { true }): Sequence<SyntaxNode> =
    if (filtering(this)) {
        generateSequence(lastChild()) { it.prevSibling() }.flatMap { treeWalker ->
            treeWalker.descendantsReversed(filtering)
        }
    } else {
        emptySequence()
    } + sequenceOf(this)

internal fun SyntaxNode.ancestors(excludeSelf: Boolean = false): Sequence<SyntaxNode> =
    generateSequence(if (excludeSelf) parent() else this) { w -> w.parent() }

internal fun SyntaxNode.siblings(
    forward: Boolean = true,
    excludeSelf: Boolean = false,
): Sequence<SyntaxNode> =
    generateSequence(
        if (excludeSelf) {
            if (forward) {
                nextSibling()
            } else {
                prevSibling()
            }
        } else {
            this
        },
    ) { w ->
        if (forward) w.nextSibling() else w.prevSibling()
    }

private fun SyntaxNode.skipRight(): SyntaxNode? = skip()

private tailrec fun SyntaxNode.skipLeft(): SyntaxNode? = prevSibling() ?: parent()?.skipLeft()

internal fun SyntaxNode.firstLeaf(): SyntaxNode? {
    tailrec fun SyntaxNode.loop(): SyntaxNode =
        when (val c = firstChild()) {
            null -> this
            else -> c.loop()
        }
    return firstChild()?.loop()
}

internal fun SyntaxNode.leafLeft(): SyntaxNode? {
    tailrec fun SyntaxNode.loop(): SyntaxNode? =
        when (val lc = lastChild()) {
            null -> this
            else -> lc.loop()
        }
    return this.skipLeft()?.loop()
}

internal fun SyntaxNode.sequenceLeft(excludeSelf: Boolean = false): Sequence<SyntaxNode> =
    generateSequence(if (excludeSelf) leafLeft() else this) { it.leafLeft() }

internal fun SyntaxNode.leafRight(): SyntaxNode? = generateSequence(this.skipRight()) { it.firstChild() }.lastOrNull()

internal fun SyntaxNode.sequenceRight(excludeSelf: Boolean = false): Sequence<SyntaxNode> =
    generateSequence(if (excludeSelf) leafRight() else this) { it.leafRight() }

internal fun SyntaxNode.lastLeaf(): SyntaxNode? {
    tailrec fun SyntaxNode.loop(): SyntaxNode? =
        when (val lc = lastChild()) {
            null -> this
            else -> lc.loop()
        }
    return lastChild()?.loop()
}

internal fun SyntaxNode.leafByOffset(offset: Int): SyntaxNode? =
    if (offset >= startOffset && offset < endOffset) {
        descendantsByOffset(offset).last()
    } else {
        null
    }

internal fun SyntaxNode.descendantsByOffset(offset: Int): Sequence<SyntaxNode> =
    generateSequence(this) { it.childByOffset(offset) }

internal tailrec fun SyntaxNode.ancestorWithType(type: SyntaxElementType): SyntaxNode? =
    when {
        this.type == type -> this
        else -> parent()?.ancestorWithType(type)
    }

internal fun SyntaxNode.skipForward(
    type: SyntaxElementType,
    excludeSelf: Boolean = false,
): SyntaxNode? =
    sequenceRight(excludeSelf)
        .filterNot {
            type == it.type
        }.firstOrNull()
