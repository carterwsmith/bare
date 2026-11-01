package com.csmith.barelauncher.install

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.csmith.barelauncher.BareLauncherApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

class InstallViewModel(application: Application) : AndroidViewModel(application) {
    private val installer = ApkInstaller(application, getApplication<BareLauncherApp>().http)
    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state.asStateFlow()

    fun install(link: String) {
        if (_state.value.isBusy) return
        val url = link.trim().toHttpUrlOrNull()
        if (url == null) {
            _state.value = InstallState.Failed(InstallError.InvalidUrl, detail = null)
            return
        }
        if (!getApplication<Application>().packageManager.canRequestPackageInstalls()) {
            _state.value = InstallState.Failed(InstallError.InstallsNotAllowed, detail = null)
            return
        }
        viewModelScope.launch {
            _state.value = InstallState.Downloading(fraction = null)
            try {
                installer.install(url) { _state.value = InstallState.Downloading(it) }
                _state.value = InstallState.Confirming
            } catch (e: IOException) {
                _state.value = InstallState.Failed(InstallError.DownloadFailed, e.message)
            }
        }
    }

    fun onInstallFinished(success: Boolean, message: String?) {
        _state.value = if (success) {
            InstallState.Installed
        } else {
            InstallState.Failed(InstallError.InstallFailed, message)
        }
    }
}
