package app.komori.data

import org.junit.Assert.*
import org.junit.Test

class DownloadPresentationTest {
    @Test fun stoppedNativeWorkDoesNotClaimToBeActivelyTransferring() {
        val row = Download("chapter", "book", "One", 3, 1, "Downloading")
        assertEquals("Waiting for Android", downloadDisplayState(row, DownloadEnvironment(true, true), true, false))
        assertEquals("Waiting for network", downloadDisplayState(row, DownloadEnvironment(), true, false))
        assertEquals("Waiting for Wi-Fi", downloadDisplayState(row, DownloadEnvironment(true, false), true, false))
        assertEquals("Downloading", downloadDisplayState(row, DownloadEnvironment(true, true), true, true))
        assertEquals("Paused", downloadDisplayState(row.copy(state = "Paused"), DownloadEnvironment(true, true), true, false))
    }
}
