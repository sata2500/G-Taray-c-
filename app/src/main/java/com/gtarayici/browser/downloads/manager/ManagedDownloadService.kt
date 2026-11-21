package com.gtarayici.browser.downloads.manager

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.gtarayici.browser.MainActivity
import com.gtarayici.browser.R
import com.gtarayici.browser.components
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * İndirmeler sürerken uygulamayı arka planda canlı tutar ve ilerlemeyi bildirimde gösterir
 * ("2 indirme · %45 · 3,2 MB/sn"). Biten veya başarısız olan her indirme için ayrı bildirim çıkar.
 */
class ManagedDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null
    private val notifications by lazy { NotificationManagerCompat.from(this) }
    private val announced = HashSet<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL, getString(R.string.dm_channel), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            FOREGROUND_ID,
            progress(emptyList()),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (watcher == null) {
            val queue = components.downloadQueue
            // Zaten bitmiş olanlar yeniden duyurulmaz.
            queue.tasks.value.filter { it.status == TaskStatus.COMPLETED || it.status == TaskStatus.FAILED }.forEach { announced += it.id }
            watcher = scope.launch {
                queue.tasks.collect { tasks ->
                    tasks.filter { (it.status == TaskStatus.COMPLETED || it.status == TaskStatus.FAILED) && announced.add(it.id) }
                        .forEach(::finished)
                    val active = tasks.filter { it.status == TaskStatus.RUNNING || it.status == TaskStatus.QUEUED }
                    if (active.isEmpty()) {
                        ServiceCompat.stopForeground(this@ManagedDownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        notify(FOREGROUND_ID, progress(active))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun openDownloads(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_DOWNLOADS),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun progress(active: List<DownloadTask>) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(
            when (active.size) {
                0 -> getString(R.string.dm_preparing)
                1 -> active.first().name
                else -> resources.getQuantityString(R.plurals.dm_active, active.size, active.size)
            },
        )
        .setContentText(summary(active))
        .apply {
            val total = active.sumOf { it.total.coerceAtLeast(0) }
            val done = active.sumOf { it.done }
            val fraction = active.singleOrNull()?.progress ?: if (total > 0) done.toFloat() / total else null
            if (fraction != null) setProgress(100, (fraction * 100).toInt(), false) else setProgress(0, 0, true)
        }
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openDownloads())
        .build()

    /** "%45 · 3,2 MB/sn · yaklaşık 2 dk kaldı" */
    private fun summary(active: List<DownloadTask>): String {
        if (active.isEmpty()) return ""
        val speed = active.sumOf { it.speed }
        val single = active.singleOrNull()
        val parts = listOfNotNull(
            single?.progress?.let { "%${(it * 100).toInt()}" },
            speed.takeIf { it > 0 }?.let { getString(R.string.dm_speed, Formatter.formatShortFileSize(this, it)) },
            single?.remainingMs?.let { com.gtarayici.browser.ui.feedback.ProgressEstimator.remainingText(it).resolve(this) },
        )
        return parts.joinToString(" · ")
    }

    private fun finished(task: DownloadTask) {
        val ok = task.status == TaskStatus.COMPLETED
        val text = if (ok) {
            getString(R.string.dm_done, Formatter.formatShortFileSize(this, task.done))
        } else {
            com.gtarayici.browser.ui.feedback.HumanErrors.message(com.gtarayici.browser.ui.feedback.ErrorArea.FileDownload, task.error).resolve(this)
        }
        notify(
            task.id.hashCode(),
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
                .setContentTitle(task.name)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(openDownloads())
                .build(),
        )
    }

    @SuppressLint("MissingPermission")
    private fun notify(id: Int, notification: android.app.Notification) {
        runCatching { notifications.notify(id, notification) }
    }

    private companion object {
        const val CHANNEL = "managed_downloads"
        const val FOREGROUND_ID = 7311
    }
}
