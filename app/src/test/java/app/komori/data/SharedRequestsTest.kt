package app.komori.data

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class SharedRequestsTest {
    @Test fun concurrentWaitersShareOneRequest() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val requests = SharedRequests<Int>(owner)
        val calls = AtomicInteger()
        val started = CompletableDeferred<Unit>(); val complete = CompletableDeferred<Unit>()
        try {
            val waiters = (1..12).map { async { requests.get("chapter") { calls.incrementAndGet(); started.complete(Unit); complete.await(); 42 } } }
            withTimeout(2000) { started.await() }; delay(50); complete.complete(Unit)
            assertEquals(List(12) { 42 }, waiters.awaitAll()); assertEquals(1, calls.get())
        } finally { owner.cancel() }
    }

    @Test fun canceledSubscriberDoesNotCancelSharedFetchAndFailureCanRetry() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val requests = SharedRequests<Int>(owner)
        val started = CompletableDeferred<Unit>(); val complete = CompletableDeferred<Unit>()
        try {
            val first = async { requests.get("chapter") { started.complete(Unit); complete.await(); 42 } }
            started.await()
            val second = async { requests.get("chapter") { error("Must join the first request") } }
            delay(30); first.cancelAndJoin(); complete.complete(Unit)
            assertEquals(42, second.await())
            try { requests.get("failure") { throw IOException("fixture") }; fail() } catch (_: IOException) { }
            assertEquals(7, requests.get("failure") { 7 })
        } finally { owner.cancel() }
    }
}
