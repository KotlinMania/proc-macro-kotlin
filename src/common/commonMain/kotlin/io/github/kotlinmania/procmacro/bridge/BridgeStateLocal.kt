// port-lint: source src/bridge/client.rs
package io.github.kotlinmania.procmacro.bridge

import io.github.kotlinmania.threadlocal.ThreadLocal

internal class BridgeStateLocal {
    private val stacks: ThreadLocal<BridgeStateStack> = ThreadLocal()

    fun stack(): MutableList<BridgeState> = stacks.getOr { BridgeStateStack() }.states
}

private class BridgeStateStack {
    val states: MutableList<BridgeState> = mutableListOf()
}
