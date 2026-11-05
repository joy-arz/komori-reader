package app.komori

import android.graphics.Rect
import android.graphics.drawable.Animatable
import androidx.test.platform.app.InstrumentationRegistry
import app.komori.data.*
import app.komori.ui.WebtoonStripCache
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class ImageFormatsTest {
    @Test fun rasterFormatsDecodeFromExtensionlessFilesAndGifRemainsAnimated() = runBlocking {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val app = instrument.targetContext.applicationContext as KomoriApp
        for ((name, mime) in listOf("animated.gif" to "image/gif", "sample.bmp" to "image/bmp",
            "sample.webp" to "image/webp", "sample.png" to "image/png", "sample.jpg" to "image/jpeg")) {
            val file = File.createTempFile("format-", ".img", app.cacheDir)
            try {
                instrument.context.assets.open("images/$name").use { input -> file.outputStream().use { input.copyTo(it) } }
                val info = requireNotNull(ImageFormats.info(file))
                assertEquals(32, info.width); assertEquals(64, info.height)
                assertEquals(mime, info.mime)
                assertTrue(validImage(file)); assertTrue(decodableImage(file))
                assertNotNull(ImageFormats.exportExtension(mime))
                val region = ImageFormats.region(file, Rect(0, 16, 32, 48), 1)
                assertEquals(32, region.width); assertEquals(32, region.height); region.recycle()
                val strip = WebtoonStripCache.load(file, 0, 32, 32, 32)
                assertEquals(32, strip.height)
                if (name.endsWith("gif")) {
                    assertTrue(info.animated)
                    val result = app.imageLoader.execute(ImageRequest.Builder(app).data(file).size(32, 64).build())
                    assertTrue(result is SuccessResult)
                    assertTrue((result as SuccessResult).drawable is Animatable)
                }
            } finally { file.delete() }
        }
    }

    @Test fun demonicUsesOneAdvertisedAlternateForInvalidContentButNeverForVerification() = runBlocking {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val bytes = instrument.context.assets.open("images/sample.png").use { it.readBytes() }
        for ((source, host, status, expectedCalls) in listOf(
            listOf("demonic", "cdn.demoniclibs.com", "200", "2"),
            listOf("demonic", "cdn.demoniclibs.com", "403", "1"),
            listOf("demonic", "other.example", "200", "1"),
            listOf("weeb", "cdn.demoniclibs.com", "200", "1"),
            listOf("demonic", "cdn.demoniclibs.com", "challenge", "1"),
        )) {
            val calls = mutableListOf<Request>()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                synchronized(calls) { calls.add(chain.request()) }
                val alternate = chain.request().url.host == "cdn.librarydm.com"
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(if (alternate || status == "challenge") 200 else status.toInt()).message("Fixture")
                    .body(if (alternate) bytes.toResponseBody() else if (status == "challenge") "<html><script src=\"/cdn-cgi/challenge-platform/check\"></script></html>".toResponseBody() else ByteArray(0).toResponseBody()).build()
            }.build()
            val file = File.createTempFile("cdn-fallback-", ".img", instrument.targetContext.cacheDir)
            try {
                try { Sources(client).transferPage(sourceId(source, "/fixture"), "https://$host/series/202/17.jpeg", file)
                    if (expectedCalls != "2") fail("Invalid primary response must fail without an alternate")
                }
                catch (failure: SourceRequestFailure) {
                    assertEquals(1, expectedCalls.toInt()); assertFalse(file.exists())
                    if (status == "403" || status == "challenge") assertTrue(requiresSourceAction(failure))
                }
                assertEquals(expectedCalls.toInt(), calls.size)
                if (expectedCalls == "2") {
                    assertTrue(validImage(file))
                    assertEquals("cdn.librarydm.com", calls.last().url.host)
                    assertEquals(calls.first().url.encodedPath, calls.last().url.encodedPath)
                    assertEquals("https://demonicscans.org/", calls.last().header("Referer"))
                }
            } finally { file.delete(); client.dispatcher.executorService.shutdown() }
        }
    }

    @Test fun emptySuccessAndHtmlAreRejectedWithoutKeepingBadPage() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        for ((body, message) in listOf("" to "empty image", "<html>not an image</html>" to "unsupported or damaged")) {
            val file = File.createTempFile("bad-format-", ".img", app.cacheDir)
            val client = OkHttpClient.Builder().addInterceptor { chain -> Response.Builder().request(chain.request())
                .protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build() }.build()
            try {
                try { transferImage(client, Request.Builder().url("https://fixture.example/page.jpeg").build(), file); fail("Must reject invalid page") }
                catch (error: ContentFailure) { assertTrue(error.message.orEmpty().contains(message)) }
                assertFalse(file.exists())
            } finally { file.delete(); client.dispatcher.executorService.shutdown() }
        }
    }
}
