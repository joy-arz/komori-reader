package app.komori.data

import kotlin.coroutines.resume

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
        "manhwa18" to "https://manhwa18.com",
        "manhwa18cc" to "https://manhwa18.cc",
    )

interface SourceCookieStore {
    fun read(url: String): String?

    fun write(url: String, cookie: String)

    fun flush()
}

/** CookieManager applies expiry, domain, path and Secure rules before returning cookies. */
class SourceCookieJar(private val store: SourceCookieStore) : CookieJar {
    private fun allowed(url: HttpUrl) =
        url.isHttps && sourceOrigins.keys.any { url.host in sourceSessionHosts(it) }

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
        "${sourceNames[source] ?: "The source"} requires browser verification. Choose Verify source, complete verification, then retry."
    )

class SourceHttpException(
    val status: Int,
    val source: String,
    val retryAtMillis: Long? = null,
    val deferred: Boolean = false,
) :
    IOException(
        when (status) {
            403 ->
                if (source == "mangadex") "MangaDex could not serve this chapter or image (HTTP 403). Refresh the chapter, try another release or try another network."
                else "${sourceNames[source] ?: "The source"} denied access (HTTP 403). Try Verify source. The site may still restrict native access."
            429 ->
                "${sourceNames[source] ?: "The source"} is rate limiting requests (HTTP 429). Wait before retrying."
            404,
            410 ->
                "${sourceNames[source] ?: "The source"} no longer has this page (HTTP $status). Refresh the chapter or try another release."
            503 ->
                "${sourceNames[source] ?: "The source"} is temporarily unavailable (HTTP 503). Wait before retrying."
            else ->
                "${sourceNames[source] ?: "The source"} returned HTTP $status. Please retry later."
        }
    )

fun requiresSourceAction(error: Exception?): Boolean =
    error is SourceVerificationRequired ||
        (error is SourceHttpException && error.source != "mangadex" && error.status in listOf(401, 403))

class SourceSessions(context: Context) {
    val userAgent: String = WebSettings.getDefaultUserAgent(context)
    private val manager = CookieManager.getInstance().apply { setAcceptCookie(true) }
    private val paths = context.getSharedPreferences("source-session-paths", Context.MODE_PRIVATE)
    private val pathLock = Any()

    private fun rememberPath(url: String) {
        val parsed = url.toHttpUrlOrNull() ?: return
        if (parsed.encodedPath.length > 512 || ';' in parsed.encodedPath) return
        synchronized(pathLock) {
            val known = paths.getStringSet(parsed.host, emptySet()).orEmpty().toMutableSet()
            if (parsed.encodedPath !in known) {
                if (known.size >= 64) known.remove(known.first())
                known.add(parsed.encodedPath)
                paths.edit().putStringSet(parsed.host, known).apply()
            }
        }
    }

    /** Expires source cookies on root, supported routes and paths used by native requests. */
    suspend fun resetSource(key: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
        val hosts = sourceSessionHosts(key)
        require(hosts.isNotEmpty())
        val deletes = linkedSetOf<Pair<String, String>>()
        for (host in hosts) {
            val candidates = linkedSetOf("/", "/g/", "/gallery/", "/artist/", "/author/", "/search/", "/login/", "/reader/")
            paths.getStringSet(host, emptySet()).orEmpty().forEach { path ->
                candidates.add(path)
                path.indices.filter { path[it] == '/' }.forEach { candidates.add(path.take(it + 1)) }
            }
            val names = candidates.take(256).flatMap { path ->
                manager.getCookie("https://$host$path").orEmpty().split(';').map { it.substringBefore('=').trim() }
            }.filter { it.matches(Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,256}")) }.distinct().take(64)
            for (path in candidates.take(256)) for (name in names) for (domain in listOf("", "; Domain=$host", "; Domain=${host.removePrefix("www.")}")) {
                deletes.add("https://$host$path" to "$name=; Max-Age=0; Path=$path; Secure; HttpOnly$domain")
            }
            android.webkit.WebStorage.getInstance().deleteOrigin("https://$host")
        }
        for ((url, cookie) in deletes) kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
            manager.setCookie(url, cookie) { if (continuation.isActive) continuation.resume(Unit) }
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { manager.flush() }
        synchronized(pathLock) { val edit = paths.edit(); hosts.forEach { edit.remove(it) }; edit.apply() }
    }
    private val jar =
        SourceCookieJar(
            object : SourceCookieStore {
                override fun read(url: String): String? { rememberPath(url); return manager.getCookie(url) }

                override fun write(url: String, cookie: String) { rememberPath(url); manager.setCookie(url, cookie) }

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
                SourceCooldowns.check(request)
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
                if (response.code in listOf(429, 503)) {
                    val failure = httpFailure(response, source ?: "source")
                    response.close()
                    throw failure
                }
                response
            }
            .build()
}
