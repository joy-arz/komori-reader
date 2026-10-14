package app.komori.data

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

fun connectionMessage(error: Exception): String =
    when (error) {
        is SSLException ->
            "MangaDex could not be reached securely. Check your device clock or try a network that can access MangaDex."
        is UnknownHostException ->
            "MangaDex could not be found. Check your internet connection and network access."
        is SocketTimeoutException -> "The connection timed out. Please retry."
        else -> error.message ?: "Could not complete the request. Please retry."
    }
