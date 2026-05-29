package org.antlr.v4.runtime.tree.pattern

import org.antlr.v4.runtime.misc.MultiMap
import org.antlr.v4.runtime.tree.ParseTree

class ParseTreeMatch(
    tree: ParseTree,
    pattern: ParseTreePattern,
    labels: MultiMap<String, ParseTree>,
    mismatchedNode: ParseTree?,
) {
    private val tree: ParseTree
    private val pattern: ParseTreePattern
    private val labels: MultiMap<String, ParseTree>
    private val mismatchedNode: ParseTree?

    init {
        requireNotNull(tree) { "tree cannot be null" }
        requireNotNull(pattern) { "pattern cannot be null" }
        requireNotNull(labels) { "labels cannot be null" }
        this.tree = tree
        this.pattern = pattern
        this.labels = labels
        this.mismatchedNode = mismatchedNode
    }

    fun get(label: String): ParseTree? {
        val parseTrees = labels.get(label)
        if (parseTrees == null || parseTrees.size == 0) {
            return null
        }
        return parseTrees[parseTrees.size - 1]
    }

    fun getAll(label: String): List<ParseTree> {
        val nodes = labels.get(label)
        return nodes ?: emptyList()
    }

    fun getLabels(): MultiMap<String, ParseTree> = labels

    fun getMismatchedNode(): ParseTree? = mismatchedNode

    fun succeeded(): Boolean = mismatchedNode == null

    fun getPattern(): ParseTreePattern = pattern

    fun getTree(): ParseTree = tree

    override fun toString(): String {
        val status = if (succeeded()) "succeeded" else "failed"
        return "Match $status; found ${getLabels().data.size} labels"
    }
}
