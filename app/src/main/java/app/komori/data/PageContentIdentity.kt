package app.komori.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Only source-proven content paths can survive a delivery-host rotation. Unknown URLs stay exact. */
internal object PageContentIdentity {
    fun page(source: String, value: String): String {
        val url = value.toHttpUrlOrNull() ?: return value
        if (url.scheme != "https" || url.query != null || url.fragment != null) return value
        return when {
            source == "mangadex" && Regex("/(data|data-saver)/[A-Za-z0-9]+/[^/]+").matches(url.encodedPath) -> url.encodedPath
            source == "nhentai" && Regex("i[0-9]*[.]nhentai[.]net").matches(url.host) &&
                Regex("/galleries/[0-9]+/[0-9]+[.](jpg|jpeg|png|webp|gif|avif)").matches(url.encodedPath) -> "nhentai:${url.encodedPath}"
            else -> value
        }
    }
    fun manifest(source: String, urls: List<String>) = urls.joinToString("\n") { page(source, it) }
    fun legacyCompatible(source: String, old: String, urls: List<String>): Boolean {
        val previous = old.split('\n')
        if (previous.size != urls.size) return false
        return previous.zip(urls).all { (before, after) ->
            page(source, before) == page(source, after) || source == "mangadex" &&
                after.toHttpUrlOrNull()?.let { url ->
                    url.query == null && url.encodedPath.matches(Regex("/(data|data-saver)/[A-Za-z0-9]+/[^/]+")) &&
                        before == url.encodedPath.substringAfter('/', "").substringAfter('/')
                } == true
        }
    }
}
