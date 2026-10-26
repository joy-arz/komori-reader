package app.komori.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import androidx.work.*
import app.komori.KomoriApp
import app.komori.MainActivity
import app.komori.R
import app.komori.domain.ChapterPolicy
import app.komori.domain.ChapterPreferences
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.json.JSONArray

object LibraryCheckScheduler {
    fun schedule(context: Context, settings: Settings) {
        val manager = WorkManager.getInstance(context)
        if (settings.libraryCheckHours == 0) {
            manager.cancelUniqueWork("library-periodic-check")
            manager.cancelAllWorkByTag("library-check-continuation")
            return
        }
        val request = PeriodicWorkRequestBuilder<LibraryCheckWorker>(settings.libraryCheckHours.toLong(), TimeUnit.HOURS)
            .setInitialDelay(settings.libraryCheckHours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints(settings)).build()
        manager.enqueueUniquePeriodicWork("library-periodic-check", ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun now(context: Context, settings: Settings) = WorkManager.getInstance(context)
        .enqueueUniqueWork("library-check-now", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<LibraryCheckWorker>().setInputData(workDataOf("manual" to true))
                .addTag("library-manual-refresh").setConstraints(constraints(settings)).build())

    internal fun constraints(settings: Settings) = Constraints.Builder()
        .setRequiredNetworkType(if (settings.checkWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true).build()
}

/** At most fifty titles per work request; continuations retain their cursor in WorkManager. */
class LibraryCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as KomoriApp
        val settings = app.settings.flow.first()
        val manual = inputData.getBoolean("manual", false)
        if (!manual && settings.libraryCheckHours == 0) return Result.success()
        val after = inputData.getString("after").orEmpty()
        val cycle = inputData.getString("cycle") ?: "$id-${System.currentTimeMillis() / TimeUnit.HOURS.toMillis(12)}"
        val books = app.dao.observeShelf().first().filter { it.saved && sourceKey(it.mangaId) in enabledSourceOrder(settings) }.map { it.mangaId }.sorted().filter { it > after }
        var checked = 0
        var failed = 0
        var cursor = after
        val start = System.nanoTime()
        for (bookId in books.take(50)) {
            if (checked > 0 && System.nanoTime() - start > TimeUnit.MINUTES.toNanos(6)) break
            try {
                val pref = app.dao.titlePreferences(bookId)
                val language = sourceReadingLanguage(bookId, settings.language, pref?.language.orEmpty())
                val oldFeed = app.dao.feed(bookId, language)
                val oldIds = oldFeed?.let { JSONArray(it.releaseIds).let { rows -> (0 until rows.length()).map { rows.getString(it) }.toSet() } }
                val old = oldIds?.let { ids -> app.dao.chapters(bookId).filter { it.id in ids }.map { it.release() } }
                val fresh = app.source.chapters(bookId, language)
                val selected = ChapterPolicy.select(fresh.map { it.release() }, ChapterPreferences(language, settings.smart, pref?.groupId.orEmpty(), pref?.strict ?: false))
                val completed = app.dao.observeProgressForManga(bookId).first().filter { it.completed }.map { it.chapterKey }.toSet()
                val added = LibraryUpdateDiff.chapters(old, selected, completed)
                app.database.withTransaction {
                    app.dao.saveFeed(bookId, language, fresh)
                    app.dao.putLibraryUpdates(added.map { LibraryUpdate(bookId, it.id, it.label, System.currentTimeMillis()) })
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failed++; app.settings.checkStatus("Some titles could not refresh: ${connectionMessage(e)}") }
            cursor = bookId
            checked++
        }
        if (books.size > checked) {
            val continuation = OneTimeWorkRequestBuilder<LibraryCheckWorker>()
                .setInputData(workDataOf("after" to cursor, "cycle" to cycle, "manual" to manual))
                .setConstraints(LibraryCheckScheduler.constraints(settings))
                .addTag(if (manual) "library-manual-continuation" else "library-check-continuation")
                .addTag(if (manual) "library-manual-refresh" else "library-scheduled-refresh").build()
            WorkManager.getInstance(app).enqueueUniqueWork("library-check-$cycle-$cursor", ExistingWorkPolicy.KEEP, continuation).result.get()
            app.settings.checkStatus("Chapter checks continuing · ${if (failed > 0) "$failed refresh errors in this batch" else "next batch queued"}")
        } else {
            if (settings.checkAuthors) failed += checkAuthors(app)
            app.settings.checkStatus(if (failed == 0) "Checks completed · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())}"
                else "Checks completed with $failed refresh errors in the last batch. Open affected sources to retry.")
        }
        app.dao.trimUpdates(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90))
        publishNotification(app, settings)
        return Result.success()
    }

    private suspend fun checkAuthors(app: KomoriApp): Int {
        val enabled = enabledSourceOrder(app.settings.flow.first())
        val creators = app.dao.observeCreators().first().filter { sourceKey(it.id) in enabled }.map { it to app.dao.creatorSnapshot(it.id) }.sortedBy { it.second?.checkedAt ?: 0 }.take(3)
        var errors = 0
        for ((creator, old) in creators) {
            try {
                val page = app.source.browse(sourceKey(creator.id), "", 1, BrowseFilter(sort = if (sourceKey(creator.id) == "mangadex") "Latest updates" else "Latest"), creator)
                val previous = old?.let { JSONArray(it.titleIds).let { rows -> (0 until rows.length()).map { rows.getString(it) }.toSet() } }
                val added = LibraryUpdateDiff.works(previous, page.titles)
                app.database.withTransaction {
                    page.titles.forEach { title ->
                        val existing = app.dao.manga(title.id)
                        if (existing == null) app.dao.putManga(title)
                    }
                    app.dao.putLibraryUpdates(added.map { LibraryUpdate(it.id, creator.id, "New work from ${creator.name}", System.currentTimeMillis(), "author") })
                    val ids = (page.titles.map { it.id } + previous.orEmpty()).distinct().take(512)
                    app.dao.putCreatorSnapshot(CreatorSnapshot(creator.id, JSONArray(ids).toString(), System.currentTimeMillis()))
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { errors++ }
        }
        return errors
    }

    private suspend fun publishNotification(app: KomoriApp, settings: Settings) {
        if (!settings.notifyChapterUpdates) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val updates = app.dao.observeLibraryUpdates().first().count { !it.seen }
        if (updates == 0) return
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("library-updates", "Library updates", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(app, 81, Intent(app, MainActivity::class.java).putExtra("showUpdates", true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(81, NotificationCompat.Builder(app, "library-updates").setSmallIcon(R.drawable.ic_stat_updates)
            .setContentTitle("Komori Library updates").setContentText("$updates new chapters or author works. Tap to review.")
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).build())
    }
}

object LibraryUpdateDiff {
    fun chapters(previous: List<app.komori.domain.Release>?, fresh: List<app.komori.domain.Release>, completed: Set<String>): List<app.komori.domain.Release> {
        if (previous == null) return emptyList()
        val known = previous.filter { it.readable }.map { it.key }.toSet()
        return fresh.filter { it.readable && it.key !in known && it.key !in completed }.distinctBy { it.key }
    }

    fun works(previous: Set<String>?, fresh: List<Manga>): List<Manga> =
        if (previous == null) emptyList() else fresh.filter { it.id !in previous }.distinctBy { it.id }
}
