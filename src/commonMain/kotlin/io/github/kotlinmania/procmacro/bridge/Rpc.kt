// port-lint: source src/bridge/rpc.rs
package io.github.kotlinmania.procmacro.bridge

internal class RpcBuffer(
    private val bytes: MutableList<Byte> = mutableListOf(),
    internal val payload: BridgePayload? = null,
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

    fun copy(): RpcBuffer = RpcBuffer(bytes.toMutableList(), payload)
}

internal data class PanicMessage(
    val message: String,
)

internal sealed class BridgePayload {
    sealed class Request : BridgePayload() {
        data class InjectedEnvVar(
            val variable: String,
        ) : Request()

        data class TrackEnvVar(
            val variable: String,
            val value: String?,
        ) : Request()

        data class TrackPath(
            val path: String,
        ) : Request()

        data class SpanSourceText(
            val span: ClientSpan,
        ) : Request()
    }

    sealed class Response : BridgePayload() {
        data class StringValue(
            val value: String?,
        ) : Response()

        data object UnitValue : Response()
    }
}

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
