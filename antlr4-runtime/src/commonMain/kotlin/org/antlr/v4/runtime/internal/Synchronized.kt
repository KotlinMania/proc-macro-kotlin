package org.antlr.v4.runtime.internal

/**
 * No-op synchronization for common targets. On JVM, actual implementations
 * use intrinsic monitor synchronization. Other Kotlin targets are either
 * single-threaded (JS/Wasm) or handle concurrency differently (Native).
 */
inline fun <T> synchronized(
    lock: Any,
    block: () -> T,
): T = block()
