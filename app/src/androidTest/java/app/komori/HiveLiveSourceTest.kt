package app.komori

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import app.komori.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit opt-in: no external service is contacted by ordinary CI. No image bytes downloaded. */
class HiveLiveSourceTest {
    @Test fun canonicalHttpsCatalogDetailsChaptersAndPageManifest() = runBlocking<Unit> {
        assumeTrue("Explicit live Hive acceptance", InstrumentationRegistry.getArguments().getString("liveHive") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KomoriApp
        withTimeout(90000) {
            val catalog = app.source.browse("hive", "crocodile", 1, BrowseFilter(), null)
            assertTrue("Hive catalog returned no titles", catalog.titles.isNotEmpty())
            val book = app.source.details(catalog.titles.first())
            assertTrue(book.metadata().any { it.type == "tag" })
            val chapters = app.source.chapters(book.id, "en")
            val public = chapters.first { it.release().readable }
            val pages = app.source.pages(public.id, false)
            assertTrue(pages.isNotEmpty())
            assertTrue(pages.all { it.startsWith("https://") })
            Log.i("KomoriHive", "PASS: catalog=${catalog.titles.size}, genres=${book.metadata().count { it.type == "tag" }}, chapters=${chapters.size}, pages=${pages.size}; imageBytes=0")
        }
    }
}
