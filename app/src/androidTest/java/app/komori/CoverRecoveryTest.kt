package app.komori

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import app.komori.data.*
import app.komori.ui.CoverFallbackInterceptor
import app.komori.ui.coverImageRequest
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.SuccessResult
import java.io.ByteArrayOutputStream
import java.util.Collections
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class CoverRecoveryTest {
    @Test fun discoverCoverRecoversWithoutOpeningDetailsAndHonorsSourceFailures() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(24, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.GREEN)
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        for ((source, thumbStatus, coverStatus) in listOf(
            Triple("zap", 404, 200), Triple("zap", 200, 200), Triple("zap", 404, 404),
            Triple("zap", 403, 200), Triple("zap", 429, 200), Triple("nhentai", 404, 200),
        )) {
            val requests = Collections.synchronizedList(mutableListOf<Request>())
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                requests.add(request)
                val gallery = request.url.encodedPath.startsWith("/gallery/")
                val status = if (gallery) 200 else if (request.url.encodedPath == "/thumb.jpg") thumbStatus else coverStatus
                val body = if (gallery) """<div class="hz-gallery-cover"><img src="https://cdn.example/cover.png"></div>"""
                    .toResponseBody("text/html".toMediaType()) else png.toResponseBody("image/png".toMediaType())
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status)
                    .message("Fixture").body(body).build()
            }.build()
            val sources = Sources(client)
            val loader = ImageLoader.Builder(context).okHttpClient(client).diskCache(null)
                .components { add(CoverFallbackInterceptor(sources.coverRecovery)) }.build()
            try {
                val book = Manga(sourceId(source, "/gallery/7/"), "Discover fixture", "", "https://cdn.example/thumb.jpg")
                val request = coverImageRequest(context, book).newBuilder().size(80, 100)
                    .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build()
                val result = loader.execute(request)
                if (thumbStatus == 200 || source == "zap" && thumbStatus == 404 && coverStatus == 200)
                    assertTrue("Expected decoded cover: $result", result is SuccessResult)
                else assertTrue(result is ErrorResult)
                val fallback = source == "zap" && thumbStatus == 404
                assertEquals(if (fallback) 3 else 1, requests.size)
                assertEquals(if (fallback) 1 else 0, requests.count { it.url.encodedPath.startsWith("/gallery/") })
                assertTrue(requests.all { it.header("Referer") == "${sourceOrigins[source]}/" })
            } finally { loader.shutdown() }
        }
    }
}
