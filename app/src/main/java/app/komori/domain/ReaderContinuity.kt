package app.komori.domain

import java.math.BigDecimal

object ReaderContinuity {
    fun gap(from: Release, to: Release): String? {
        if (from.mangaId != to.mangaId || from.language != to.language) return null
        val first = from.number?.toBigDecimalOrNull() ?: return null
        val last = to.number?.toBigDecimalOrNull() ?: return null
        if (last - first <= BigDecimal.ONE) return null
        return "Chapter gap: ${from.label} to ${to.label}. Intermediate chapters are not available in this feed."
    }

    fun previous(releases: List<Release>, current: Release): Release? {
        val index = releases.indexOfFirst { it.id == current.id }
        return if (index <= 0) null else releases.take(index).lastOrNull { it.key != current.key && it.readable }
    }
}
