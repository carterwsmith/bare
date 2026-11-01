package com.csmith.barelauncher

import android.app.Application
import com.csmith.barelauncher.watching.WatchHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

class BareLauncherApp : Application() {
    /** Shared by the home screen and the background listener that records playback. */
    val watchHistory by lazy { WatchHistory(filesDir, CoroutineScope(SupervisorJob())) }

    /** One client for every download, so they share connections and threads. */
    val http by lazy { OkHttpClient() }
}
