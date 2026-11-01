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
import com.csmith.barelauncher.install.InstallPhase
import com.csmith.barelauncher.install.InstallTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val watchHistory = getApplication<BareLauncherApp>().watchHistory
    private val installStore = getApplication<BareLauncherApp>().installs
    private val layoutStore = HomeLayoutStore(application)
    val layout: StateFlow<HomeLayout> = layoutStore.layout

    private val installedApps = MutableStateFlow<List<LaunchableApp>?>(null)
    // Keep the same lazy-grid item and focus when a download becomes a real app.
    private val appKeys = mutableMapOf<String, String>()

    val grid: StateFlow<HomeGrid> = combine(installedApps, layout, installStore.tasks) { loaded, layout, tasks ->
        if (loaded == null) return@combine HomeGrid()
        rememberCompletedKeys(loaded, tasks)
        val loadedPackages = loaded.map { it.packageName }.toSet()
        val reserved = tasks.filter { !it.isUpdate && it.phase.isBusy }.mapNotNull { it.packageName }.toSet()
        val apps = layout.arrange(loaded).filterNot { it.packageName in reserved }
        val installs = tasks.filterNot { it.phase == InstallPhase.Installed && it.packageName in loadedPackages }
            .map { if (it.phase == InstallPhase.Installed) it.copy(phase = InstallPhase.Installing) else it }
        HomeGrid(
            apps.map { HomeTile.App(it, appKeys[it.packageName] ?: it.packageName) } +
                installs.map(HomeTile::Install),
            loadState = AppLoadState.Ready,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeGrid())

    val hidden: StateFlow<List<LaunchableApp>> =
        combine(installedApps, layout) { apps, layout ->
            apps.orEmpty().filter { it.packageName in layout.hidden }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val keepWatching: StateFlow<List<KeepWatchingItem>> =
        combine(grid.map { it.apps }.distinctUntilChanged(), watchHistory.entries) { apps, entries ->
            val appsByPackage = apps.associateBy { it.packageName }
            entries.mapNotNull { entry ->
                appsByPackage[entry.packageName]?.let { app ->
                    KeepWatchingItem(entry, app, watchHistory.loadArtwork(entry.id)?.asImageBitmap())
                }
            }
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var loadJob: Job? = null

    private val packagesChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart
            if (intent.action == Intent.ACTION_PACKAGE_ADDED && packageName != null &&
                !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                installedApps.value?.let { apps ->
                    layoutStore.update { it.append(apps, packageName) }
                }
            }
            reloadApps()
        }
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
        viewModelScope.launch {
            installStore.tasks.map { tasks -> tasks.filter { it.phase == InstallPhase.Installed }.map { it.id } }
                .distinctUntilChanged().collect { if (it.isNotEmpty()) reloadApps() }
        }
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(packagesChanged)
    }

    fun togglePin(app: LaunchableApp) = layoutStore.update {
        if (app.packageName in it.pinned) it.unpin(app.packageName) else it.pin(app.packageName)
    }

    fun move(app: LaunchableApp, offset: Int) = layoutStore.update {
        it.move(requireNotNull(installedApps.value), app.packageName, offset)
    }

    fun hide(app: LaunchableApp) = layoutStore.update { it.hide(app.packageName) }
    fun show(app: LaunchableApp) = layoutStore.update { it.show(app.packageName) }
    fun dismissInstall(id: String) = installStore.remove(id)

    private fun rememberCompletedKeys(apps: List<LaunchableApp>, tasks: List<InstallTask>) {
        val packages = apps.map { it.packageName }.toSet()
        tasks.filter { it.phase == InstallPhase.Installed && !it.isUpdate && it.packageName in packages }
            .distinctBy { it.packageName }
            .forEach { appKeys[requireNotNull(it.packageName)] = "install:${it.id}" }
    }

    private fun reloadApps() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val completed = installStore.tasks.value.filter { it.phase == InstallPhase.Installed }
            val loaded = withContext(Dispatchers.IO) { loadLaunchableApps(getApplication()) }
            val newPackages = completed.filterNot { it.isUpdate }.mapNotNull { it.packageName }.toSet()
            val existing = loaded.filterNot { it.packageName in newPackages }
            newPackages.forEach { packageName -> layoutStore.update { it.append(existing, packageName) } }
            rememberCompletedKeys(loaded, completed)
            installedApps.value = loaded
            // Artwork and ordering are ready before the temporary tiles leave the snapshot.
            completed.forEach { installStore.remove(it.id) }
        }
    }
}
