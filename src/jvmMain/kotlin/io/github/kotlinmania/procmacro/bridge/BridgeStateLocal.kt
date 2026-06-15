// port-lint: source src/bridge/client.rs
package io.github.kotlinmania.procmacro.bridge

private val bridgeStateStack = ThreadLocal<MutableList<BridgeState>>()

internal actual class BridgeStateLocal actual constructor() {
    actual fun stack(): MutableList<BridgeState> {
        var stack = bridgeStateStack.get()
        if (stack == null) {
            stack = mutableListOf()
            bridgeStateStack.set(stack)
        }
        return stack
    }
}
