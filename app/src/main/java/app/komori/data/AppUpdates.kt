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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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
)

data class UpdateState(
    val checking: Boolean = false,
    val update: AppUpdate? = null,
    val downloading: Boolean = false,
    val percent: Int = 0,
    val file: File? = null,
    val message: String = "Checks GitHub whenever the app launches.",
    val show: Boolean = false,
)

object UpdatePolicy {
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

class AppUpdates(private val context: Context) {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.MINUTES)
            .build()
    private val directory
        get() = File(context.cacheDir, "updates").apply { mkdirs() }

    private fun text(url: String, maxBytes: Long): String {
        client
            .newCall(
                Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Komori/${BuildConfig.VERSION_NAME}")
                    .build()
            )
            .execute()
            .use { response ->
                if (!response.isSuccessful)
                    throw IOException(
                        "GitHub update check failed (${response.code}). Try again later."
                    )
                val body = response.body ?: throw IOException("Empty update response")
                val source = body.source()
                if (source.request(maxBytes + 1)) throw IOException("Update response is too large")
                return source.readUtf8()
            }
    }

    suspend fun check(): AppUpdate? =
        withContext(Dispatchers.IO) {
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
                    .filter { !it.optBoolean("draft") }
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
                    } ?: return@withContext null
            val tag = candidate.getString("tag_name")
            val update =
                UpdatePolicy.manifest(
                    JSONObject(text("$UPDATE_REPO/releases/download/$tag/update.json", 16 * 1024)),
                    tag,
                )
            if (update.code <= BuildConfig.VERSION_CODE) return@withContext null
            update
        }

    fun unmetered(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        return manager.activeNetwork != null && !manager.isActiveNetworkMetered
    }

    suspend fun download(
        update: AppUpdate,
        unmeteredOnly: Boolean = false,
        progress: (Int) -> Unit,
    ): File =
        withContext(Dispatchers.IO) {
            val target = File(directory, "update-${update.code}.apk")
            directory.listFiles()?.filter { it != target }?.forEach { it.delete() }
            if (target.exists()) {
                try {
                    verify(target, update)
                    return@withContext target
                } catch (_: Exception) {
                    target.delete()
                }
            }
            val partial = File(directory, "update.part")
            try {
                client.newCall(Request.Builder().url(update.url).build()).execute().use { response
                    ->
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
                verify(partial, update)
                check(partial.renameTo(target)) { "Could not store the update." }
                target
            } finally {
                partial.delete()
            }
        }

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
