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
import java.io.IOException

private const val BUFFER_SIZE = 64 * 1024

/**
 * Streams an APK from a URL straight into a PackageInstaller session, so nothing is
 * staged on disk first. The system reports the outcome to [InstallActivity].
 */
class ApkInstaller(private val context: Context, private val client: OkHttpClient) {
    private val packageInstaller = context.packageManager.packageInstaller

    suspend fun install(url: HttpUrl, onProgress: (Float?) -> Unit) = withContext(Dispatchers.IO) {
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = packageInstaller.createSession(params)
        packageInstaller.openSession(sessionId).use { session ->
            try {
                download(url, session, onProgress)
            } catch (e: Exception) {
                session.abandon()
                throw e
            }
            session.commit(resultReceiver(sessionId))
        }
    }

    private suspend fun download(
        url: HttpUrl,
        session: PackageInstaller.Session,
        onProgress: (Float?) -> Unit,
    ) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body
            // -1 when the server doesn't say, which openWrite also accepts.
            val total = body.contentLength()
            session.openWrite("base.apk", 0, total).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var written = 0L
                    while (true) {
                        // Leaving the install screen cancels the download.
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        written += read
                        onProgress(if (total > 0) written.toFloat() / total else null)
                    }
                }
                session.fsync(out)
            }
        }
    }

    private fun resultReceiver(sessionId: Int): IntentSender {
        val intent = Intent(context, InstallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Mutable so the installer can attach its status extras.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getActivity(context, sessionId, intent, flags).intentSender
    }
}
