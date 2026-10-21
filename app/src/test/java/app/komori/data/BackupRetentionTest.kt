package app.komori.data

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class BackupRetentionTest {
    private class Folder : BackupFolder {
        val files = linkedMapOf<String, Pair<String, ByteArray>>()
        var corrupt = false
        override fun create(name: String, bytes: ByteArray): String {
            files[name] = name to bytes.copyOf()
            return name
        }
        override fun read(id: String) = files.getValue(id).second.let { if (corrupt) it + 0.toByte() else it }
        override fun list() = files.map { BackupDocument(it.key, it.value.first) }
        override fun delete(id: String) = files.remove(id) != null
    }
    private fun bytes() = BackupCodec.encode(MetadataBackup(1700000000000, "test", emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))

    @Test fun verifiedRotationKeepsThreeAndPreservesManualAndUnrelatedDocuments() {
        val folder = Folder()
        folder.create("manual.json", byteArrayOf(1))
        folder.create("komori-auto-not-ours.json", byteArrayOf(2))
        repeat(6) { BackupRetention.save(folder, bytes(), 3, 1700000000000 + it) }
        assertEquals(5, folder.files.size)
        assertTrue(folder.files.containsKey("manual.json"))
        assertTrue(folder.files.containsKey("komori-auto-not-ours.json"))
        val automatic = folder.list().filter { it.name.startsWith("komori-auto-170") }
        assertEquals(3, automatic.size)
        assertTrue(automatic.all { it.name.substringAfter("komori-auto-").substringBefore('-').toLong() >= 1700000000003 })
        automatic.forEach { assertEquals(1700000000000, BackupCodec.decode(folder.read(it.id)).createdAt) }
    }

    @Test fun failedReadBackNeverPrunesPreviousBackups() {
        val folder = Folder()
        repeat(3) { BackupRetention.save(folder, bytes(), 3, 1700000000000 + it) }
        val before = folder.files.keys.toSet()
        folder.corrupt = true
        try { BackupRetention.save(folder, bytes(), 3, 1700000000004); fail("Corrupt provider must fail verification") }
        catch (_: IOException) { }
        assertEquals(before, folder.files.keys)
    }

    @Test fun corruptOlderFileDoesNotDisplaceValidRetainedBackups() {
        val folder = Folder()
        repeat(3) { BackupRetention.save(folder, bytes(), 3, 1700000000000 + it) }
        folder.create("komori-auto-1700000000004-00000000-0000-0000-0000-000000000000.json", byteArrayOf(1))
        val failures = BackupRetention.save(folder, bytes(), 3, 1700000000005)
        assertEquals(1, failures)
        val valid = folder.files.filter { it.key != "komori-auto-1700000000004-00000000-0000-0000-0000-000000000000.json" }
        assertEquals(3, valid.size)
        assertTrue(valid.keys.any { it.startsWith("komori-auto-1700000000001") })
        assertTrue(valid.keys.any { it.startsWith("komori-auto-1700000000002") })
    }

    @Test fun invalidBackupNeverCreatesOrDeletesAnything() {
        val folder = Folder()
        folder.create("manual.json", byteArrayOf(1))
        try { BackupRetention.save(folder, byteArrayOf(1), 3); fail("Invalid backup must fail") }
        catch (_: InvalidBackup) { }
        assertEquals(setOf("manual.json"), folder.files.keys)
    }
}
