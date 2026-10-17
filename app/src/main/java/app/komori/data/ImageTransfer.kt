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
        if (total > limit) throw IOException("This image exceeds the 32 MB page limit.")
        output.write(buffer, 0, count)
        progress(total)
    }
}

fun validImage(file: File): Boolean {
    if (!file.isFile || file.length() == 0L) return false
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    return bounds.outWidth > 0 && bounds.outHeight > 0
}

/** Cancellation closes the actual socket, including while the body is being read. */
suspend fun transferImage(
    client: OkHttpClient,
    request: Request,
    target: File,
    progress: (Long, Long) -> Unit = { _, _ -> },
) =
    suspendCancellableCoroutine<Unit> { continuation ->
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
                                throw IOException(
                                    "This source requires browser verification. Try another source until access is restored."
                                )
                            if (!it.isSuccessful)
                                throw IOException("Image request failed (HTTP ${it.code}).")
                            val body = it.body ?: throw IOException("Empty image response")
                            if (body.contentLength() > MAX_IMAGE_BYTES)
                                throw IOException("This image exceeds the 32 MB page limit.")
                            target.outputStream().use { out ->
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
                                            throw IOException(
                                                "Free storage space to continue loading images."
                                            )
                                    }
                                }
                            }
                            if (!validImage(target))
                                throw IOException("The source did not return a valid image.")
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
