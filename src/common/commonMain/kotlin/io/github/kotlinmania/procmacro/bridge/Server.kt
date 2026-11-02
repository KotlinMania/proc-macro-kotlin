// port-lint: source src/bridge/server.rs
package io.github.kotlinmania.procmacro.bridge

internal interface BridgeServer {
    fun globals(): ExpnGlobals<ClientSpan> = defaultClientGlobals()

    fun internSymbol(ident: String): Symbol = Symbol.intern(ident)

    fun withSymbolString(
        symbol: Symbol,
        block: (String) -> Unit,
    ) {
        block(symbol.asString())
    }

    fun injectedEnvVar(variable: String): String? = null

    fun trackEnvVar(
        variable: String,
        value: String?,
    ) {
    }

    fun trackPath(path: String) {
    }

    fun spanSourceText(span: ClientSpan): String? = span.span.sourceText()

    fun tsExpandExpr(stream: ClientTokenStream): Result<ClientTokenStream> = stream.expandExprLocally()

    fun dispatch(buffer: RpcBuffer): RpcBuffer =
        when (val payload = buffer.payload) {
            is BridgePayload.Request.InjectedEnvVar ->
                RpcBuffer(payload = BridgePayload.Response.StringValue(injectedEnvVar(payload.variable)))
            is BridgePayload.Request.TrackEnvVar -> {
                trackEnvVar(payload.variable, payload.value)
                RpcBuffer(payload = BridgePayload.Response.UnitValue)
            }
            is BridgePayload.Request.TrackPath -> {
                trackPath(payload.path)
                RpcBuffer(payload = BridgePayload.Response.UnitValue)
            }
            is BridgePayload.Request.SpanSourceText ->
                RpcBuffer(payload = BridgePayload.Response.StringValue(spanSourceText(payload.span)))
            is BridgePayload.Request.ExpandExpr ->
                RpcBuffer(payload = BridgePayload.Response.TokenStreamResult(tsExpandExpr(payload.stream)))
            else -> buffer.copy()
        }
}

internal class Dispatcher<S : BridgeServer>(
    private val server: S,
) {
    fun dispatch(buffer: RpcBuffer): RpcBuffer = server.dispatch(buffer)

    fun globals(): ExpnGlobals<ClientSpan> = server.globals()
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
    ): RpcBuffer {
        if (crossThread) {
            throw UnsupportedOperationException("CrossThread bridge execution requires a platform-specific runner")
        }
        val config =
            BridgeConfig(
                input = input,
                dispatch = BridgeDispatch { dispatcher.dispatch(it) },
                forceShowPanics = forceShowPanics,
                globals = dispatcher.globals(),
            )
        return BridgeClientState.enter(
            BridgeState(
                globals = config.globals,
                dispatch = config.dispatch,
            ),
        ) {
            runClient(config)
        }
    }
}

internal val SameThread: MaybeCrossThread = MaybeCrossThread(crossThread = false)

internal val CrossThread: MaybeCrossThread = MaybeCrossThread(crossThread = true)
