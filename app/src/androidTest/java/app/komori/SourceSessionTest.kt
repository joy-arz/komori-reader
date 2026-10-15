package app.komori

import android.webkit.CookieManager
import androidx.test.platform.app.InstrumentationRegistry
import app.komori.data.SourceSessions
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SourceSessionTest {
    @Test fun webViewCookiesReachNativeClientWithPathAndHostIsolation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val origin = "https://nhentai.net/"
        fun setCookie(value: String) {
            val done = CountDownLatch(1)
            instrumentation.runOnMainSync {
                CookieManager.getInstance().setCookie(origin, value) { done.countDown() }
            }
            assertTrue(done.await(5, TimeUnit.SECONDS))
            CookieManager.getInstance().flush()
        }
        setCookie("komori_session_test=verified; Path=/g/; Secure; HttpOnly; Max-Age=60")
        try {
            val sessions = SourceSessions(instrumentation.targetContext)
            val jar = sessions.client().cookieJar
            assertTrue(jar.loadForRequest("${origin}g/1/".toHttpUrl()).any {
                it.name == "komori_session_test" && it.value == "verified"
            })
            assertFalse(jar.loadForRequest("${origin}search/".toHttpUrl()).any {
                it.name == "komori_session_test"
            })
            assertTrue(jar.loadForRequest("https://images.example.com/".toHttpUrl()).isEmpty())
            assertTrue(jar.loadForRequest("http://nhentai.net/g/1/".toHttpUrl()).isEmpty())
            assertTrue(sessions.userAgent.isNotBlank())
        } finally {
            setCookie("komori_session_test=; Path=/g/; Secure; Max-Age=0")
        }
    }
}
