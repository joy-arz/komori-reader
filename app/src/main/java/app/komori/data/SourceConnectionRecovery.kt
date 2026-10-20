package app.komori.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response

class SourceConnectionResetException(source: String, cause: IOException) :
    IOException(
        "${sourceNames[source]} reset the connection after a fresh-connection retry. Try another network or retry later. Verify Source only handles browser verification.",
        cause,
    )

/** One idempotent GET recovery; sessions and TLS validation are inherited unchanged. */
internal class SourceConnectionRecovery(
    private val client: OkHttpClient,
    private val source: String,
    private val retryDelayMillis: Long = 500,
    private val clock: () -> Long = System::nanoTime,
) {
    private val compatibility by lazy {
        client
            .newBuilder()
            .connectionPool(ConnectionPool())
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
    }
    @Volatile private var compatibilityUntil: Long? = null

    suspend fun <T> read(request: Request, consume: (Response) -> T): T {
        if (source != "nhentai" || request.method != "GET")
            return client.newCall(request).readResponse(consume)
        val usingCompatibility = compatibilityUntil?.let { clock() - it < 0 } == true
        try {
            return (if (usingCompatibility) compatibility else client)
                .newCall(request)
                .readResponse(consume)
        } catch (error: IOException) {
            if (!isConnectionReset(error)) throw error
        }
        delay(retryDelayMillis)
        try {
            val result =
                (if (usingCompatibility) client else compatibility)
                    .newCall(request)
                    .readResponse(consume)
            compatibilityUntil =
                if (usingCompatibility) null else clock() + TimeUnit.MINUTES.toNanos(15)
            return result
        } catch (error: IOException) {
            if (isConnectionReset(error)) throw SourceConnectionResetException(source, error)
            throw error
        }
    }
}
