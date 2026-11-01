package com.csmith.barelauncher.install

import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import com.csmith.barelauncher.ui.BareTheme

class InstallActivity : ComponentActivity() {
    private val viewModel: InstallViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BareTheme {
                val state by viewModel.state.collectAsState()
                InstallScreen(state = state, onInstall = viewModel::install)
            }
        }
    }

    /** PackageInstaller reports back here; see [ApkInstaller]. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        check(intent.hasExtra(PackageInstaller.EXTRA_STATUS)) { "Expected an install result, got $intent" }
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val prompt = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                startActivity(requireNotNull(prompt) { "Installer asked for confirmation without a prompt" })
            }
            else -> viewModel.onInstallFinished(
                success = status == PackageInstaller.STATUS_SUCCESS,
                message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
            )
        }
    }
}
