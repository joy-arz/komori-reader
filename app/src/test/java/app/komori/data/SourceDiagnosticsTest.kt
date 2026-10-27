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
    @Test(expected = CancellationException::class) fun cancellationIsNeverPresentedAsSourceFailure() = runBlocking<Unit> {
        sourceRequest("zap", SourceStage.DETAILS) { throw CancellationException() }
    }
}
