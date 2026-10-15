package app.komori.data

import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private fun metadata() =
        JSONObject()
            .put("versionName", "0.5.0-alpha.1")
            .put("versionCode", 106)
            .put("packageName", "app.komori.reader")
            .put("apkUrl", "$UPDATE_REPO/releases/download/v0.5.0-alpha.1/komori-0.5.0-alpha.1.apk")
            .put("size", 4)
            .put("sha256", "a".repeat(64))

    @Test
    fun alphaNumbersSortNumerically() {
        assertTrue(UpdatePolicy.compareVersions("v0.5.0-alpha.10", "0.5.0-alpha.2") > 0)
        assertTrue(UpdatePolicy.compareVersions("0.5.0", "0.5.0-alpha.10") > 0)
        assertTrue(UpdatePolicy.compareVersions("0.4.9", "0.5.0-alpha.1") < 0)
        assertEquals(0, UpdatePolicy.compareVersions("v0.5.0-alpha.1", "0.5.0-alpha.1"))
    }

    @Test
    fun acceptsMatchingReleaseManifest() {
        assertEquals(106L, UpdatePolicy.manifest(metadata(), "v0.5.0-alpha.1").code)
    }

    @Test
    fun rejectsWrongRepositoryPackageTagAndOversize() {
        listOf(
                metadata().put("apkUrl", "https://example.com/app.apk"),
                metadata().put("packageName", "other.app"),
                metadata().put("size", 90L * 1024 * 1024),
                metadata().put("sha256", "bad"),
                metadata().put("versionCode", -1),
                metadata().put("versionName", "0.5.0-alpha.2"),
            )
            .forEach { json ->
                assertThrows(IllegalArgumentException::class.java) {
                    UpdatePolicy.manifest(json, "v0.5.0-alpha.1")
                }
            }
    }

    @Test
    fun rejectsInvalidVersionTags() {
        listOf("latest", "1.2", "1.2.3-../../evil", "-1.2.3").forEach {
            assertThrows(IllegalArgumentException::class.java) {
                UpdatePolicy.compareVersions(it, "0.5.0")
            }
        }
    }

    @Test
    fun rejectsCorruptedOrTruncatedDownloads() {
        val file = File.createTempFile("komori-update", ".apk")
        try {
            file.writeText("good")
            val sha =
                MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") {
                    "%02x".format(it)
                }
            val update = UpdatePolicy.manifest(metadata().put("sha256", sha), "v0.5.0-alpha.1")
            UpdatePolicy.verifyDigest(file, update)
            file.writeText("evil")
            assertThrows(IllegalArgumentException::class.java) {
                UpdatePolicy.verifyDigest(file, update)
            }
            file.writeText("go")
            assertThrows(IllegalArgumentException::class.java) {
                UpdatePolicy.verifyDigest(file, update)
            }
        } finally {
            file.delete()
        }
    }
}
