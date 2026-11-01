package com.csmith.barelauncher.install

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.csmith.barelauncher.BareLauncherApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

class InstallViewModel(application: Application) : AndroidViewModel(application) {
    private val app = getApplication<BareLauncherApp>()
    private val _error = MutableStateFlow<InstallError?>(null)
    val error = _error.asStateFlow()
    private var submitted = false

    /** The URL screen closes once the foreground download service has started. */
    fun install(link: String): Boolean {
        if (submitted) return false
        val url = link.trim().toHttpUrlOrNull()
        if (url == null) {
            _error.value = InstallError.InvalidUrl
            return false
        }
        if (!app.packageManager.canRequestPackageInstalls()) {
            _error.value = InstallError.InstallsNotAllowed
            return false
        }
        val id = UUID.randomUUID().toString()
        app.installs.begin(id, url, InstallOrigin.Tv)
        return try {
            ContextCompat.startForegroundService(app, Intent(app, InstallService::class.java)
                .putExtra(InstallService.EXTRA_ID, id)
                .putExtra(InstallService.EXTRA_URL, url.toString()))
            submitted = true
            true
        } catch (e: IllegalStateException) {
            app.installs.fail(id, InstallError.InstallFailed, e.message)
            _error.value = InstallError.InstallFailed
            false
        }
    }
}
