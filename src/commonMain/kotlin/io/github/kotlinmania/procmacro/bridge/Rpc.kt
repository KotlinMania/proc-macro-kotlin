// port-lint: source src/bridge/rpc.rs
package io.github.kotlinmania.procmacro.bridge

internal class RpcBuffer(
    private val bytes: MutableList<Byte> = mutableListOf(),
) {
    val size: Int get() = bytes.size

    fun isEmpty(): Boolean = bytes.isEmpty()

    fun clear() {
        bytes.clear()
    }

    fun append(byte: Byte) {
        bytes.add(byte)
    }

    fun appendAll(input: ByteArray) {
        for (byte in input) bytes.add(byte)
    }

    fun toByteArray(): ByteArray = bytes.toByteArray()

    fun copy(): RpcBuffer = RpcBuffer(bytes.toMutableList())
}

internal data class PanicMessage(
    val message: String,
)

internal sealed class Result<out T> {
    data class Ok<T>(
        val value: T,
    ) : Result<T>()

    data class Err(
        val message: String,
    ) : Result<Nothing>()

    fun getOrThrow(): T =
        when (this) {
            is Ok -> value
            is Err -> throw IllegalStateException(message)
        }
}
