package app.komori.data

import java.io.IOException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.*
import org.junit.Test

class FailurePolicyTest {
    @Test
    fun parsesDeltaDateExpiredAndOverflowRetryAfter() {
        val now = 1_445_412_480_000L
        assertEquals(now + 120_000, retryAfterDeadline("120", now))
        assertEquals(now + 60_000, retryAfterDeadline("Wed, 21 Oct 2015 07:29:00 GMT", now))
        assertEquals(now, retryAfterDeadline("Wed, 21 Oct 2015 07:27:00 GMT", now))
        assertEquals(Long.MAX_VALUE, retryAfterDeadline("999999999999999999999", now))
        assertNull(retryAfterDeadline("-2", now))
        assertNull(retryAfterDeadline("tomorrow", now))
    }

    @Test
    fun cooldownIsOriginScopedExpiresAndDoesNotShorten() {
        val request = Request.Builder().url("https://cooldown.example:444/page").build()
        val response =
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(429)
                .message("Limited")
                .header("Retry-After", "120")
                .build()
        val error = httpFailure(response, now = 1000)
        assertEquals(121_000L, error.retryAtMillis)
        httpFailure(response.newBuilder().header("Retry-After", "1").build(), now = 1000)
        val deferred =
            assertThrows(SourceHttpException::class.java) {
                SourceCooldowns.check(request, now = 2000)
            }
        assertTrue(deferred.deferred)
        assertEquals(121_000L, deferred.retryAtMillis)
        SourceCooldowns.check(
            request.newBuilder().url("https://cooldown.example/page").build(),
            now = 2000,
        )
        SourceCooldowns.check(request, now = 121_000)
    }

    @Test
    fun permanentFailuresDoNotAutomaticallyRetry() {
        for (error in
            listOf(
                SourceHttpException(404, "source"),
                SourceHttpException(410, "source"),
                SourceHttpException(400, "source"),
                SourceVerificationRequired("source"),
                StorageFailure("Disk full"),
                ContentFailure("Invalid image"),
                SSLHandshakeException("TLS"),
            )) {
            assertFalse(error.toString(), automaticRetryAllowed(error))
        }
        assertEquals(FailureKind.OFFLINE, failureKind(UnknownHostException()))
        assertEquals(FailureKind.MISSING_PAGE, failureKind(SourceHttpException(404, "source")))
        assertTrue(automaticRetryAllowed(UnknownHostException()))
        assertTrue(automaticRetryAllowed(SourceHttpException(503, "source")))
    }

    @Test
    fun sessionBudgetSurvivesRepeatedChecksAndManualResetReopensIt() {
        val budget = PageRetryBudget()
        repeat(3) {
            budget.check("page")
            assertFalse(budget.failed("page", IOException("Reset")) is PageRetryLimit)
        }
        assertTrue(budget.failed("page", IOException("Reset")) is PageRetryLimit)
        repeat(5) { assertThrows(PageRetryLimit::class.java) { budget.check("page") } }
        budget.check("other-page")
        budget.reset("page")
        budget.check("page")
        budget.failed("page", SourceHttpException(404, "source"))
        assertThrows(SourceHttpException::class.java) { budget.check("page") }
        budget.clear()
        budget.check("page")
    }

    @Test
    fun deferredCooldownDoesNotConsumeNetworkAttempts() {
        val budget = PageRetryBudget(1)
        repeat(10) {
            budget.failed("page", SourceHttpException(429, "source", 1000, deferred = true))
        }
        budget.check("page")
        assertTrue(budget.failed("page", IOException("Reset")) is PageRetryLimit)
    }
}
