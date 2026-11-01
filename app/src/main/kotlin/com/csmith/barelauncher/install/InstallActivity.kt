package com.csmith.barelauncher.install

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import com.csmith.barelauncher.BareLauncherApp
import com.csmith.barelauncher.ui.BareTheme

class InstallActivity : ComponentActivity() {
    private val viewModel: InstallViewModel by viewModels()
    private val store get() = (application as BareLauncherApp).installs
    private val confirmation = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = requireNotNull(intent.getStringExtra(EXTRA_INSTALL_ID))
        if (store.tasks.value.find { it.id == id }?.phase == InstallPhase.Confirming) {
            if (result.resultCode == Activity.RESULT_CANCELED) {
                store.fail(id, InstallError.Cancelled)
            } else {
                store.update(id) { it.copy(phase = InstallPhase.Installing, confirmation = null) }
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (handleInstallIntent(intent, savedInstanceState != null)) return
        setContent {
            BareTheme {
                val error by viewModel.error.collectAsState()
                InstallScreen(error = error, onInstall = { if (viewModel.install(it)) finish() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        check(handleInstallIntent(intent, false)) { "Expected an install result" }
    }

    private fun handleInstallIntent(intent: Intent, restoring: Boolean): Boolean {
        val id = intent.getStringExtra(EXTRA_INSTALL_ID) ?: return false
        if (intent.hasExtra(PackageInstaller.EXTRA_STATUS)) {
            when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val prompt = requireNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java))
                    store.update(id) { it.copy(phase = InstallPhase.Confirming, confirmation = prompt) }
                    if (!restoring) confirmation.launch(prompt.putExtra(Intent.EXTRA_RETURN_RESULT, true))
                }
                PackageInstaller.STATUS_SUCCESS -> {
                    store.update(id) { it.copy(phase = InstallPhase.Installed, confirmation = null) }
                    finish()
                }
                else -> {
                    store.fail(
                        id,
                        if (status == PackageInstaller.STATUS_FAILURE_ABORTED) InstallError.Cancelled else InstallError.InstallFailed,
                        intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
                    )
                    finish()
                }
            }
        } else {
            val prompt = requireNotNull(store.tasks.value.find { it.id == id }?.confirmation)
            if (!restoring) confirmation.launch(prompt.putExtra(Intent.EXTRA_RETURN_RESULT, true))
        }
        return true
    }

    companion object {
        const val EXTRA_INSTALL_ID = "install_id"
    }
}
