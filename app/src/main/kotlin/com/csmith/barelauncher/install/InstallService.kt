package com.csmith.barelauncher.install

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.csmith.barelauncher.BareLauncherApp
import com.csmith.barelauncher.R
import com.csmith.barelauncher.home.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl

private const val CHANNEL_ID = "app-installs"
private const val NOTIFICATION_ID = 1

/** Keeps TV downloads alive when the URL screen closes or another app is opened. */
class InstallService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as BareLauncherApp
    private val store get() = app.installs

    override fun onCreate() {
        super.onCreate()
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, getString(R.string.install_notifications), NotificationManager.IMPORTANCE_LOW,
        ))
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        scope.launch {
            store.tasks.collect { tasks ->
                val active = tasks.firstOrNull { it.origin == InstallOrigin.Tv && it.phase.isBusy }
                if (active == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    notifications.notify(NOTIFICATION_ID, notification(active))
                    delay(250)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val request = requireNotNull(intent)
        val id = requireNotNull(request.getStringExtra(EXTRA_ID))
        val url = requireNotNull(request.getStringExtra(EXTRA_URL)).toHttpUrl()
        scope.launch {
            try {
                ApkInstaller(this@InstallService, app.http, store).install(id, url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val phase = store.tasks.value.find { it.id == id }?.phase
                val error = when {
                    e is InvalidApkException -> InstallError.InvalidApk
                    phase == InstallPhase.Downloading -> InstallError.DownloadFailed
                    else -> InstallError.InstallFailed
                }
                store.fail(id, error, e.message)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun notification(task: InstallTask?): Notification {
        val openHome = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(task?.title ?: getString(R.string.install_notifications))
            .setContentText(getString(task?.phase?.message ?: R.string.status_downloading))
            .setProgress(100, ((task?.fraction ?: 0f) * 100).toInt(), task?.fraction == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openHome)
            .build()
    }

    companion object {
        const val EXTRA_ID = "install_id"
        const val EXTRA_URL = "url"
    }
}
