// port-lint: source src/lib.rs
package io.github.kotlinmania.procmacro

/**
 * Determines whether proc_macro has been made accessible to the currently
 * running program.
 *
 * In upstream Rust, the proc_macro crate is only intended for use inside
 * the implementation of procedural macros. All the functions in the
 * upstream crate panic if invoked from outside of a procedural macro, such
 * as from a build script or unit test or ordinary Rust binary.
 *
 * For the Kotlin port the meaning is adapted: this library is available
 * wherever it has been linked, because the backing tokenizer is the
 * JetBrains multiplatform Kotlin lexer, not an in-process compiler bridge.
 * The current implementation therefore always returns `true`. A future
 * phase may refine the contract (e.g. return `false` on a target where
 * the lexer backend has not been wired in).
 */
public fun isAvailable(): Boolean = true
