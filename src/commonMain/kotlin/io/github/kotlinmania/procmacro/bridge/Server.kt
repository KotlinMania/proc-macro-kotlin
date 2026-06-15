// port-lint: source src/bridge/server.rs
package io.github.kotlinmania.procmacro.bridge

import io.github.kotlinmania.procmacro.Span

internal interface BridgeServer {
    fun globals(): ExpnGlobals<ClientSpan> =
        ExpnGlobals(
            defSite = ClientSpan(Span.defSite()),
            callSite = ClientSpan(Span.callSite()),
            mixedSite = ClientSpan(Span.mixedSite()),
        )

    fun internSymbol(ident: String): Symbol = Symbol.intern(ident)

    fun withSymbolString(
        symbol: Symbol,
        block: (String) -> Unit,
    ) {
        block(symbol.asString())
    }

    fun dispatch(buffer: RpcBuffer): RpcBuffer = buffer.copy()
}

internal class Dispatcher<S : BridgeServer>(
    private val server: S,
) {
    fun dispatch(buffer: RpcBuffer): RpcBuffer = server.dispatch(buffer)
}

internal interface ExecutionStrategy {
    fun runBridgeAndClient(
        dispatcher: Dispatcher<out BridgeServer>,
        input: RpcBuffer,
        runClient: (BridgeConfig) -> RpcBuffer,
        forceShowPanics: Boolean,
    ): RpcBuffer
}

internal data class MaybeCrossThread(
    val crossThread: Boolean,
) : ExecutionStrategy {
    override fun runBridgeAndClient(
        dispatcher: Dispatcher<out BridgeServer>,
        input: RpcBuffer,
        runClient: (BridgeConfig) -> RpcBuffer,
        forceShowPanics: Boolean,
    ): RpcBuffer =
        runClient(
            BridgeConfig(
                input = input,
                dispatch = BridgeDispatch { dispatcher.dispatch(it) },
                forceShowPanics = forceShowPanics,
            ),
        )
}

internal val SameThread: MaybeCrossThread = MaybeCrossThread(crossThread = false)

internal val CrossThread: MaybeCrossThread = MaybeCrossThread(crossThread = true)
