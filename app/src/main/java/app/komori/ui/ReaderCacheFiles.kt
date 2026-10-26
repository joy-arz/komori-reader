package app.komori.ui

import java.io.File
import java.io.IOException

/** Coordinates cache publication, visible/decoder leases and temporary-file ownership. */
internal object ReaderCacheFiles {
    private data class Pin(val files: Set<String>, val group: Any?)
    private val pins = mutableMapOf<Any, Pin>()
    private val writers = mutableSetOf<String>()

    @Synchronized fun pin(owner: Any, files: Collection<File>, group: Any? = null) { pins[owner] = Pin(files.map { it.absolutePath }.toSet(), group) }
    @Synchronized fun release(owner: Any) { pins.remove(owner) }
    @Synchronized fun releaseGroup(group: Any) { pins.entries.removeAll { it.value.group === group } }
    @Synchronized private fun protected(path: String) = path in writers || pins.values.any { path in it.files }

    suspend fun <T> leased(file: File, block: suspend () -> T): T {
        val owner = Any()
        pin(owner, listOf(file))
        try { return block() } finally { release(owner) }
    }

    @Synchronized fun temporary(directory: File): File = File.createTempFile("page-", ".part", directory).also { writers += it.absolutePath }
    @Synchronized fun discard(file: File) { file.delete(); writers -= file.absolutePath }

    @Synchronized fun publish(temporary: File, target: File, budget: Long) {
        if (!temporary.renameTo(target)) throw IOException("Could not cache page")
        writers -= temporary.absolutePath
        ReaderCacheAccess.touch(target)
        var total = target.parentFile!!.listFiles().orEmpty().sumOf { it.length() }
        target.parentFile!!.listFiles().orEmpty()
            .filter { it != target && it.extension == "img" && !protected(it.absolutePath) }
            .sortedBy { ReaderCacheAccess.lastAccess(it) }
            .forEach { file ->
                if (total > budget) { val bytes = file.length(); if (file.delete()) total -= bytes }
            }
    }

    /** After process death none of the previous process's transfers have a live owner. */
    @Synchronized fun clean(directory: File, images: Boolean = false): Long {
        var removed = 0L
        directory.listFiles().orEmpty().filter {
            (it.extension == "part" || images && it.extension == "img") && !protected(it.absolutePath)
        }.forEach { file -> val bytes = file.length(); if (file.delete()) removed += bytes }
        return removed
    }
}
