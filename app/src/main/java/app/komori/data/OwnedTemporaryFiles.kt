package app.komori.data

import java.io.File

/** Ownership outlives a canceled caller while a socket callback is still unwinding. */
internal object OwnedTemporaryFiles {
    private val references = mutableMapOf<String, Int>()
    val updateParts = Regex("update-[0-9]+-[A-Za-z0-9_-]+\\.part")
    val exports = Regex("comic-[0-9]+\\.cbz")

    @Synchronized fun create(directory: File, prefix: String, suffix: String): File {
        check(directory.isDirectory || directory.mkdirs()) { "Could not prepare temporary storage." }
        return File.createTempFile(prefix, suffix, directory).also { references[it.absolutePath] = 1 }
    }
    @Synchronized private fun acquire(file: File) {
        val count = references[file.absolutePath] ?: throw java.io.IOException("Temporary transfer was canceled.")
        references[file.absolutePath] = count + 1
    }
    @Synchronized fun discard(file: File) {
        val remaining = (references[file.absolutePath] ?: 1) - 1
        if (remaining > 0) references[file.absolutePath] = remaining
        else { references.remove(file.absolutePath); runCatching { file.delete() } }
    }
    fun <T> use(file: File, block: () -> T): T {
        acquire(file)
        try { return block() } finally { discard(file) }
    }
    @Synchronized fun clean(directory: File, owned: Regex): Long {
        var bytes = 0L
        val files = try { directory.listFiles().orEmpty() } catch (_: SecurityException) { emptyArray() }
        files.filter { it.isFile && owned.matches(it.name) && it.absolutePath !in references }.forEach {
            try { val size = it.length(); if (it.delete()) bytes += size } catch (_: SecurityException) { }
        }
        return bytes
    }
}
