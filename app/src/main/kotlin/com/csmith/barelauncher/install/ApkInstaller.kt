package com.csmith.barelauncher.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

private const val BUFFER_SIZE = 64 * 1024

class ApkInstaller(private val context: Context, private val client: OkHttpClient, private val store: InstallStore) {
    private val packageInstaller = context.packageManager.packageInstaller

    suspend fun install(id: String, url: HttpUrl) = withContext(Dispatchers.IO) {
        val apk = File(context.cacheDir, "install-$id.apk")
        try {
            download(url, apk) { store.progress(id, it) }
            store.update(id) { it.copy(phase = InstallPhase.Verifying, fraction = null) }
            val info = readApkInfo(context, apk)
            store.update(id) { it.copy(
                title = info.label, icon = info.icon, packageName = info.packageName,
                isUpdate = info.isUpdate, phase = InstallPhase.Installing,
            ) }

            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(info.packageName)
                setAppLabel(info.label)
                setAppIcon(info.icon)
                setSize(apk.length())
            }
            val sessionId = packageInstaller.createSession(params)
            packageInstaller.openSession(sessionId).use { session ->
                try {
                    session.openWrite("base.apk", 0, apk.length()).use { out ->
                        apk.inputStream().use { input -> input.copyTo(out, BUFFER_SIZE) }
                        session.fsync(out)
                    }
                    currentCoroutineContext().ensureActive()
                    session.commit(resultReceiver(id, sessionId))
                } catch (e: Exception) {
                    session.abandon()
                    throw e
                }
            }
        } finally {
            apk.delete()
        }
    }

    private suspend fun download(url: HttpUrl, apk: File, onProgress: (Float?) -> Unit) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body
            val total = body.contentLength()
            apk.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var written = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        written += read
                        onProgress(if (total > 0) written.toFloat() / total else null)
                    }
                    if (total >= 0 && written != total) throw IOException("The download was incomplete.")
                }
            }
        }
    }

    private fun resultReceiver(id: String, sessionId: Int): IntentSender {
        val intent = Intent(context, InstallActivity::class.java)
            .putExtra(InstallActivity.EXTRA_INSTALL_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        ).intentSender
    }
}
