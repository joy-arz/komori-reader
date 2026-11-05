package app.komori

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import app.komori.data.*
import app.komori.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CreatorCatalogIntegrationTest {
    @Test fun allWith120CreatorsPublishesIncrementallyAndReturnsFromAnAuthorWithoutRefetching() = runBlocking<Unit> {
        val app = compose.activity.application as KomoriApp
        val creators = (1..120).map { Creator(sourceId("nhentai", "/author/fast-$it"), "Creator $it", "author") }
        val slow = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        lateinit var vm: ReaderViewModel
        compose.runOnUiThread {
            vm = ReaderViewModel(app) { creator, _, _ ->
                calls += creator.id
                if (creator == creators.first()) slow.await()
                BrowsePage(listOf(Manga(sourceId("nhentai", "/gallery/${creators.indexOf(creator)}"), creator.name, "", "")), false)
            }
            vm.showAuthorWorks(creators)
        }
        try {
            withTimeout(5000) { vm.authorFeed.first { it.loading && it.items.any { title -> title.title == "Creator 2" } } }
            assertTrue(calls.size <= 3)
            compose.runOnUiThread { vm.showAuthorWorks(listOf(creators[79])) }
            withTimeout(5000) { vm.authorFeed.first { !it.loading && it.context == listOf(creators[79].id) } }
            val count = calls.size
            compose.runOnUiThread { vm.showAuthorWorks(creators) }
            assertFalse(vm.authorFeed.value.loading)
            assertTrue(vm.authorFeed.value.items.any { it.title == "Creator 2" })
            assertEquals(count, calls.size)
            slow.complete(Unit)
            compose.runOnUiThread { vm.refreshAuthors() }
            withTimeout(5000) { vm.authorFeed.first { !it.loading && it.items.any { title -> title.title == "Creator 1" } } }
            assertTrue(calls.size > count)
        } finally { compose.runOnUiThread { vm.viewModelScope.cancel() } }
    }

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun asuraAndBothAtsumaruCatalogsReachCreatorProviderWithNativeRanking() = runBlocking<Unit> {
        val app = compose.activity.application as KomoriApp
        lateinit var vm: ReaderViewModel
        compose.runOnUiThread {
            vm = ReaderViewModel(app) { creator, page, filter ->
                val key = sourceKey(creator.id)
                assertEquals(1, page)
                assertTrue(filter.sort in app.source.sorts(key))
                BrowsePage(listOf(Manga(sourceId(key, "/manga/creator-fixture"), filter.sort, "", "")), false)
            }
        }
        try {
            for (key in listOf("asura", "atsu", "atsuadult")) {
                val creator = Creator(sourceId(key, "/author/fixture"), "Fixture author", "author")
                for (sort in listOf(AuthorSort.LATEST, AuthorSort.POPULAR)) {
                    compose.runOnUiThread { vm.showAuthorWorks(listOf(creator), sort) }
                    val feed = withTimeout(5000) { vm.authorFeed.first { it.context == listOf(creator.id) && it.sort == sort && !it.loading } }
                    assertTrue(feed.errors.toString(), feed.errors.isEmpty())
                    assertEquals(listOf(sort.sourceSort(key)), feed.items.map { it.title })
                    assertEquals(key, sourceKey(feed.items.single().id))
                }
            }
        } finally { compose.runOnUiThread { vm.viewModelScope.cancel() } }
    }
}
