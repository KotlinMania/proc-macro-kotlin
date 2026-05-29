package org.antlr.v4.runtime.tree.pattern

class TextChunk(text: String) : Chunk() {
    val text: String

    init {
        requireNotNull(text) { "text cannot be null" }
        this.text = text
    }

    override fun toString(): String = "'$text'"

}
