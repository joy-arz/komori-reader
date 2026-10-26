package app.komori.data

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*

/** Canceling a subscriber does not cancel the request used by other subscribers. */
internal class SharedRequests<T>(private val scope: CoroutineScope) {
    private val pending = ConcurrentHashMap<String, Deferred<T>>()

    suspend fun get(key: String, fetch: suspend () -> T): T {
        val candidate = scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) { fetch() }
        val request = pending.putIfAbsent(key, candidate) ?: candidate
        if (request !== candidate) candidate.cancel()
        else request.invokeOnCompletion { pending.remove(key, request) }
        request.start()
        return request.await()
    }

    fun invalidate(key: String) { pending.remove(key)?.cancel() }
    fun cancel() { pending.values.forEach { it.cancel() }; pending.clear() }
}
