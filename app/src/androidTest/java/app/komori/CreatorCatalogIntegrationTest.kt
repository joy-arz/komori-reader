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
