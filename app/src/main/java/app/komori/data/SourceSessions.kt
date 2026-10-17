package app.komori.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import java.io.IOException
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

val sourceOrigins =
    linkedMapOf(
        "demonic" to "https://demonicscans.org",
        "weeb" to "https://weebcentral.com",
        "zap" to "https://hentaizap.com",
        "imhentai" to "https://imhentai.xxx",
        "nhentai" to "https://nhentai.net",
    )

interface SourceCookieStore {
    fun read(url: String): String?

    fun write(url: String, cookie: String)

    fun flush()
}

/** CookieManager applies expiry, domain, path and Secure rules before returning cookies. */
class SourceCookieJar(private val store: SourceCookieStore) : CookieJar {
    private fun allowed(url: HttpUrl) =
        url.isHttps && sourceOrigins.values.any { it.toHttpUrl().host == url.host }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!allowed(url)) return emptyList()
        return store.read(url.toString()).orEmpty().split(';').mapNotNull { part ->
            val name = part.substringBefore('=').trim()
            if (name.isEmpty() || !part.contains('=')) null
            else
                runCatching {
                        Cookie.Builder()
                            .name(name)
                            .value(part.substringAfter('=').trim())
                            .hostOnlyDomain(url.host)
                            .path("/")
                            .secure()
                            .build()
                    }
                    .getOrNull()
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!allowed(url)) return
        cookies
            .filter {
                url.host == it.domain || (!it.hostOnly && url.host.endsWith("." + it.domain))
            }
            .forEach { store.write(url.toString(), it.toString()) }
        store.flush()
    }
}

class SourceVerificationRequired(val source: String) :
    IOException(
        "${sourceNames[source]} requires browser verification. Choose Verify source, complete verification, then retry."
    )

class SourceHttpException(val status: Int, source: String) :
    IOException(
        when (status) {
            403 ->
                "${sourceNames[source]} denied access (HTTP 403). Try Verify source. The site may still restrict native access."
            429 ->
                "${sourceNames[source]} is rate limiting requests (HTTP 429). Wait before retrying."
            else -> "${sourceNames[source]} returned HTTP $status. Please retry later."
        }
    )

fun requiresSourceAction(error: Exception?): Boolean =
    error is SourceVerificationRequired ||
        (error is SourceHttpException && error.status in listOf(401, 403))

class SourceSessions(context: Context) {
    val userAgent: String = WebSettings.getDefaultUserAgent(context)
    private val manager = CookieManager.getInstance().apply { setAcceptCookie(true) }
    private val jar =
        SourceCookieJar(
            object : SourceCookieStore {
                override fun read(url: String) = manager.getCookie(url)

                override fun write(url: String, cookie: String) = manager.setCookie(url, cookie)

                override fun flush() = manager.flush()
            }
        )

    fun client(): OkHttpClient =
        MangaDex.client()
            .newBuilder()
            .cookieJar(jar)
            .addInterceptor { chain ->
                val request =
                    chain
                        .request()
                        .newBuilder()
                        .header("User-Agent", chain.request().header("User-Agent") ?: userAgent)
                        .build()
                val response = chain.proceed(request)
                val refererHost = response.request.header("Referer")?.toHttpUrlOrNull()?.host
                val source =
                    sourceOrigins.entries
                        .firstOrNull {
                            it.value.toHttpUrl().host == response.request.url.host ||
                                it.value.toHttpUrl().host == refererHost
                        }
                        ?.key
                if (source != null && response.header("cf-mitigated") == "challenge") {
                    response.close()
                    throw SourceVerificationRequired(source)
                }
                if (source != null && response.code in listOf(401, 403)) {
                    val status = response.code
                    response.close()
                    throw SourceHttpException(status, source)
                }
                response
            }
            .build()
}
