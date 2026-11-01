package com.csmith.barelauncher.install

import android.content.Intent
import android.graphics.Bitmap
import androidx.annotation.StringRes
import com.csmith.barelauncher.R

enum class InstallPhase(@StringRes val message: Int) {
    Downloading(R.string.status_downloading),
    Transferring(R.string.status_transferring),
    Verifying(R.string.status_verifying),
    Installing(R.string.status_installing),
    Confirming(R.string.status_confirming),
    Installed(R.string.status_installed),
    Failed(R.string.error_install_failed);

    val isBusy: Boolean
        get() = this != Installed && this != Failed

    val showsProgress: Boolean
        get() = this == Downloading || this == Transferring
}

enum class InstallOrigin { Tv, Cli }

data class InstallTask(
    val id: String,
    val title: String,
    val origin: InstallOrigin,
    val phase: InstallPhase = InstallPhase.Downloading,
    /** Null when the download server does not provide its size. */
    val fraction: Float? = null,
    val icon: Bitmap? = null,
    val error: InstallError? = null,
    val detail: String? = null,
    val confirmation: Intent? = null,
    val packageName: String? = null,
    val isUpdate: Boolean = false,
)

enum class InstallError(@StringRes val message: Int) {
    InvalidUrl(R.string.error_invalid_url),
    InstallsNotAllowed(R.string.error_installs_not_allowed),
    DownloadFailed(R.string.error_download_failed),
    TransferFailed(R.string.error_transfer_failed),
    InvalidApk(R.string.error_invalid_apk),
    InstallFailed(R.string.error_install_failed),
    Cancelled(R.string.error_install_cancelled),
}
