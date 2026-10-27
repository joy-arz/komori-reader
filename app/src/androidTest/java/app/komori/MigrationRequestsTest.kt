package app.komori

import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import app.komori.data.*
import app.komori.ui.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class MigrationRequestsTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KomoriApp
    private class Requests : MigrationRequests {
        val searches = mutableListOf<CompletableDeferred<BrowsePage>>()
        val selections = mutableListOf<CompletableDeferred<Manga>>()
        override suspend fun browse(source: String, query: String, page: Int): BrowsePage {
            val result = CompletableDeferred<BrowsePage>(); searches += result
            return withContext(NonCancellable) { result.await() }
        }
        override suspend fun details(candidate: Manga): Manga {
            val result = CompletableDeferred<Manga>(); selections += result
            return withContext(NonCancellable) { result.await() }
        }
        override suspend fun chapters(id: String, language: String): List<Chapter> = emptyList()
    }
    private val original = Manga("old-manga", "Original", "", "")
    private val first = Manga(sourceId("zap", "/gallery/11/"), "First", "", "")
    private val second = Manga(sourceId("zap", "/gallery/12/"), "Second", "", "")
    private suspend fun until(block: () -> Boolean) = withTimeout(5000) { while (!block()) delay(10) }

    @Test fun editsSourceChangesObsoleteErrorsAndReopeningCannotPublishOldSearch() = runBlocking<Unit> {
        val fake = Requests(); val vm = withContext(Dispatchers.Main) { MigrationViewModel(app, fake, Unit) }
        suspend fun search(query: String, source: String = "zap") = withContext(Dispatchers.Main) { vm.edit(query, source); vm.search() }
        try {
            withContext(Dispatchers.Main) { vm.begin(original) }
            search("first"); until { fake.searches.size == 1 }
            search("latest"); until { fake.searches.size == 2 }
            fake.searches[1].complete(BrowsePage(listOf(second), false))
            until { vm.state.value.results == listOf(second) }
            fake.searches[0].complete(BrowsePage(listOf(first), false)); delay(50)
            assertEquals("latest", vm.state.value.query); assertEquals(listOf(second), vm.state.value.results)
            search("obsolete"); until { fake.searches.size == 3 }
            withContext(Dispatchers.Main) { vm.edit("nhentai", "nhentai") }
            fake.searches[2].completeExceptionally(IOException("obsolete secret error")); delay(50)
            assertEquals("nhentai", vm.state.value.sourceKey); assertNull(vm.state.value.error); assertTrue(vm.state.value.results.isEmpty())
            search("closed"); until { fake.searches.size == 4 }
            withContext(Dispatchers.Main) { vm.close(); vm.begin(original) }
            fake.searches[3].complete(BrowsePage(listOf(first), false)); delay(50)
            assertTrue(vm.state.value.results.isEmpty()); assertEquals(original.title, vm.state.value.query)
        } finally { fake.searches.forEach { it.complete(BrowsePage(emptyList(), false)) }; withContext(Dispatchers.Main) { vm.close(); vm.viewModelScope.cancel() } }
    }

    @Test fun staleDestinationDetailsCannotBecomeApplicableAfterSelectionOrContextChanges() = runBlocking<Unit> {
        val fake = Requests(); val vm = withContext(Dispatchers.Main) { MigrationViewModel(app, fake, Unit) }
        var applied = false
        try {
            withContext(Dispatchers.Main) { vm.begin(original); vm.edit("", "zap"); vm.search() }
            until { fake.searches.size == 1 }; fake.searches[0].complete(BrowsePage(listOf(first, second), false))
            until { !vm.state.value.loading }
            withContext(Dispatchers.Main) { vm.select(first) }; until { fake.selections.size == 1 }
            withContext(Dispatchers.Main) { vm.select(second) }; until { fake.selections.size == 2 }
            fake.selections[1].complete(second); until { vm.state.value.selected == second }
            fake.selections[0].complete(first); delay(50)
            assertEquals(second, vm.state.value.selected)
            withContext(Dispatchers.Main) { vm.edit("another", "zap"); vm.apply(true, false) { applied = true } }
            assertFalse(applied); assertNull(vm.state.value.selected); assertTrue(vm.state.value.targets.isEmpty()); assertTrue(vm.state.value.plan.isEmpty())
            withContext(Dispatchers.Main) { vm.select(second); vm.apply(true, false) { applied = true } }
            assertFalse(applied)
        } finally { fake.searches.forEach { it.complete(BrowsePage(emptyList(), false)) }; fake.selections.forEach { it.complete(first) }; withContext(Dispatchers.Main) { vm.close(); vm.viewModelScope.cancel() } }
    }
}
