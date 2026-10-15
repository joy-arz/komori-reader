package app.komori.data

import kotlinx.coroutines.*
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test

class HttpResponseTest {
    @Test fun cancellationClosesTheCatalogCall() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val call = MangaDex.client().newCall(Request.Builder().url(server.url("/")).build())
            val job = launch { call.readResponse { it.body!!.string() } }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)) }
            job.cancelAndJoin()
            assertTrue(call.isCanceled())
        }
    }

    @Test fun onlyAccessFailuresRequireUserVerification() {
        assertTrue(requiresSourceAction(SourceVerificationRequired("zap")))
        assertTrue(requiresSourceAction(SourceHttpException(403, "nhentai")))
        assertFalse(requiresSourceAction(SourceHttpException(429, "zap")))
        assertFalse(requiresSourceAction(java.net.SocketTimeoutException()))
        assertFalse(requiresSourceAction(null))
    }
}
