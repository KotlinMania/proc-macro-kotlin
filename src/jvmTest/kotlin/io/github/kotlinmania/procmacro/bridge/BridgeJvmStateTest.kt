package io.github.kotlinmania.procmacro.bridge

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BridgeJvmStateTest {
    @Test
    fun bridgeStateIsScopedPerJvmThread() {
        val ready = CountDownLatch(2)
        val release = CountDownLatch(1)
        val done = CountDownLatch(2)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())

        val first = stateThread("first", ready, release, done, failures)
        val second = stateThread("second", ready, release, done, failures)
        first.start()
        second.start()

        assertTrue(ready.await(5, TimeUnit.SECONDS), "bridge threads did not both enter state")
        release.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS), "bridge threads did not both finish")

        failures.firstOrNull()?.let { throw AssertionError("thread-local bridge state leaked", it) }
    }

    private fun stateThread(
        marker: String,
        ready: CountDownLatch,
        release: CountDownLatch,
        done: CountDownLatch,
        failures: MutableList<Throwable>,
    ): Thread =
        Thread {
            try {
                val state =
                    BridgeState(
                        globals = defaultClientGlobals(),
                        dispatch = BridgeDispatch { RpcBuffer(payload = BridgePayload.Response.StringValue(marker)) },
                    )
                BridgeClientState.enter(state) {
                    ready.countDown()
                    assertTrue(release.await(5, TimeUnit.SECONDS), "bridge state release timed out")
                    assertEquals(marker, BridgeMethods.injectedEnvVar("ignored"))
                    RpcBuffer()
                }
            } catch (t: Throwable) {
                failures.add(t)
            } finally {
                done.countDown()
            }
        }
}
