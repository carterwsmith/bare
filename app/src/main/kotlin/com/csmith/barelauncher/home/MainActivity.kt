package com.csmith.barelauncher.home

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.csmith.barelauncher.install.InstallActivity
import com.csmith.barelauncher.ui.BareTheme

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BareTheme {
                val apps by viewModel.apps.collectAsStateWithLifecycle()
                val layout by viewModel.layout.collectAsStateWithLifecycle()
                val hidden by viewModel.hidden.collectAsStateWithLifecycle()
                val keepWatching by viewModel.keepWatching.collectAsStateWithLifecycle()
                // Startup counts as finished once the app grid has something in it.
                ReportDrawnWhen { apps.isNotEmpty() }
                // This is the home screen; Back would only drop to Amazon's home behind it.
                BackHandler {}
                HomeScreen(
                    apps = apps,
                    pinned = layout.pinned,
                    hidden = hidden,
                    keepWatching = keepWatching,
                    onLaunch = { startActivity(it.launchIntent) },
                    onMove = viewModel::move,
                    onTogglePin = viewModel::togglePin,
                    onHide = viewModel::hide,
                    onShow = viewModel::show,
                    onInstallApk = { startActivity(Intent(this@MainActivity, InstallActivity::class.java)) },
                    // On Fire OS the general settings action opens a page inside Amazon's
                    // launcher, which Home on Fire bounces straight back from. This one opens
                    // the real Settings app, whose side menu reaches every other section.
                    onOpenSettings = { startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)) },
                )
            }
        }
    }
}
