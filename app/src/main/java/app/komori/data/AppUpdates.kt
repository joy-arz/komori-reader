package app.komori.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import app.komori.BuildConfig
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

const val UPDATE_REPO = "https://github.com/joy-arz/komori-reader"
private const val MAX_APK_SIZE = 80L * 1024 * 1024

data class AppUpdate(
    val version: String,
    val code: Long,
    val url: String,
    val sha256: String,
    val size: Long,
    val releaseNotes: String = "",
)

data class UpdateState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val update: AppUpdate? = null,
    val percent: Int = 0,
    val file: File? = null,
    val message: String = "Automatic checks are limited to once every six hours; failed checks cool down for thirty minutes.",
    val channel: String = "stable",
    val snoozedUntil: Long = 0,
    val show: Boolean = false,
) {
    val checking get() = phase == UpdatePhase.CHECKING
    val downloading get() = phase == UpdatePhase.DOWNLOADING
    val busy get() = checking || downloading
}

object UpdatePolicy {
    fun acceptsChannel(tag: String, prerelease: Boolean, channel: String): Boolean =
        channel == "preview" || !prerelease && '-' !in tag

    fun automaticCheckAllowed(now: Long, attempt: Long, success: Long): Boolean {
        if (attempt <= 0 || now < attempt) return true
        val interval = if (success >= attempt) TimeUnit.HOURS.toMillis(6) else TimeUnit.MINUTES.toMillis(30)
        return now - attempt >= interval
    }

    // GitHub's /latest excludes prereleases. Sort version tags rather than relying
    // on publication order, which can change when an old release is republished.
    fun compareVersions(a: String, b: String): Int {
        fun parts(v: String): Pair<List<Int>, List<String>> {
            require(
                v.matches(Regex("v?[0-9]+\\.[0-9]+\\.[0-9]+(-[0-9A-Za-z]+(\\.[0-9A-Za-z]+)*)?"))
            )
            val split = v.removePrefix("v").split('-', limit = 2)
            val core = split[0].split('.').map { it.toInt() }
            require(core.size == 3 && core.all { it >= 0 })
            return core to split.getOrNull(1)?.split('.').orEmpty()
        }
        val (ac, ap) = parts(a)
        val (bc, bp) = parts(b)
        ac.indices.forEach { if (ac[it] != bc[it]) return ac[it].compareTo(bc[it]) }
        if (ap.isEmpty() != bp.isEmpty()) return if (ap.isEmpty()) 1 else -1
        for (i in 0 until minOf(ap.size, bp.size)) {
            val an = ap[i].toIntOrNull()
            val bn = bp[i].toIntOrNull()
            val cmp =
                if (an != null && bn != null) an.compareTo(bn)
                else if ((an == null) != (bn == null)) {
                    if (an == null) 1 else -1
                } else ap[i].compareTo(bp[i])
            if (cmp != 0) return cmp
        }
        return ap.size.compareTo(bp.size)
    }

    fun manifest(json: JSONObject, tag: String): AppUpdate {
        val version = json.getString("versionName")
        compareVersions(version, version)
        require(tag == "v$version") { "Update version does not match its release." }
        require(json.getString("packageName") == "app.komori.reader") { "Wrong update package." }
        val code = json.getLong("versionCode")
        val size = json.getLong("size")
        val sha = json.getString("sha256").lowercase()
        val url = json.getString("apkUrl")
        require(code > 0 && size in 1..MAX_APK_SIZE && sha.matches(Regex("[0-9a-f]{64}"))) {
            "Invalid update metadata."
        }
        require(url == "$UPDATE_REPO/releases/download/$tag/komori-$version.apk") {
            "Unexpected update download location."
        }
        return AppUpdate(version, code, url, sha, size)
    }

    fun verifyDigest(file: File, update: AppUpdate) {
        require(file.length() == update.size) { "Incomplete APK download. Please retry." }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        require(digest.digest().joinToString("") { "%02x".format(it) } == update.sha256) {
            "APK checksum mismatch."
        }
    }
}

private object UpdateNetwork {
    private var shared: OkHttpClient? = null
    @Synchronized fun client(context: Context): OkHttpClient = shared ?: (
OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "update-http"), 4L * 1024 * 1024))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.MINUTES)
            .build()
    ).also { shared = it }
}

private object UpdateSelection { val lock = Any(); val transfers = Mutex() }

