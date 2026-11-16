// port-lint: source src/bridge/selfless_reify.rs
package io.github.kotlinmania.procmacro.bridge

/**
 * Reifies a stateless client entry-point closure into a callable bridge
 * function reference.
 */
internal fun <A, R> reifyToExternCFnHrtBridge(f: (A) -> R): (A) -> R = { bridge -> f(bridge) }
