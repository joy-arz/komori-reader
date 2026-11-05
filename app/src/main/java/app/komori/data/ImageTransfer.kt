package app.komori.data

import android.graphics.BitmapFactory
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*

const val MAX_IMAGE_BYTES = 32L * 1024 * 1024

internal fun copyImageBytes(
    input: InputStream,
    output: OutputStream,
    limit: Long = MAX_IMAGE_BYTES,
    progress: (Long) -> Unit = {},
    check: () -> Unit = {},
) {
    val buffer = ByteArray(32 * 1024)
    var total = 0L
    while (true) {
        check()
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > limit) throw ContentFailure("This image exceeds the 32 MB page limit.")
        storageOperation { output.write(buffer, 0, count) }
        progress(total)
    }
}

fun validImage(file: File): Boolean {
    return ImageFormats.info(file) != null
}

/** Cancellation closes the actual socket, including while the body is being read. */
suspend fun transferImage(
    client: OkHttpClient,
    request: Request,
    target: File,
    progress: (Long, Long) -> Unit = { _, _ -> },
) =
    suspendCancellableCoroutine<Unit> { continuation ->
        try {
            SourceCooldowns.check(request)
        } catch (e: SourceHttpException) {
            continuation.resumeWithException(e)
            return@suspendCancellableCoroutine
        }
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            if (it.header("cf-mitigated") == "challenge")
                                throw SourceVerificationRequired("source")
                            if (!it.isSuccessful) throw httpFailure(it,
                                if (request.header("Referer") == "https://mangadex.org/") "mangadex" else "source")
                            val body = it.body ?: throw IOException("Empty image response")
                            if (body.contentLength() > MAX_IMAGE_BYTES)
                                throw ContentFailure("This image exceeds the 32 MB page limit.")
                            storageOperation { target.outputStream() }
                                .use { out ->
                                    body.byteStream().use { input ->
                                        copyImageBytes(
                                            input,
                                            out,
                                            progress = { bytes ->
                                                progress(bytes, body.contentLength())
                                            },
                                        ) {
                                            if (!continuation.isActive)
                                                throw IOException("Image request cancelled")
                                            if (target.parentFile!!.usableSpace < 16L * 1024 * 1024)
                                                throw StorageFailure(
                                                    "Free storage space to continue loading images."
                                                )
                                        }
                                    }
                                }
                            if (target.length() == 0L)
                                throw ImageContentFailure(ImageContentProblem.EMPTY)
                            if (!validImage(target)) {
                                val prefix = target.inputStream().use { input ->
                                    val bytes = ByteArray(4096)
                                    val count = input.read(bytes).coerceAtLeast(0)
                                    String(bytes, 0, count, Charsets.UTF_8)
                                }
                                if (prefix.trimStart().startsWith("<") && listOf("cf-chl-", "/cdn-cgi/challenge-platform/", "challenges.cloudflare.com")
                                        .any { marker -> prefix.contains(marker, ignoreCase = true) })
                                    throw SourceVerificationRequired("source")
                                throw ImageContentFailure(ImageContentProblem.UNREADABLE)
                            }
                        }
                        if (continuation.isActive) continuation.resume(Unit)
                    } catch (e: Exception) {
                        target.delete()
                        if (continuation.isActive) continuation.resumeWithException(e)
                    } finally {
                        if (!continuation.isActive && call.isCanceled()) target.delete()
                    }
                }
            }
        )
    }
