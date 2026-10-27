package app.komori.data

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class OwnedTemporaryFilesTest {
    @Test fun orphanCleanupIsScopedRepeatableAndKeepsReadyApksAndActiveFiles() {
        val directory = Files.createTempDirectory("update-ownership").toFile()
        val active = OwnedTemporaryFiles.create(directory, "update-100-", ".part").apply { writeText("active") }
        val orphan = File(directory, "update-99-1234.part").apply { writeText("orphan") }
        val ready = File(directory, "update-99.apk").apply { writeText("verified fixture") }
        val unrelated = File(directory, "holiday.part").apply { writeText("keep") }
        try {
            assertEquals(6, OwnedTemporaryFiles.clean(directory, OwnedTemporaryFiles.updateParts).toInt())
            assertFalse(orphan.exists()); assertTrue(active.exists()); assertTrue(ready.exists()); assertTrue(unrelated.exists())
            assertEquals(0L, OwnedTemporaryFiles.clean(directory, OwnedTemporaryFiles.updateParts))
        } finally { OwnedTemporaryFiles.discard(active); directory.deleteRecursively() }
    }

    @Test fun canceledCallerDoesNotDeleteCallbackFileUntilTheCallbackUnwinds() {
        val directory = Files.createTempDirectory("update-callback").toFile()
        val file = OwnedTemporaryFiles.create(directory, "update-100-", ".part").apply { writeText("active") }
        val entered = CountDownLatch(1); val finish = CountDownLatch(1)
        val thread = Thread { OwnedTemporaryFiles.use(file) { entered.countDown(); check(finish.await(5, TimeUnit.SECONDS)); assertTrue(file.exists()) } }
        try {
            thread.start(); assertTrue(entered.await(5, TimeUnit.SECONDS))
            OwnedTemporaryFiles.discard(file)
            assertEquals(0L, OwnedTemporaryFiles.clean(directory, OwnedTemporaryFiles.updateParts)); assertTrue(file.exists())
            finish.countDown(); thread.join(5000); assertFalse(thread.isAlive); assertFalse(file.exists())
        } finally { finish.countDown(); thread.join(5000); directory.deleteRecursively() }
    }

    @Test fun legacyExportOrphansAreReclaimedWithoutTouchingOtherArchives() {
        val directory = Files.createTempDirectory("export-ownership").toFile()
        val orphan = File(directory, "comic-123456.cbz").apply { writeText("abandoned") }
        val unrelated = File(directory, "my-comic.cbz").apply { writeText("keep") }
        try { assertEquals(9L, OwnedTemporaryFiles.clean(directory, OwnedTemporaryFiles.exports)); assertFalse(orphan.exists()); assertTrue(unrelated.exists()) }
        finally { directory.deleteRecursively() }
    }
}
