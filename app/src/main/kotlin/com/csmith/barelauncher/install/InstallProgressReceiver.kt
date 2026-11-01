package com.csmith.barelauncher.install

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import com.csmith.barelauncher.BareLauncherApp
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File

private enum class ReportResult(val code: Int) {
    Accepted(Activity.RESULT_OK),
    Rejected(Activity.RESULT_CANCELED),
    InvalidApk(Activity.RESULT_FIRST_USER),
}

/** Protected by android.permission.DUMP: only ADB/system callers can report CLI installs. */
class InstallProgressReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        resultCode = ReportResult.Rejected.code
        try {
            val id = requireNotNull(intent.getStringExtra("install_id"))
            require(id.matches(Regex("[A-Za-z0-9_.-]+")))
            val encodedUrl = requireNotNull(intent.getStringExtra("url_base64"))
            val url = requireNotNull(String(Base64.decode(encodedUrl, Base64.DEFAULT), Charsets.UTF_8).toHttpUrlOrNull())
            val phase = InstallPhase.valueOf(requireNotNull(intent.getStringExtra("phase")))
            val percent = if (intent.hasExtra("percent")) {
                intent.getIntExtra("percent", -1).also { require(it in 0..100) }
            } else null
            val error = intent.getStringExtra("error")?.let(InstallError::valueOf)
            require(phase != InstallPhase.Failed || error != null)
            val store = (context.applicationContext as BareLauncherApp).installs
            val staging = File(requireNotNull(context.getExternalFilesDir("installs")), "$id.apk")
            if (phase == InstallPhase.Verifying) {
                val info = try {
                    readApkInfo(context, staging)
                } catch (e: InvalidApkException) {
                    store.fail(id, InstallError.InvalidApk)
                    resultCode = ReportResult.InvalidApk.code
                    return
                }
                store.reportCli(id, url, phase, percent, error)
                store.update(id) { it.copy(title = info.label, icon = info.icon, packageName = info.packageName, isUpdate = info.isUpdate) }
            } else {
                store.reportCli(id, url, phase, percent, error)
            }
            resultData = staging.path
            resultCode = ReportResult.Accepted.code
        } catch (e: IllegalArgumentException) {
            Log.e("InstallProgress", "Invalid CLI progress report", e)
        }
    }
}
