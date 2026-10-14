package app.komori.domain

import org.junit.Assert.*
import org.junit.Test

class ReadingModeTest {
    @Test
    fun titleOverridesDoNotChangeWithDefaults() {
        assertEquals(ReadingMode.RTL, ReadingMode.resolve("rtl", true, false))
        assertEquals(ReadingMode.LTR, ReadingMode.resolve("ltr", true, true))
        assertEquals(ReadingMode.WEBTOON, ReadingMode.resolve("webtoon", false, false))
    }

    @Test
    fun missingAndUnknownModesFallBackSafely() {
        assertEquals(ReadingMode.WEBTOON, ReadingMode.resolve("", true, false))
        assertEquals(ReadingMode.RTL, ReadingMode.resolve(null, false, true))
        assertEquals(ReadingMode.LTR, ReadingMode.resolve("future", false, false))
    }

    @Test
    fun edgeTapsRespectDirectionAndCenterIsReserved() {
        assertEquals(-1, ReadingMode.LTR.tapDelta(0.1f))
        assertEquals(1, ReadingMode.LTR.tapDelta(0.9f))
        assertEquals(1, ReadingMode.RTL.tapDelta(0.1f))
        assertEquals(-1, ReadingMode.RTL.tapDelta(0.9f))
        assertNull(ReadingMode.LTR.tapDelta(0.5f))
        assertNull(ReadingMode.RTL.tapDelta(0.5f))
    }

    @Test
    fun swipesFollowReadingDirection() {
        assertEquals(1, ReadingMode.LTR.swipeDelta(-100f))
        assertEquals(-1, ReadingMode.LTR.swipeDelta(100f))
        assertEquals(1, ReadingMode.RTL.swipeDelta(100f))
        assertEquals(-1, ReadingMode.RTL.swipeDelta(-100f))
        assertEquals(0, ReadingMode.RTL.swipeDelta(0f))
    }
}
