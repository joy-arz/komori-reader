package app.komori.ui

import java.io.File

/** Access order must not change the file revision used by decoded image caches. */
internal object ReaderCacheAccess {
    private val accesses = object : LinkedHashMap<String, Long>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 512
    }

    @Synchronized
    fun touch(file: File) {
        accesses[file.path] = System.currentTimeMillis()
    }

    @Synchronized
    fun lastAccess(file: File): Long = accesses[file.path] ?: file.lastModified()
}
