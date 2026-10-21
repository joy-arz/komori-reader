package app.komori.ui

import android.content.Context
import android.net.Uri
import app.komori.data.*
import coil.intercept.Interceptor
import coil.network.HttpException
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.ImageResult

private const val COVER_MANGA = "komori.cover.manga"

internal fun coverImageRequest(context: Context, manga: Manga): ImageRequest =
    ImageRequest.Builder(context)
        .data(manga.cover.ifBlank {
            if (sourceKey(manga.id) == "local") {
                val token = sourcePath(manga.id).trim('/').substringAfterLast('/')
                Uri.fromFile(pageFile(context, sourceId("local", "/chapter/$token/"), 0)).toString()
            } else ""
        })
        .setParameter(COVER_MANGA, manga, memoryCacheKey = null)
        .apply { sourceOrigins[sourceKey(manga.id)]?.let { addHeader("Referer", "$it/") } }
        .build()

internal class CoverFallbackInterceptor(private val recovery: CoverRecovery) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val result = chain.proceed(request)
        if (result !is ErrorResult) return result
        val manga = request.parameters.value<Manga>(COVER_MANGA) ?: return result
        val error = result.throwable
        if (error is SourceVerificationRequired ||
            error is SourceHttpException && error.status in listOf(401, 403, 429, 503) ||
            error is HttpException && error.response.code in listOf(401, 403, 429, 503)) return result
        val cover = recovery.resolve(manga) ?: return result
        return chain.proceed(request.newBuilder().data(cover).build())
    }
}
