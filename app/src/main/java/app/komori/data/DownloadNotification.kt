package app.komori.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import app.komori.MainActivity

internal fun downloadForegroundInfo(context: Context, title: String, done: Int = 0, total: Int = 0): ForegroundInfo {
    val channel = "chapter-downloads"
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(channel, "Chapter downloads", NotificationManager.IMPORTANCE_LOW))
    val open = PendingIntent.getActivity(context, 41,
        Intent(context, MainActivity::class.java).putExtra("showDownloads", true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val notification = NotificationCompat.Builder(context, channel)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(title).setContentText(if (total > 0) "$done / $total pages" else "Preparing chapter…")
        .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
        .setProgress(total.coerceAtLeast(0), done.coerceAtLeast(0), total <= 0)
        .build()
    return ForegroundInfo(4101, notification, if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
}
