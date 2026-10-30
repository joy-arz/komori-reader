package app.komori.data

import org.junit.Assert.*
import org.junit.Test

class SourceConsentPolicyTest {
    @Test fun freshInstallsEnableOnlyGeneralSources() {
        val initial = SourceConsentPolicy.initial(null, false, null)
        assertEquals("demonic,weeb", initial.enabled)
        assertFalse(initial.confirmed)
        assertTrue(SourceConsentPolicy.adultSources.none { it in initial.enabled.split(',') })
    }
    @Test fun implicitLegacyDefaultsStayEnabledOnUpgrade() {
        val initial = SourceConsentPolicy.initial(null, true, null)
        assertEquals(sourceNames.keys.joinToString(","), initial.enabled)
        assertTrue(initial.confirmed)
    }
    @Test fun explicitUpgradeChoicesAreNeverReplacedByDefaults() {
        for (sources in listOf("", "weeb", "demonic,nhentai", "manhwa18cc")) {
            val initial = SourceConsentPolicy.initial(sources, true, null)
            assertEquals(sources, initial.enabled)
            assertEquals(sources.split(',').any { it in SourceConsentPolicy.adultSources }, initial.confirmed)
        }
    }
    @Test fun confirmationIsGlobalPersistentAndNeverNeededToDisable() {
        for (source in sourceNames.keys) {
            assertEquals(source in SourceConsentPolicy.adultSources, SourceConsentPolicy.needsConfirmation(source, true, false))
            assertFalse(SourceConsentPolicy.needsConfirmation(source, true, true))
            assertFalse(SourceConsentPolicy.needsConfirmation(source, false, false))
        }
        assertTrue(SourceConsentPolicy.initial("demonic", false, true).confirmed)
    }
}
