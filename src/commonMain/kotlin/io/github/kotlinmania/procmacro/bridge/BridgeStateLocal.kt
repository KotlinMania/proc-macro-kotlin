// port-lint: source src/bridge/client.rs
package io.github.kotlinmania.procmacro.bridge

internal expect class BridgeStateLocal() {
    fun stack(): MutableList<BridgeState>
}
