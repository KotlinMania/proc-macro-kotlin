package io.github.kotlinmania.procmacro

/**
 * Determines whether procedural macro token facilities are accessible to
 * the currently running program.
 *
 * Returns `true` whenever the multiplatform tokenizer runtime is linked;
 * callers checking whether an active compiler RPC bridge session is entered
 * on the current thread can query `BridgeClientState.isAvailable()`.
 */
public fun isAvailable(): Boolean = true
