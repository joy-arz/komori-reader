package app.komori.data

import java.io.EOFException
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

fun connectionMessage(error: Exception): String =
    when (error) {
        is SSLException ->
            "The source could not be reached securely. Your network may be redirecting or blocking it. Check your device clock or try another network."
        is UnknownHostException ->
            "The source could not be found. Check your internet connection and network access."
        is SourceConnectionResetException -> error.message.orEmpty()
        is SocketException ->
            if (isConnectionReset(error))
                "The source reset the connection. Retry, or try another network if it keeps happening."
            else "The source connection failed. Check your connection and retry."
        is SocketTimeoutException -> "The connection timed out. Please retry."
        else -> error.message ?: "Could not complete the request. Please retry."
    }

internal fun isConnectionReset(error: IOException): Boolean =
    error is EOFException ||
        error.message.orEmpty().startsWith("stream was reset", ignoreCase = true) ||
        (error is SocketException &&
            (error.message.orEmpty().contains("reset", true) ||
                error.message.orEmpty().contains("broken pipe", true)))
