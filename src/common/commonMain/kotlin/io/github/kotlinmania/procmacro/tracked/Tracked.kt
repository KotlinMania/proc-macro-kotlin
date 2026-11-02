package io.github.kotlinmania.procmacro.tracked

import io.github.kotlinmania.procmacro.TrackedEnvError
import io.github.kotlinmania.procmacro.TrackedEnvVarOutcome
import io.github.kotlinmania.procmacro.bridge.BridgeMethods

/**
 * Retrieves an environment variable and records it in build dependency info.
 *
 * The build system executing the compiler will know that the variable was
 * accessed during macro expansion and can rerun compilation when its value
 * changes.
 */
public fun envVar(key: String): TrackedEnvVarOutcome {
    val injected = BridgeMethods.injectedEnvVar(key)
    BridgeMethods.trackEnvVar(key, injected)
    return if (injected != null) {
        TrackedEnvVarOutcome.Ok(injected)
    } else {
        TrackedEnvVarOutcome.Err(TrackedEnvError.NOT_PRESENT)
    }
}

/**
 * Tracks a file or directory path explicitly in build dependency info.
 */
public fun path(path: String) {
    BridgeMethods.trackPath(path)
}
