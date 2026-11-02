// port-lint: source src/bridge/closure.rs
package io.github.kotlinmania.procmacro.bridge

internal open class Env

internal fun interface ClosureCallCallback {
    fun invoke(
        env: Env?,
        arg: Buffer,
    ): Buffer
}

/**
 * Callable bridge closure wrapping a mutable environment and [Buffer]-to-[Buffer]
 * dispatch entry point.
 */
internal class Closure(
    val callCallback: ClosureCallCallback,
    val env: Env?,
) {
    fun call(arg: Buffer): Buffer = callCallback.invoke(env, arg)

    companion object {
        private class LambdaEnv(
            val handler: (Buffer) -> Buffer,
        ) : Env()

        private val lambdaDispatcher =
            ClosureCallCallback { env, arg ->
                val typed = checkNotNull(env as? LambdaEnv) { "Closure environment missing LambdaEnv" }
                typed.handler(arg)
            }

        fun from(f: (Buffer) -> Buffer): Closure =
            Closure(
                callCallback = lambdaDispatcher,
                env = LambdaEnv(f),
            )
    }
}
