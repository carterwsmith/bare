package com.csmith.barelauncher.home

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.csmith.barelauncher.BareLauncherApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val watchHistory = getApplication<BareLauncherApp>().watchHistory
    private val layoutStore = HomeLayoutStore(application)
    val layout: StateFlow<HomeLayout> = layoutStore.layout

    private val installedApps = MutableStateFlow(emptyList<LaunchableApp>())

    /** The apps on the home screen, in the user's arrangement, hidden ones left out. */
    val apps: StateFlow<List<LaunchableApp>> =
        combine(installedApps, layout) { apps, layout -> layout.arrange(apps) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Installed apps the user has hidden, so they can be brought back. */
    val hidden: StateFlow<List<LaunchableApp>> =
        combine(installedApps, layout) { apps, layout ->
            apps.filter { it.packageName in layout.hidden }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Watch history for apps on the home screen, with artwork loaded. */
    val keepWatching: StateFlow<List<KeepWatchingItem>> =
        combine(apps, watchHistory.entries) { apps, entries ->
            val appsByPackage = apps.associateBy { it.packageName }
            entries.mapNotNull { entry ->
                appsByPackage[entry.packageName]?.let { app ->
                    KeepWatchingItem(entry, app, watchHistory.loadArtwork(entry.id)?.asImageBitmap())
                }
            }
        }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var loadJob: Job? = null

    // Reloading only when apps change keeps returning to the home screen free of work.
    private val packagesChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reloadApps()
    }

    init {
        reloadApps()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(application, packagesChanged, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(packagesChanged)
    }

    fun togglePin(app: LaunchableApp) = layoutStore.update {
        if (app.packageName in it.pinned) it.unpin(app.packageName) else it.pin(app.packageName)
    }

    fun move(app: LaunchableApp, offset: Int) = layoutStore.update {
        it.move(installedApps.value, app.packageName, offset)
    }

    fun hide(app: LaunchableApp) = layoutStore.update { it.hide(app.packageName) }

    fun show(app: LaunchableApp) = layoutStore.update { it.show(app.packageName) }

    private fun reloadApps() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            installedApps.value = withContext(Dispatchers.IO) { loadLaunchableApps(getApplication()) }
        }
    }
}
