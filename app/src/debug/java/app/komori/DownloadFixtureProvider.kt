package app.komori

import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import app.komori.data.*
import app.komori.domain.DownloadPolicy
import java.io.File
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Shell/instrumentation-only loopback fixtures. This provider does not exist in release APKs. */
class DownloadFixtureProvider : ContentProvider() {
    private val app get() = context!!.applicationContext as KomoriApp
    private val preferences get() = app.getSharedPreferences("download-fixtures", 0)
    override fun onCreate(): Boolean {
        app.downloadRuntime = object : DownloadRuntime {
            override fun source(id: String): Sources? {
                if (!id.startsWith("work-fixture-")) return null
                val endpoint = preferences.getString("endpoint:$id", null) ?: return null
                val base = endpoint.toHttpUrl()
                check(base.scheme == "http" && base.host == "127.0.0.1")
                val client = OkHttpClient.Builder().addInterceptor { chain ->
                    val url = chain.request().url
                    chain.proceed(chain.request().newBuilder().url(base.newBuilder().encodedPath(url.encodedPath).encodedQuery(url.encodedQuery).build()).build())
                }.build()
                return Sources(client, client)
            }
            override fun availableBytes(id: String, directory: File): Long? =
                if (id.startsWith("work-fixture-") && preferences.getBoolean("low:$id", false)) 0L else null
        }
        return true
    }
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle = runBlocking(Dispatchers.IO) {
        val id = requireNotNull(arg)
        require(id.matches(Regex("work-fixture-[A-Za-z0-9-]{1,100}")))
        app.startupRecovery?.join()
        when (method) {
            "configure" -> {
                val endpoint = requireNotNull(extras?.getString("endpoint")).toHttpUrl()
                require(endpoint.scheme == "http" && endpoint.host == "127.0.0.1" && endpoint.username.isEmpty() && endpoint.password.isEmpty())
                preferences.edit().putString("endpoint:$id", endpoint.toString()).commit()
            }
            "queue" -> {
                val existing = app.dao.download(id)
                require(existing?.state !in DownloadPolicy.activeStates)
                app.dao.putDownload(existing?.copy(state = "Queued", error = "") ?: Download(id, id, "WorkManager fixture", 3,
                    queuePosition = app.dao.lastQueuePosition() + 1))
                kickDownloadQueue(app)
            }
            "pause" -> app.dao.download(id)?.let { DownloadCoordinator(app).stop(it, "Paused") }
            "delete" -> app.dao.download(id)?.let { DownloadCoordinator(app).delete(it) }
            "low" -> preferences.edit().putBoolean("low:$id", extras?.getBoolean("value") == true).commit()
            "recover" -> kickDownloadQueue(app, recoverInterrupted = true)
            "cleanup" -> {
                app.dao.download(id)?.let { DownloadCoordinator(app).delete(it) }
                preferences.edit().remove("endpoint:$id").remove("low:$id").commit()
            }
            else -> error("Unknown fixture command")
        }
        Bundle().apply { putString("result", "ok") }
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor = runBlocking(Dispatchers.IO) {
        val id = uri.lastPathSegment.orEmpty()
        require(id.startsWith("work-fixture-"))
        MatrixCursor(arrayOf("state", "done", "total")).apply { app.dao.download(id)?.let { addRow(arrayOf<Any>(it.state, it.done, it.total)) } }
    }
    override fun getType(uri: Uri) = "vnd.android.cursor.item/download-fixture"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = throw UnsupportedOperationException()
}
