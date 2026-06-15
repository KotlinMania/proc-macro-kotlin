// port-lint: source src/bridge/client.rs
package io.github.kotlinmania.procmacro.bridge

private val webBridgeStateStack: MutableList<BridgeState> = mutableListOf()

internal actual class BridgeStateLocal actual constructor() {
    actual fun stack(): MutableList<BridgeState> = webBridgeStateStack
}
