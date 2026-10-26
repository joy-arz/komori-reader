package app.komori.data

import android.content.Context
import java.util.concurrent.TimeUnit

/** Attempt recency rotates failures fairly without overwriting successful catalog snapshots. */
internal class AuthorCheckRotation(context: Context) {
    private val store = context.getSharedPreferences("author-check-rotation", Context.MODE_PRIVATE)
    fun candidates(creators: List<Creator>, now: Long = System.currentTimeMillis()): List<Creator> = creators
        .filter { store.getLong("due:${it.id}", 0) <= now }
        .sortedWith(compareBy<Creator> { store.getLong("attempt:${it.id}", 0) }.thenBy { it.id }).take(3)

    fun attempted(id: String, success: Boolean, now: Long = System.currentTimeMillis()) {
        val failures = if (success) 0 else (store.getInt("failures:$id", 0) + 1).coerceAtMost(6)
        val wait = if (success) 0L else minOf(TimeUnit.HOURS.toMillis(12), TimeUnit.MINUTES.toMillis(30) * (1L shl (failures - 1)))
        store.edit().putLong("attempt:$id", now).putLong("due:$id", now + wait).putInt("failures:$id", failures).apply()
    }
}
