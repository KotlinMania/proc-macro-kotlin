package org.antlr.v4.runtime.tree.pattern

class TagChunk(label: String?, tag: String) : Chunk() {
    val tag: String
    val label: String?

    constructor(tag: String) : this(null, tag)

    init {
        require(tag.isNotEmpty()) { "tag cannot be null or empty" }
        this.label = label
        this.tag = tag
    }

    override fun toString(): String {
        if (label != null) return "$label:$tag"
        return tag
    }

}
