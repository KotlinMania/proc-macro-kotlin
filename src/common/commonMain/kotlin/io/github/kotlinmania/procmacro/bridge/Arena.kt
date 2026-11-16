// port-lint: source src/bridge/arena.rs
package io.github.kotlinmania.procmacro.bridge

import kotlin.math.max
import kotlin.math.min

internal const val PAGE: Int = 4096
internal const val HUGE_PAGE: Int = 2 * 1024 * 1024

/**
 * Contiguous slice within an [Arena] byte chunk.
 */
internal class ArenaSlice(
    val storage: ByteArray,
    val offset: Int,
    val length: Int,
) {
    fun writeCopyOfSlice(source: ByteArray) {
        require(source.size == length) { "source length ${source.size} does not match slice length $length" }
        source.copyInto(storage, destinationOffset = offset, startIndex = 0, endIndex = length)
    }

    fun toByteArray(): ByteArray = storage.copyOfRange(offset, offset + length)

    fun decodeUtf8(): String = storage.decodeToString(offset, offset + length)
}

/**
 * Minimal dropless byte-slice arena allocator used by the client-side
 * symbol interner.
 *
 * Chunks start at [PAGE] bytes and double on each growth step until
 * reaching [HUGE_PAGE] bytes.
 */
internal class Arena private constructor() {
    private var activeChunk: ByteArray = ByteArray(0)
    private var startOffset: Int = 0
    private var endOffset: Int = 0
    private val chunks: MutableList<ByteArray> = mutableListOf()

    val chunkCount: Int
        get() = chunks.size

    fun grow(additional: Int) {
        val lastChunk = chunks.lastOrNull()
        val doubled =
            if (lastChunk == null) {
                PAGE
            } else {
                min(lastChunk.size, HUGE_PAGE / 2) * 2
            }
        val newCap = max(additional, doubled)
        val chunk = ByteArray(newCap)
        chunks.add(chunk)
        activeChunk = chunk
        startOffset = 0
        endOffset = chunk.size
    }

    fun allocRawWithoutGrow(bytes: Int): ArenaSlice? {
        require(bytes >= 0) { "cannot allocate negative byte count: $bytes" }
        val newEnd = endOffset - bytes
        return if (startOffset <= newEnd && chunks.isNotEmpty()) {
            endOffset = newEnd
            ArenaSlice(storage = activeChunk, offset = newEnd, length = bytes)
        } else {
            null
        }
    }

    fun allocRaw(bytes: Int): ArenaSlice {
        if (bytes == 0) {
            return emptySlice
        }
        allocRawWithoutGrow(bytes)?.let { return it }
        grow(bytes)
        return checkNotNull(allocRawWithoutGrow(bytes)) {
            "arena growth failed to satisfy allocation of $bytes bytes"
        }
    }

    fun allocStr(string: String): String {
        val encoded = string.encodeToByteArray()
        val slice = allocRaw(encoded.size)
        slice.writeCopyOfSlice(encoded)
        return slice.decodeUtf8()
    }

    companion object {
        private val emptySlice = ArenaSlice(ByteArray(0), 0, 0)

        fun new(): Arena = Arena()
    }
}
