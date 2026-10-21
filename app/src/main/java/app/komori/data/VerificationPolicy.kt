package app.komori.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun sourceSessionHosts(key: String): Set<String> {
    val host = sourceOrigins[key]?.toHttpUrl()?.host ?: return emptySet()
    return setOf(host, "www.$host")
}

fun allowsVerificationNavigation(key: String, url: String, mainFrame: Boolean): Boolean {
    val parsed = url.toHttpUrlOrNull() ?: return false
    return parsed.isHttps && (parsed.host in sourceSessionHosts(key) || !mainFrame && parsed.host == "challenges.cloudflare.com")
}
