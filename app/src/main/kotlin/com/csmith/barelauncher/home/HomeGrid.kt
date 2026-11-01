package com.csmith.barelauncher.home

import com.csmith.barelauncher.install.InstallTask

enum class AppLoadState { Loading, Ready }

sealed interface HomeTile {
    val key: String

    data class App(val app: LaunchableApp, override val key: String = app.packageName) : HomeTile
    data class Install(val task: InstallTask) : HomeTile {
        override val key = "install:${task.id}"
    }
}

/** One snapshot prevents an app-list refresh and install completion from rendering separately. */
data class HomeGrid(
    val tiles: List<HomeTile> = emptyList(),
    val loadState: AppLoadState = AppLoadState.Loading,
) {
    val isLoading: Boolean
        get() = loadState == AppLoadState.Loading

    val apps: List<LaunchableApp>
        get() = tiles.mapNotNull { (it as? HomeTile.App)?.app }
}
