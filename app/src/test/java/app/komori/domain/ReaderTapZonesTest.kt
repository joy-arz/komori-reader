package app.komori.domain

import org.junit.Assert.*
import org.junit.Test

class ReaderTapZonesTest {
    @Test fun onlyTheTwoDimensionalCenterRevealsControls() {
        assertTrue(ReaderTapZones.isControlTap(.5f, .5f))
        for ((x, y) in listOf(.5f to .1f, .5f to .9f, .1f to .5f, .9f to .5f, .31f to .5f, .5f to .69f)) {
            assertFalse("Outside center: $x, $y", ReaderTapZones.isControlTap(x, y))
        }
    }

    @Test fun edgesRemainPageTurnsAndInvalidPositionsCannotRevealControls() {
        for (mode in listOf(ReadingMode.LTR, ReadingMode.RTL)) {
            assertNotNull(mode.tapDelta(.1f))
            assertNotNull(mode.tapDelta(.9f))
            assertNull(mode.tapDelta(.5f))
        }
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, -.1f, 1.1f)) {
            assertFalse(ReaderTapZones.isControlTap(value, .5f))
            assertFalse(ReaderTapZones.isControlTap(.5f, value))
        }
    }
}