open class AppUpdates(private val context: Context, private val transport: OkHttpClient? = null) : UpdateBackend {
    override val canDownload get() = !BuildConfig.DEBUG
    private val preferences = context.getSharedPreferences("app-updates", Context.MODE_PRIVATE)
    override fun channel() = preferences.getString("channel", "stable").orEmpty().takeIf { it in listOf("stable", "preview") } ?: "stable"
    override fun channel(value: String) = synchronized(UpdateSelection.lock) {
        require(value in listOf("stable", "preview"))
        preferences.edit().putString("channel", value).putLong("selectionRevision", preferences.getLong("selectionRevision", 0) + 1).remove("available").apply()
    }
    override fun snoozedUntil() = preferences.getLong("snoozedUntil", 0)
    override fun snooze() = preferences.edit().putLong("snoozedUntil", System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24)).apply()
    override fun automaticCheckAllowed() = UpdatePolicy.automaticCheckAllowed(System.currentTimeMillis(), preferences.getLong("attempt", 0), preferences.getLong("success", 0))
    override fun recordAttempt() = preferences.edit().putLong("attempt", System.currentTimeMillis()).apply()
    override fun recordSuccess() = preferences.edit().putLong("success", System.currentTimeMillis()).apply()
    override fun cachedUpdate(): AppUpdate? = runCatching {
        val json = JSONObject(preferences.getString("available", null) ?: return@runCatching null)
        val tag = "v${json.getString("versionName")}"
        UpdatePolicy.manifest(json, tag).copy(releaseNotes = json.optString("notes").take(32768)).takeIf {
            it.code > BuildConfig.VERSION_CODE && UpdatePolicy.compareVersions(it.version, BuildConfig.VERSION_NAME) > 0 && UpdatePolicy.acceptsChannel(tag, '-' in tag, channel())
        }
    }.getOrNull()

    private suspend fun remember(update: AppUpdate?, revision: Long) {
        val context = currentCoroutineContext()
        synchronized(UpdateSelection.lock) {
        context.ensureActive()
        if (preferences.getLong("selectionRevision", 0) != revision) return
        val edit = preferences.edit()
        if (update == null) edit.remove("available") else edit.putString("available", JSONObject()
            .put("versionName", update.version).put("versionCode", update.code).put("packageName", "app.komori.reader")
            .put("apkUrl", update.url).put("sha256", update.sha256).put("size", update.size).put("notes", update.releaseNotes.take(32768)).toString())
        edit.apply()
        }
    }

    private val client = transport ?: UpdateNetwork.client(context.applicationContext)
    private val directory
        get() = File(context.cacheDir, "updates").apply { mkdirs() }

    private suspend fun text(url: String, maxBytes: Long): String {
        return client
            .newCall(
                Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github+json")
                    .cacheControl(CacheControl.Builder().maxAge(0, TimeUnit.SECONDS).build())
                    .header("User-Agent", "Komori/${BuildConfig.VERSION_NAME}")
                    .build()
            )
            .readResponse { response ->
                if (!response.isSuccessful)
                    throw IOException(
                        "GitHub update check failed (${response.code}). Try again later."
                    )
                val body = response.body ?: throw IOException("Empty update response")
                val source = body.source()
                if (source.request(maxBytes + 1)) throw IOException("Update response is too large")
                source.readUtf8()
            }
    }

    override suspend fun check(): AppUpdate? =
        withContext(Dispatchers.IO) {
            val (selectedChannel, revision) = synchronized(UpdateSelection.lock) { channel() to preferences.getLong("selectionRevision", 0) }
            val releases =
                JSONArray(
                    text(
                        "https://api.github.com/repos/joy-arz/komori-reader/releases?per_page=30",
                        2 * 1024 * 1024,
                    )
                )
            val candidate =
                releases
                    .objects()
                    .filter { !it.optBoolean("draft") && UpdatePolicy.acceptsChannel(it.optString("tag_name"), it.optBoolean("prerelease"), selectedChannel) }
                    .filter { release ->
                        runCatching {
                                UpdatePolicy.compareVersions(
                                    release.getString("tag_name"),
                                    BuildConfig.VERSION_NAME,
                                ) > 0
                            }
                            .getOrDefault(false)
                    }
                    .filter {
                        it.getJSONArray("assets").objects().any { asset ->
                            asset.optString("name") == "update.json"
                        }
                    }
                    .maxWithOrNull { a, b ->
                        UpdatePolicy.compareVersions(
                            a.getString("tag_name"),
                            b.getString("tag_name"),
                        )
                    } ?: return@withContext null.also { remember(null, revision) }
            val tag = candidate.getString("tag_name")
            val update =
                UpdatePolicy.manifest(
                    JSONObject(text("$UPDATE_REPO/releases/download/$tag/update.json", 16 * 1024)),
                    tag,
                )
            if (update.code <= BuildConfig.VERSION_CODE) return@withContext null.also { remember(null, revision) }
            update.copy(releaseNotes = candidate.optString("body").take(32768)).also { remember(it, revision) }
        }

    override fun unmetered(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        return manager.activeNetwork != null && !manager.isActiveNetworkMetered
    }

    override suspend fun download(
        update: AppUpdate,
        unmeteredOnly: Boolean,
        progress: (Int) -> Unit,
    ): File = UpdateSelection.transfers.withLock {
        withContext(Dispatchers.IO) {
            OwnedTemporaryFiles.clean(directory, OwnedTemporaryFiles.updateParts)
            val target = File(directory, "update-${update.code}.apk")
            if (target.exists()) {
                try {
                    verify(target, update)
                    return@withContext target
                } catch (_: Exception) {
                    target.delete()
                }
            }
            val partial = OwnedTemporaryFiles.create(directory, "update-${update.code}-", ".part")
            try {
                client.newCall(Request.Builder().url(update.url).build()).readResponse { response
                    ->
                    OwnedTemporaryFiles.use(partial) {
                    if (!response.isSuccessful)
                        throw IOException("Update download failed (${response.code}).")
                    val body = response.body ?: throw IOException("Empty APK response")
                    var received = 0L
                    body.byteStream().use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                ensureActive()
                                if (unmeteredOnly && !unmetered())
                                    throw IOException(
                                        "Unmetered connection lost. Retry on Wi-Fi, or download manually."
                                    )
                                val count = input.read(buffer)
                                if (count == -1) break
                                received += count
                                if (received > update.size)
                                    throw IOException("Unexpected APK size.")
                                output.write(buffer, 0, count)
                                progress((received * 100 / update.size).toInt())
                            }
                        }
                    }
                    }
                }
                verify(partial, update)
                check(partial.renameTo(target)) { "Could not store the update." }
                directory.listFiles().orEmpty().filter { it != target && it.name.matches(Regex("update-[0-9]+[.]apk")) }
                    .sortedByDescending { it.lastModified() }.drop(1).forEach { runCatching { it.delete() } }
                target
            } finally {
                OwnedTemporaryFiles.discard(partial)
            }
        }
    }

    companion object {
        fun recoverTemporaryFiles(context: Context) {
            OwnedTemporaryFiles.clean(File(context.cacheDir, "updates"), OwnedTemporaryFiles.updateParts)
            OwnedTemporaryFiles.clean(File(context.cacheDir, "exports"), OwnedTemporaryFiles.exports)
            OwnedTemporaryFiles.clean(context.cacheDir, OwnedTemporaryFiles.exports)
        }
    }

    suspend fun download(update: AppUpdate, progress: (Int) -> Unit): File = download(update, false, progress)

    @Suppress("DEPRECATION")
    private fun verify(file: File, update: AppUpdate) {
        UpdatePolicy.verifyDigest(file, update)
        val flags =
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.path, flags) ?: error("Invalid APK")
        val installed = pm.getPackageInfo(context.packageName, flags)
        val archiveCode =
            if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode
            else archive.versionCode.toLong()
        val installedCode =
            if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode
            else installed.versionCode.toLong()
        require(
            archive.packageName == context.packageName &&
                archiveCode == update.code &&
                archiveCode > installedCode &&
                archive.versionName == update.version
        ) {
            "APK package or version does not match this update."
        }
        fun signers(info: android.content.pm.PackageInfo): Set<String> =
            (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners
                else info.signatures)
                ?.map { it.toCharsString() }
                ?.toSet()
                .orEmpty()
        val current = signers(installed)
        require(current.isNotEmpty() && current == signers(archive)) {
            "APK signing identity does not match the installed app."
        }
    }

    fun installIntent(file: File): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                FileProvider.getUriForFile(context, "${context.packageName}.updates", file),
                "application/vnd.android.package-archive",
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun permissionIntent() =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )
}
