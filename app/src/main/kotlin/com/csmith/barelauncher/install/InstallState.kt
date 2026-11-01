package com.csmith.barelauncher.install

import androidx.annotation.StringRes
import com.csmith.barelauncher.R

sealed interface InstallState {
    data object Idle : InstallState

    /** [fraction] is null when the server doesn't send the file size. */
    data class Downloading(val fraction: Float?) : InstallState

    /** Waiting on the system's install prompt. */
    data object Confirming : InstallState

    data object Installed : InstallState

    data class Failed(val error: InstallError, val detail: String?) : InstallState

    val isBusy: Boolean
        get() = this is Downloading || this is Confirming
}

enum class InstallError(@StringRes val message: Int) {
    InvalidUrl(R.string.error_invalid_url),
    InstallsNotAllowed(R.string.error_installs_not_allowed),
    DownloadFailed(R.string.error_download_failed),
    InstallFailed(R.string.error_install_failed),
}
