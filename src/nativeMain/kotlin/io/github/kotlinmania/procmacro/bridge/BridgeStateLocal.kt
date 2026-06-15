// port-lint: source src/bridge/client.rs
package io.github.kotlinmania.procmacro.bridge

import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
private object NativeBridgeStateStack {
    val stack: MutableList<BridgeState> = mutableListOf()
}

internal actual class BridgeStateLocal actual constructor() {
    actual fun stack(): MutableList<BridgeState> = NativeBridgeStateStack.stack
}
