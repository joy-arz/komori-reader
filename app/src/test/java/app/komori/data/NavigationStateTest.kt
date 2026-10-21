package app.komori.data

import org.junit.Assert.*
import org.junit.Test

class NavigationStateTest {
    @Test fun restoresIdentifiersAndFiltersWithoutCachingResultOrImagePayloads() {
        val state = NavigationState(1, "zap", "name", BrowseFilter("Popular", setOf("/tag/one/"), "Author", "/artist/name/", "en", "m"),
            sourceId("zap", "/gallery/1/"), sourceId("zap", "/gallery/1/"), 12,
            Creator(sourceId("zap", "/artist/name/"), "Name", "artist"))
        assertEquals(state, NavigationState.decode(state.encode()))
        assertTrue(state.encode().length < 1024)
    }

    @Test fun damagedOrOversizedStateFallsBackWithoutBreakingLaunch() {
        assertNull(NavigationState.decode("not json"))
        assertNull(NavigationState.decode("x".repeat(32769)))
        assertNull(NavigationState.decode(null))
    }
}
