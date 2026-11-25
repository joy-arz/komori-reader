package app.komori.data

import java.io.IOException
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SourceDiagnosticsTest {
    @Test fun stagesRemainActionableWithoutExposingUrlsOrSecrets() = runBlocking {
        for (stage in SourceStage.entries) {
            val error = try { sourceRequest("nhentai", stage) { throw SocketException("reset https://private.invalid/history?token=secret Cookie: token") } } catch (error: SourceRequestFailure) { error }
            assertTrue(connectionMessage(error).contains(stage.label))
            assertFalse(error.message.contains("secret")); assertFalse(error.diagnostic.contains("secret"))
            assertFalse(error.message.contains("private.invalid")); assertEquals(FailureKind.TRANSIENT, failureKind(error))
            assertTrue(automaticRetryAllowed(error))
        }
    }
    @Test fun wrappedHttpCooldownVerificationStorageAndTlsKeepTheirPolicy() {
        val until = System.currentTimeMillis() + 120000
        val throttled = SourceRequestFailure("zap", SourceStage.IMAGE, SourceHttpException(429, "zap", until, deferred = true))
        assertEquals(until, retryAtMillis(throttled)); assertEquals(FailureKind.RATE_LIMIT, failureKind(throttled))
        assertSame(throttled, PageRetryBudget(1).failed("page", throttled))
        val verify = SourceRequestFailure("zap", SourceStage.CATALOG, SourceHttpException(403, "zap"))
        assertTrue(requiresSourceAction(verify)); assertFalse(automaticRetryAllowed(verify))
        val tls = SourceRequestFailure("nhentai", SourceStage.MANIFEST, SSLHandshakeException("private"))
        assertEquals(FailureKind.SECURE_CONNECTION, failureKind(tls)); assertFalse(automaticRetryAllowed(tls))
        val missing = SourceRequestFailure("nhentai", SourceStage.DETAILS, SourceHttpException(404, "nhentai"))
        assertFalse(automaticRetryAllowed(missing)); assertTrue(missing.message.contains("unavailable"))
        val offline = SourceRequestFailure("mangadex", SourceStage.CHAPTERS, UnknownHostException("secret"))
        assertEquals(FailureKind.OFFLINE, failureKind(offline))
    }
    @Test fun originCertificateFailuresExplainTheOutageAndStayRetryable() {
        val image = SourceRequestFailure(
            "manhwa18", SourceStage.IMAGE,
            SourceHttpException(526, "source", image = true, host = "cdn.manhwa18.com"),
        )
        assertTrue(image.message.contains("origin certificate"))
        assertTrue(image.message.contains("source-side outage"))
        assertTrue(image.message.contains("cdn.manhwa18.com"))
        assertEquals(FailureKind.HTTP, failureKind(image))
        assertTrue(automaticRetryAllowed(image))
        assertTrue(image.diagnostic.contains("status=526"))
        assertTrue(image.diagnostic.contains("host=cdn.manhwa18.com"))
        assertEquals(FailureKind.HTTP, failureKind(SourceHttpException(526, "manhwa18")))
        assertTrue(automaticRetryAllowed(SourceHttpException(526, "manhwa18")))
    }

    @Test fun httpDiagnosticsRecordOnlyTheFinalHostAndStatus() {
        val request = okhttp3.Request.Builder()
            .url("https://cdn.manhwa18.com/chapters/1.jpg?token=secret")
            .build()
        val response = okhttp3.Response.Builder()
            .request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(526)
            .message("Origin certificate")
            .build()
        val failure = httpFailure(response)
        val wrapped = SourceRequestFailure("manhwa18", SourceStage.IMAGE, failure)
        assertEquals("cdn.manhwa18.com", failure.host)
        assertEquals("https://cdn.manhwa18.com/chapters/1.jpg?token=secret", failure.location.toString())
        assertTrue(wrapped.diagnostic.contains("status=526"))
        assertTrue(wrapped.diagnostic.contains("host=cdn.manhwa18.com"))
        assertFalse(wrapped.diagnostic.contains("token"))
        assertFalse(wrapped.diagnostic.contains("/chapters"))
        assertFalse(wrapped.message.contains("token"))
        assertFalse(wrapped.message.contains("/chapters"))
    }

    @Test fun imageContentFailuresRemainSpecificAndDoNotAutomaticallyRetry() {
        for (problem in ImageContentProblem.entries) {
            val wrapped = SourceRequestFailure("demonic", SourceStage.IMAGE, ImageContentFailure(problem))
            assertTrue(wrapped.message.contains(problem.message))
            assertEquals(FailureKind.CONTENT, failureKind(wrapped))
            assertFalse(automaticRetryAllowed(wrapped))
            assertFalse(wrapped.diagnostic.contains("https://"))
        }
        val untrusted = SourceRequestFailure("demonic", SourceStage.IMAGE, ContentFailure("secret-cookie"))
        assertFalse(untrusted.message.contains("secret-cookie"))
    }
    @Test(expected = CancellationException::class) fun cancellationIsNeverPresentedAsSourceFailure() = runBlocking<Unit> {
        sourceRequest("zap", SourceStage.DETAILS) { throw CancellationException() }
    }
}
