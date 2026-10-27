package app.komori.data

import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.json.JSONException
import java.net.SocketTimeoutException

enum class SourceStage(val label: String) {
    CATALOG("Catalog/search"), DETAILS("Title details"), CHAPTERS("Chapter list"),
    MANIFEST("Page list"), IMAGE("Image transfer"), FILTERS("Source filters"), LANGUAGES("Languages")
}

/** Carries no query, URL, cookies or response text in its presentation/diagnostic summary. */
class SourceRequestFailure(val source: String, val stage: SourceStage, val failure: Exception) : IOException(null, failure) {
    override val message get() = "${sourceNames[source] ?: "Source"} · ${stage.label}: ${safeMessage()}"
    val diagnostic get() = "source=${source.takeIf { it in sourceNames } ?: "unknown"}; stage=${stage.name}; kind=${failureKind(failure).name}; type=${failure.javaClass.simpleName}"
    private fun safeMessage(): String = when (failureKind(failure)) {
        FailureKind.VERIFICATION -> "Verification is required. Open Verify source, complete the challenge, then retry."
        FailureKind.RATE_LIMIT -> "Requests are rate limited. Wait before retrying." + retryAtMillis(failure).takeIf { it > System.currentTimeMillis() }?.let {
            " Retry in ${((it - System.currentTimeMillis()) / 1000 + 1).coerceAtLeast(1)} seconds."
        }.orEmpty()
        FailureKind.MISSING_PAGE -> "This content is unavailable. Refresh or choose another release."
        FailureKind.STORAGE -> "Storage is unavailable. Free space and retry."
        FailureKind.CONTENT -> "Unsupported or invalid content. Refresh or try another release."
        FailureKind.SECURE_CONNECTION -> "A secure connection failed. Check the device clock or try another network."
        FailureKind.OFFLINE -> "The network or source is unreachable. Check connectivity and retry."
        FailureKind.HTTP -> "HTTP ${(failure as SourceHttpException).status}. Retry later or choose another release."
        FailureKind.TRANSIENT -> when (failure) {
            is JSONException, is IllegalArgumentException -> "The source format could not be read. Refresh or report this source."
            is SocketTimeoutException -> "The request timed out. Retry when the connection is stable."
            else -> "The connection was interrupted. Retry, or try another network if it persists."
        }
    }
}

internal fun originalFailure(error: Exception?): Exception? = if (error is SourceRequestFailure) originalFailure(error.failure) else error
internal suspend inline fun <T> sourceRequest(source: String, stage: SourceStage, block: () -> T): T =
    try { block() } catch (error: CancellationException) { throw error }
    catch (error: SourceRequestFailure) { throw error }
    catch (error: Exception) {
        val failure = if (error is JSONException || error is IllegalArgumentException) ContentFailure("The source format is unsupported.", error) else error
        throw SourceRequestFailure(source, stage, failure)
    }
