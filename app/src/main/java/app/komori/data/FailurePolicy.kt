package app.komori.data

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLException
import okhttp3.Request
import okhttp3.Response

enum class FailureKind {
    OFFLINE,
    RATE_LIMIT,
    MISSING_PAGE,
    STORAGE,
    VERIFICATION,
    CONTENT,
    SECURE_CONNECTION,
    HTTP,
    TRANSIENT,
}

class StorageFailure(message: String, cause: Throwable? = null) : IOException(message, cause)

class ContentFailure(message: String) : IOException(message)

internal inline fun <T> storageOperation(block: () -> T): T =
    try {
        block()
    } catch (e: IOException) {
        throw StorageFailure("Could not write page data. Check free storage and retry.", e)
    }

class PageRetryLimit(cause: Exception) :
    IOException(
        "${connectionMessage(cause)} Automatic retries stopped for this page. Retry manually to try again.",
        cause,
    )

fun failureKind(error: Exception?): FailureKind = classifyFailure(originalFailure(error))

private fun classifyFailure(error: Exception?): FailureKind =
    when {
        requiresSourceAction(error) -> FailureKind.VERIFICATION
        error is StorageFailure -> FailureKind.STORAGE
        error is ContentFailure -> FailureKind.CONTENT
        error is SSLException -> FailureKind.SECURE_CONNECTION
        error is UnknownHostException ||
            error is NoRouteToHostException ||
            error is ConnectException -> FailureKind.OFFLINE
        error is SourceHttpException &&
            error.status in listOf(429, 503) &&
            error.retryAtMillis != null -> FailureKind.RATE_LIMIT
        error is SourceHttpException && error.status in listOf(404, 410) -> FailureKind.MISSING_PAGE
        error is SourceHttpException -> FailureKind.HTTP
        else -> FailureKind.TRANSIENT
    }

fun automaticRetryAllowed(error: Exception?): Boolean =
    error !is PageRetryLimit &&
        when (failureKind(error)) {
            FailureKind.STORAGE,
            FailureKind.CONTENT,
            FailureKind.SECURE_CONNECTION,
            FailureKind.VERIFICATION,
            FailureKind.MISSING_PAGE -> false
            FailureKind.HTTP ->
                (originalFailure(error) as SourceHttpException).status.let { it in listOf(408, 425, 429) || it >= 500 }
            else -> true
        }

fun retryAtMillis(error: Exception?): Long = (originalFailure(error) as? SourceHttpException)?.retryAtMillis ?: 0L

internal fun retryAfterDeadline(header: String?, now: Long = System.currentTimeMillis()): Long? {
    val value = header?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (value.all { it in '0'..'9' }) {
        val seconds = value.toLongOrNull() ?: return Long.MAX_VALUE
        return now + seconds.coerceAtMost((Long.MAX_VALUE - now) / 1000) * 1000
    }
    return runCatching {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant()
                .toEpochMilli()
                .coerceAtLeast(now)
        }
        .getOrNull()
}

/**
 * Origin-scoped cooldowns prevent a second page from immediately repeating a rate-limited request.
 */
internal object SourceCooldowns {
    private val deadlines = ConcurrentHashMap<String, Long>()

    private fun origin(request: Request) =
        "${request.url.scheme}://${request.url.host}:${request.url.port}"

    fun check(request: Request, source: String = "source", now: Long = System.currentTimeMillis()) {
        deadlines.entries.removeIf { it.value <= now }
        deadlines[origin(request)]?.let {
            throw SourceHttpException(429, source, it, deferred = true)
        }
    }

    fun record(response: Response, deadline: Long) {
        deadlines.merge(origin(response.request), deadline, ::maxOf)
    }
}

internal fun httpFailure(
    response: Response,
    source: String = "source",
    now: Long = System.currentTimeMillis(),
): SourceHttpException {
    val until =
        if (response.code == 429 || response.code == 503)
            retryAfterDeadline(response.header("Retry-After"), now)
                ?: if (response.code == 429) now + 30_000 else null
        else null
    if (until != null && until > now) SourceCooldowns.record(response, until)
    return SourceHttpException(response.code, source, until)
}

/**
 * Failures survive lazy-item disposal but reset for a new reader session or explicit manual retry.
 */
class PageRetryBudget(private val limit: Int = 4) {
    init {
        require(limit > 0)
    }

    private data class Failure(val attempts: Int, val error: Exception)

    private val failures = mutableMapOf<String, Failure>()

    @Synchronized
    fun check(key: String) {
        failures[key]?.let {
            if (!automaticRetryAllowed(it.error)) throw it.error
            if (it.attempts >= limit) throw PageRetryLimit(it.error)
        }
    }

    @Synchronized
    fun failed(key: String, error: Exception): Exception {
        if ((originalFailure(error) as? SourceHttpException)?.deferred == true) return error
        val count = (failures[key]?.attempts ?: 0) + 1
        failures[key] = Failure(count, error)
        return if (count >= limit && automaticRetryAllowed(error)) PageRetryLimit(error) else error
    }

    @Synchronized
    fun reset(key: String) {
        failures.remove(key)
    }

    @Synchronized
    fun clear() {
        failures.clear()
    }
}
