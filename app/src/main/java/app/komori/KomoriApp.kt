package app.komori

import android.app.Application
import androidx.room.Room
import app.komori.data.*
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import app.komori.ui.CoverFallbackInterceptor
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class KomoriApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default).launch {
            val current = settings.flow.first()
            LibraryCheckScheduler.schedule(this@KomoriApp, current)
            BackupScheduler.schedule(this@KomoriApp, current)
            kickDownloadQueue(this@KomoriApp)
        }
    }

    val database by lazy {
        Room.databaseBuilder(this, LibraryDatabase::class.java, "komori.db")
            .addMigrations(SCROLL_MIGRATION, READER_MODE_MIGRATION, FEED_MIGRATION, METADATA_MIGRATION, ORGANIZATION_MIGRATION)
            .build()
    }
    val dao
        get() = database.dao()

    val settings by lazy { SettingsStore(this) }
    val sessions by lazy { SourceSessions(this) }
    val source by lazy { Sources(sessions.client(), MangaDex.client()) }

    override fun newImageLoader() =
        ImageLoader.Builder(this)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.12).build() }
            .okHttpClient(source.client)
            .components { add(CoverFallbackInterceptor(source.coverRecovery)) }
            .crossfade(false)
            .build()
}
