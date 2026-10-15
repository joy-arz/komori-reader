package app.komori

import android.app.Application
import androidx.room.Room
import app.komori.data.*
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache

class KomoriApp : Application(), ImageLoaderFactory {
    val database by lazy {
        Room.databaseBuilder(this, LibraryDatabase::class.java, "komori.db")
            .addMigrations(SCROLL_MIGRATION, READER_MODE_MIGRATION, FEED_MIGRATION, METADATA_MIGRATION)
            .build()
    }
    val dao
        get() = database.dao()

    val settings by lazy { SettingsStore(this) }
    val sessions by lazy { SourceSessions(this) }
    val source by lazy { Sources(sessions.client()) }

    override fun newImageLoader() =
        ImageLoader.Builder(this)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.12).build() }
            .okHttpClient(source.client)
            .crossfade(false)
            .build()
}
