package app.komori.data

import app.komori.domain.DownloadPolicy
import app.komori.domain.Release
import org.junit.Assert.*
import org.junit.Test

class BacklogPoliciesTest {
    private fun release(id: String, number: String? = "1", language: String = "en", manga: String = "book") =
        Release(id, manga, number, null, "", language, emptyList(), "", 20)

    @Test fun migrationRequiresUnambiguousNumberVolumeAndLanguage() {
        val old = release("old")
        val position = Progress(old.id, old.mangaId, old.key, 7, false)
        val candidate = release("new", "1.0", manga = "other")
        assertEquals(candidate, SourceMigrationPolicy.propose(listOf(old), listOf(position), listOf(candidate)).single().target)
        assertNull(SourceMigrationPolicy.propose(listOf(old), listOf(position), listOf(candidate, candidate.copy(id = "alternate"))).single().target)
        assertNull(SourceMigrationPolicy.propose(listOf(old), listOf(position), listOf(candidate.copy(language = "ja"))).single().target)
        assertNull(SourceMigrationPolicy.propose(listOf(old.copy(number = null)), listOf(position), listOf(candidate)).single().target)
        assertNull(SourceMigrationPolicy.propose(listOf(old), listOf(position), listOf(candidate.copy(volume = "2"))).single().target)
    }

    @Test fun migrationPreservesCompletedLogicalRecordAcrossAlternates() {
        val old = release("old")
        val alternative = old.copy(id = "old-alt")
        val partial = Progress(old.id, old.mangaId, old.key, 15, false)
        val complete = partial.copy(releaseId = alternative.id, completed = true, page = 2)
        val plan = SourceMigrationPolicy.propose(listOf(old, alternative), listOf(partial, complete), listOf(release("new", manga = "other")))
        assertEquals(1, plan.size)
        assertTrue(plan.single().position.completed)
    }

    @Test fun updateBaselineAvoidsFloodsAndAlternateOrCompletedAlerts() {
        val old = release("old")
        val next = release("next", "2")
        val newEdition = old.copy(id = "alternate")
        assertTrue(LibraryUpdateDiff.chapters(null, listOf(old, next), emptySet()).isEmpty())
        assertEquals(listOf(next), LibraryUpdateDiff.chapters(listOf(old), listOf(newEdition, next, next), emptySet()))
        assertTrue(LibraryUpdateDiff.chapters(listOf(old), listOf(next), setOf(next.key)).isEmpty())
        assertTrue(LibraryUpdateDiff.chapters(listOf(old), listOf(next.copy(unavailable = true)), emptySet()).isEmpty())
    }

    @Test fun verificationNeverAllowsHostSuffixConfusionOrNonHttps() {
        assertTrue(allowsVerificationNavigation("nhentai", "https://nhentai.net/g/123/", true))
        assertTrue(allowsVerificationNavigation("nhentai", "https://www.nhentai.net/", true))
        assertFalse(allowsVerificationNavigation("nhentai", "https://nhentai.net.attacker.test/", true))
        assertFalse(allowsVerificationNavigation("nhentai", "http://nhentai.net/", true))
        assertFalse(allowsVerificationNavigation("nhentai", "intent://nhentai.net/", true))
        assertFalse(allowsVerificationNavigation("nhentai", "https://challenges.cloudflare.com/", true))
        assertTrue(allowsVerificationNavigation("nhentai", "https://challenges.cloudflare.com/", false))
        assertFalse(allowsVerificationNavigation("unknown", "https://nhentai.net/", true))
    }

    @Test fun downloadRangesUseReadingOrderAndDoNotShiftPastExistingChapters() {
        val rows = (1..6).map { release("c$it", "$it") }.reversed()
        assertEquals(listOf("c2", "c4"), DownloadPolicy.range(rows, mapOf("c3" to "Complete"), 2, 4).map { it.id })
        assertTrue(DownloadPolicy.range(rows, emptyMap(), 0, 4).isEmpty())
        assertTrue(DownloadPolicy.range(rows, emptyMap(), 4, 3).isEmpty())
        assertTrue(DownloadPolicy.range(rows, emptyMap(), 1, 7).isEmpty())
        assertEquals(listOf("c4"), DownloadPolicy.range(rows, mapOf("c2" to "Waiting for storage", "c3" to "Queued"), 2, 4).map { it.id })
    }

    @Test fun updateChannelsAndPersistentCooldownAreConservative() {
        assertFalse(UpdatePolicy.acceptsChannel("v1.0.0-alpha.1", true, "stable"))
        assertTrue(UpdatePolicy.acceptsChannel("v1.0.0-alpha.1", true, "preview"))
        assertTrue(UpdatePolicy.acceptsChannel("v1.0.0", false, "stable"))
    }
}
