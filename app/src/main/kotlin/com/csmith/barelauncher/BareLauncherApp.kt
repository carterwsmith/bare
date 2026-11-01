package com.csmith.barelauncher

import android.app.Application
import com.csmith.barelauncher.watching.WatchHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class BareLauncherApp : Application() {
    /** Shared by the home screen and the background listener that records playback. */
    val watchHistory by lazy { WatchHistory(filesDir, CoroutineScope(SupervisorJob())) }
}
